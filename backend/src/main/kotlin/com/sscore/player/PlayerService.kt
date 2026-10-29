package com.sscore.player

import com.sscore.common.ApiException
import com.sscore.dto.CareerStatDto
import com.sscore.dto.PlayerDetailDto
import com.sscore.dto.PlayerMatchRecordDto
import com.sscore.dto.PlayerMatchStatDto
import com.sscore.dto.PlayerProfileDto
import com.sscore.dto.PlayerSeasonStatsDetailDto
import com.sscore.dto.PlayerTrophyDto
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.football.SeasonResolver
import com.sscore.football.optionalExternal
import com.sscore.league.LeagueCatalog
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.service.ApiFootballService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

// 선수 상세 화면(프로필·경기·시즌 통계·경기별 기록)의 조회 로직
@Service
class PlayerService(
    private val players: PlayerRepository,
    private val seasonStats: PlayerSeasonStatRepository,
    private val playerStats: PlayerStatRepository,
    private val matches: MatchRepository,
    private val matchEvents: MatchEventRepository,
    private val teams: TeamRepository,
    private val champions: ChampionRepository,
    private val apiService: ApiFootballService,
    private val seasons: SeasonResolver,
    private val catalog: LeagueCatalog
) {
    // 없는 선수로는 외부 API 호출이나 조회를 하지 않는다
    fun requirePlayer(playerId: Long): PlayerEntity = players.findById(playerId).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "선수를 찾을 수 없습니다.")
    }

    private fun photoOf(player: PlayerEntity) =
        player.photoUrl ?: "https://media.api-sports.io/football/players/${player.playerId}.png"

    private fun PlayerSeasonStatEntity.toCareerStat() = CareerStatDto(
        season = season, teamName = teamName, leagueName = leagueName,
        appearances = appearances, goals = goals, assists = assists, rating = rating, minutesPlayed = minutesPlayed
    )

    // ── 프로필 + 시즌별 커리어 ──────────────────────────────────

    fun getProfile(playerId: Long): PlayerProfileDto {
        val player = requirePlayer(playerId)
        return PlayerProfileDto(
            playerId = player.playerId,
            name = player.name,
            nationality = player.nationality,
            position = player.position,
            age = player.age,
            photoUrl = photoOf(player),
            currentTeamName = player.team?.name,
            careerStats = seasonStats.findByPlayerIdOrderBySeasonDesc(playerId).map { it.toCareerStat() }
        )
    }

    // ── 세부 페이지(프로필 + 이번 시즌 + 트로피) ─────────────────

    fun getDetail(playerId: Long): PlayerDetailDto {
        val player = requirePlayer(playerId)
        val career = seasonStats.findByPlayerIdOrderBySeasonDesc(playerId)
        val effectiveSeason = seasons.effective()

        // 키·생년월일·주발·등번호는 외부 API에서 가져온다(없어도 화면은 보여 준다).
        // 등번호는 최근 시즌부터 시도해서 번호가 있는 결과를 쓴다.
        val recentSeasons = career.map { it.season }.distinct().take(3).ifEmpty { listOf(effectiveSeason, effectiveSeason - 1) }
        val api = recentSeasons.firstNotNullOfOrNull { season ->
            optionalExternal("선수 상세(playerId=$playerId, season=$season)", null) {
                apiService.fetchPlayerDetails(playerId, season)?.takeIf { it.squadNumber != null }
            }
        } ?: optionalExternal("선수 상세(playerId=$playerId)", null) {
            apiService.fetchPlayerDetails(playerId, recentSeasons.first())
        }

        // 트로피: 선수가 뛴 팀+시즌과 챔피언 기록의 교집합
        val played = career.map { it.teamName to it.season }.toSet()
        val trophies = champions.findAll()
            .filter { (it.teamName to it.season) in played }
            .groupBy { it.leagueId }
            .map { (leagueId, entries) ->
                PlayerTrophyDto(
                    trophyName = catalog.nameKo(leagueId),
                    count = entries.size,
                    seasons = entries.sortedByDescending { it.season }.map { "${it.season}/${(it.season + 1).toString().takeLast(2)}" }
                )
            }
            .sortedByDescending { it.count }

        return PlayerDetailDto(
            playerId = player.playerId,
            name = player.name,
            photoUrl = photoOf(player),
            currentTeamName = career.firstOrNull()?.teamName ?: player.team?.name,   // 가장 최근 시즌 기록의 팀이 더 정확하다
            height = api?.height,
            birthDate = api?.birthDate,
            nationality = player.nationality ?: api?.nationality,
            age = player.age ?: api?.age,
            position = player.position,
            squadNumber = api?.squadNumber,
            preferredFoot = api?.preferredFoot,
            currentSeasonStats = career.firstOrNull { it.season == effectiveSeason }?.toCareerStat(),
            trophies = trophies
        )
    }

    // ── 경기 기록 (최신 시즌 소속팀의 경기별 출전/득점/도움/카드) ─────

    fun getMatches(playerId: Long): List<PlayerMatchRecordDto> {
        val player = requirePlayer(playerId)

        // 최신 시즌 스탯에서 팀 정보를 가져온다 (PlayerEntity.team보다 정확)
        val latestStat = seasonStats.findByPlayerIdOrderBySeasonDesc(playerId).firstOrNull() ?: return emptyList()
        val allTeams = teams.findAll()
        val team = allTeams.find { it.name == latestStat.teamName } ?: player.team ?: return emptyList()
        val logos = allTeams.associate { it.teamId to it.logoUrl }

        val teamMatches = matches.findByTeamAndSeason(team.teamId, latestStat.season, catalog.ids())
        val statsByFixture = playerStats.findByPlayerId(playerId).associateBy { it.fixtureId }
        val eventsByFixture = matchEvents.findByFixtureIdIn(teamMatches.mapNotNull { it.fixtureId }).groupBy { it.fixtureId }
        val lastName = player.name.split(" ").last()

        return teamMatches.map { m ->
            val isHome = m.homeTeamId == team.teamId
            val oppId = if (isHome) m.awayTeamId else m.homeTeamId
            val oppName = if (isHome) m.awayTeam else m.homeTeam
            val stat = m.fixtureId?.let { statsByFixture[it] }

            val events = m.fixtureId?.let { eventsByFixture[it] }.orEmpty()
            val mine = events.filter {
                it.playerName?.contains(lastName, ignoreCase = true) == true ||
                    it.assistPlayerName?.contains(lastName, ignoreCase = true) == true
            }
            val goals = mine.count { it.type == "Goal" && it.playerName?.contains(lastName, ignoreCase = true) == true }
            val assists = mine.count { it.type == "Goal" && it.assistPlayerName?.contains(lastName, ignoreCase = true) == true }
            val yellowCards = mine.count { it.type == "Card" && it.detail?.contains("Yellow") == true }
            val redCards = mine.count { it.type == "Card" && it.detail?.contains("Red") == true }

            // 출전 여부: 이벤트가 있거나 출전 시간이 있으면 출전
            val minutes = stat?.minutesPlayed ?: 0
            val playedInMatch = goals > 0 || assists > 0 || yellowCards > 0 || redCards > 0 || minutes > 0
            val status = when {
                playedInMatch && minutes in 1..59 -> "SUBBED_ON"
                playedInMatch -> "STARTED"
                stat != null && minutes == 0 -> "BENCH"
                else -> "NO_DATA"
            }

            PlayerMatchRecordDto(
                fixtureId = m.fixtureId ?: 0L,
                matchDate = m.matchDate,
                opponentName = oppName,
                opponentLogo = oppId?.let { logos[it] },
                score = m.score,
                isHome = isHome,
                status = status,
                minutesPlayed = stat?.minutesPlayed,
                goals = goals,
                assists = assists,
                yellowCards = yellowCards,
                redCards = redCards,
                rating = stat?.rating,
                homeTeam = m.homeTeam,
                awayTeam = m.awayTeam,
                homeTeamId = m.homeTeamId,
                awayTeamId = m.awayTeamId,
                matchTime = m.matchTime,
                matchRound = m.matchRound,
                leagueId = m.leagueId,
                season = m.season,
                stadium = m.stadium
            )
        }.sortedByDescending { it.matchDate }
    }

    // ── 시즌별 통계 목록 (경기별 기록을 시즌 단위로 합산) ───────────

    fun getSeasonStats(playerId: Long): List<PlayerSeasonStatsDetailDto> {
        val player = requirePlayer(playerId)
        val allTeams = teams.findAll().associateBy { it.name }
        val allPlayerStats = playerStats.findByPlayerId(playerId)

        return seasonStats.findByPlayerIdOrderBySeasonDesc(playerId).map { s ->
            // 해당 시즌+팀의 경기 fixtureId 목록
            val fixtureIds = allTeams[s.teamName]?.let { team ->
                matches.findByTeamAndSeason(team.teamId, s.season, catalog.ids()).mapNotNull { it.fixtureId }.toSet()
            } ?: emptySet()
            val ps = allPlayerStats.filter { it.fixtureId in fixtureIds }

            val totalPasses = ps.sumOf { it.passesTotal }
            val successPasses = ps.sumOf { it.passesAccuracy }   // 성공 패스 횟수
            val passAccAvg = if (totalPasses > 0) successPasses.toDouble() / totalPasses * 100 else 0.0

            PlayerSeasonStatsDetailDto(
                season = s.season,
                teamName = s.teamName,
                leagueName = s.leagueName,
                position = player.position,
                appearances = s.appearances,
                goals = s.goals,
                assists = s.assists,
                avgRating = s.rating,
                yellowCards = s.yellowCards,
                redCards = s.redCards,
                passesTotal = totalPasses,
                passesAccuracyAvg = passAccAvg,
                passesKey = ps.sumOf { it.passesKey },
                shotsTotal = ps.sumOf { it.shotsTotal },
                shotsOnTarget = ps.sumOf { it.shotsOnTarget },
                xgTotal = ps.mapNotNull { it.xg }.sum(),
                tacklesTotal = ps.sumOf { it.tacklesTotal },
                tacklesBlocks = ps.sumOf { it.tacklesBlocks },
                tacklesInterceptions = ps.sumOf { it.tacklesInterceptions },
                duelsTotal = ps.sumOf { it.duelsTotal },
                duelsWon = ps.sumOf { it.duelsWon },
                foulsDrawn = ps.sumOf { it.foulsDrawn },
                foulsCommitted = ps.sumOf { it.foulsCommitted },
                goalkeeperSaves = ps.sumOf { it.goalkeeperSaves },
                goalkeeperConceded = ps.sumOf { it.goalkeeperConceded },
                goalkeeperSavesInsideBox = ps.sumOf { it.goalkeeperSavesInsideBox },
                matchesWithData = ps.size
            )
        }
    }

    // ── 한 경기에서의 선수 상세 기록 (외부 API의 경기 상세에서 읽는다) ──

    @Suppress("UNCHECKED_CAST")
    fun getMatchStats(playerId: Long, fixtureId: Long): PlayerMatchStatDto {
        requirePlayer(playerId)
        val details = apiService.fetchFixtureDetails(fixtureId) ?: throw noRecord()
        val playersSection = details["players"] as? List<Map<String, Any>> ?: throw noRecord()

        for (teamEntry in playersSection) {
            val teamName = (teamEntry["team"] as? Map<*, *>)?.get("name")?.toString() ?: ""
            val entries = teamEntry["players"] as? List<Map<*, *>> ?: continue

            for (entry in entries) {
                val p = entry["player"] as? Map<*, *> ?: continue
                val pid = (p["id"] as? Number)?.toLong() ?: continue
                if (pid != playerId) continue

                val st = (entry["statistics"] as? List<Map<*, *>>)?.firstOrNull() ?: continue
                val games = st["games"] as? Map<*, *>
                val shots = st["shots"] as? Map<*, *>
                val gls = st["goals"] as? Map<*, *>
                val passes = st["passes"] as? Map<*, *>
                val tackles = st["tackles"] as? Map<*, *>
                val duels = st["duels"] as? Map<*, *>
                val dribbles = st["dribbles"] as? Map<*, *>
                val fouls = st["fouls"] as? Map<*, *>

                fun num(m: Map<*, *>?, k: String) = (m?.get(k) as? Number)?.toInt()

                // 국적·나이: 경기 응답에 없으면 DB 값을 쓴다
                val dbPlayer = players.findById(pid).orElse(null)

                return PlayerMatchStatDto(
                    playerId = pid,
                    playerName = p["name"]?.toString() ?: "",
                    photoUrl = "https://media.api-sports.io/football/players/$pid.png",
                    teamName = teamName,
                    position = games?.get("position")?.toString(),
                    nationality = p["nationality"]?.toString() ?: dbPlayer?.nationality,
                    age = (p["age"] as? Number)?.toInt() ?: dbPlayer?.age,
                    minutesPlayed = num(games, "minutes"),
                    rating = (games?.get("rating") as? String)?.toDoubleOrNull(),
                    goals = num(gls, "total"),
                    assists = num(gls, "assists"),
                    passesTotal = num(passes, "total"),
                    passesAccuracy = (passes?.get("accuracy") as? String)?.toIntOrNull() ?: (passes?.get("accuracy") as? Number)?.toInt(),
                    keyPasses = num(passes, "key"),
                    shotsTotal = num(shots, "total"),
                    shotsOn = num(shots, "on"),
                    tacklesTotal = num(tackles, "total"),
                    tacklesBlocks = num(tackles, "blocks"),
                    tacklesInterceptions = num(tackles, "interceptions"),
                    duelsTotal = num(duels, "total"),
                    duelsWon = num(duels, "won"),
                    dribblesAttempts = num(dribbles, "attempts"),
                    dribblesSuccess = num(dribbles, "success"),
                    foulsDrawn = num(fouls, "drawn"),
                    foulsCommitted = num(fouls, "committed"),
                    saves = num(gls, "saves"),
                    goalsConceded = num(gls, "conceded"),
                    opponentXg = run {
                        // 경기 통계에서 상대팀의 expected_goals를 찾는다
                        val statsSection = details["statistics"] as? List<Map<String, Any>>
                        statsSection?.firstOrNull { (it["team"] as? Map<*, *>)?.get("name")?.toString() != teamName }
                            ?.let { opponent ->
                                (opponent["statistics"] as? List<Map<*, *>>)
                                    ?.firstOrNull { it["type"]?.toString().equals("expected_goals", ignoreCase = true) }
                                    ?.get("value")?.toString()?.toDoubleOrNull()
                            }
                    }
                )
            }
        }
        throw noRecord()
    }

    private fun noRecord() = ApiException(HttpStatus.NOT_FOUND, "PLAYER_MATCH_STATS_NOT_FOUND", "이 경기의 선수 기록이 없습니다.")
}
