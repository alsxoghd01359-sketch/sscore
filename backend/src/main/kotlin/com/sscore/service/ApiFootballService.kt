package com.sscore.service

import com.sscore.dto.*
import com.sscore.entity.ChampionEntity
import com.sscore.entity.MatchEntity
import com.sscore.entity.MatchEventEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.football.FixtureParser
import com.sscore.football.FootballApiClient
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 외부 축구 API의 응답을 우리 DTO/엔티티로 바꾸는 곳. HTTP 호출은 FootballApiClient가 맡는다.
// 외부 API가 오류를 돌려주면 ApiException(502)이 그대로 올라가고, 그 경우 아무것도 저장하지 않는다.
@Service
class ApiFootballService(
    private val football: FootballApiClient,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val playerSeasonStatRepository: PlayerSeasonStatRepository,
    private val matchRepository: MatchRepository,
    private val championRepository: ChampionRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val top5LeagueIds = listOf(39, 140, 78, 135, 61)

    // ─────────────────────────────────────────────
    // 팀 동기화
    // ─────────────────────────────────────────────
    @Transactional
    fun syncTeams(leagueId: Int, season: Int): String {
        val items = football.list("/teams", "league" to leagueId, "season" to season)
        val existingTeamIds = teamRepository.findAll().map { it.teamId }.toSet()
        val newTeams = items.mapNotNull { item ->
            val team = item["team"] as? Map<*, *> ?: return@mapNotNull null
            val id = (team["id"] as? Number)?.toInt() ?: return@mapNotNull null
            val name = team["name"]?.toString() ?: return@mapNotNull null
            if (id in existingTeamIds) return@mapNotNull null
            TeamEntity(teamId = id, name = name, country = team["country"]?.toString(), logoUrl = team["logo"]?.toString())
        }
        if (newTeams.isEmpty()) return "성공: 모든 팀이 이미 최신 상태입니다."
        teamRepository.saveAll(newTeams)
        log.info("{} 시즌 리그 {} 새로운 팀 {}개 저장 완료", season, leagueId, newTeams.size)
        return "성공: 새로운 팀 ${newTeams.size}개 동기화 완료"
    }

    // ─────────────────────────────────────────────
    // 순위표 조회
    // ─────────────────────────────────────────────
    fun fetchStandings(leagueId: Int, season: Int): List<StandingDto> {
        val rows = standingRows(leagueId, season)
        return rows.mapNotNull { row ->
            val team = row["team"] as? Map<*, *> ?: return@mapNotNull null
            val all = row["all"] as? Map<*, *>
            val goals = row["goals"] as? Map<*, *>
            StandingDto(
                rank = (row["rank"] as? Number)?.toInt() ?: 0,
                teamId = (team["id"] as? Number)?.toInt() ?: 0,
                teamName = team["name"]?.toString() ?: "",
                points = (row["points"] as? Number)?.toInt() ?: 0,
                played = (all?.get("played") as? Number)?.toInt() ?: 0,
                win = (all?.get("win") as? Number)?.toInt() ?: 0,
                draw = (all?.get("draw") as? Number)?.toInt() ?: 0,
                lose = (all?.get("lose") as? Number)?.toInt() ?: 0,
                goalsFor = (goals?.get("for") as? Number)?.toInt() ?: 0,
                goalsAgainst = (goals?.get("against") as? Number)?.toInt() ?: 0,
                goalsDiff = (row["goalsDiff"] as? Number)?.toInt() ?: 0,
                form = row["form"]?.toString()
            )
        }
    }

    // 홈/원정 성적 (standings의 home/away 분리). 순위표에 그 팀이 없으면 (null, null)
    fun fetchTeamRecord(teamId: Int, leagueId: Int, season: Int): Pair<RecordDto?, RecordDto?> {
        val row = standingRows(leagueId, season).firstOrNull {
            ((it["team"] as? Map<*, *>)?.get("id") as? Number)?.toInt() == teamId
        } ?: return Pair(null, null)

        fun parseRecord(key: String): RecordDto? {
            val rec = row[key] as? Map<*, *> ?: return null
            val goals = rec["goals"] as? Map<*, *>
            val w = (rec["win"] as? Number)?.toInt() ?: 0
            val d = (rec["draw"] as? Number)?.toInt() ?: 0
            val l = (rec["lose"] as? Number)?.toInt() ?: 0
            val gf = (goals?.get("for") as? Number)?.toInt() ?: 0
            val ga = (goals?.get("against") as? Number)?.toInt() ?: 0
            return RecordDto(
                played = (rec["played"] as? Number)?.toInt() ?: 0,
                win = w, draw = d, lose = l,
                goalsFor = gf, goalsAgainst = ga,
                goalsDiff = gf - ga,
                points = w * 3 + d
            )
        }
        return Pair(parseRecord("home"), parseRecord("away"))
    }

    private fun standingRows(leagueId: Int, season: Int): List<Map<*, *>> {
        val league = football.list("/standings", "league" to leagueId, "season" to season)
            .firstOrNull()?.get("league") as? Map<*, *> ?: return emptyList()
        val table = (league["standings"] as? List<*>)?.firstOrNull() as? List<*> ?: return emptyList()
        return table.filterIsInstance<Map<*, *>>()
    }

    // ─────────────────────────────────────────────
    // 선수 상세 정보 (키, 생년월일, 주발, 등번호)
    // ─────────────────────────────────────────────
    data class PlayerApiInfo(
        val height: String?, val birthDate: String?,
        val nationality: String?, val age: Int?,
        val squadNumber: Int?, val preferredFoot: String?
    )

    fun fetchPlayerDetails(playerId: Long, season: Int): PlayerApiInfo? {
        val item = football.list("/players", "id" to playerId, "season" to season).firstOrNull() ?: return null
        val p = item["player"] as? Map<*, *> ?: return null
        val stats = (item["statistics"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val birth = p["birth"] as? Map<*, *>
        return PlayerApiInfo(
            height = p["height"]?.toString()?.takeIf { it != "null" },
            birthDate = birth?.get("date")?.toString()?.takeIf { it != "null" },
            nationality = p["nationality"]?.toString(),
            age = (p["age"] as? Number)?.toInt(),
            squadNumber = ((stats?.get("games") as? Map<*, *>)?.get("number") as? Number)?.toInt(),
            preferredFoot = p["foot"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        )
    }

    // ─────────────────────────────────────────────
    // 경기 이벤트만 조회 (득점/카드/교체)
    // ─────────────────────────────────────────────
    fun fetchFixtureEvents(fixtureId: Long): List<MatchEventEntity> =
        football.list("/fixtures/events", "fixture" to fixtureId).map { ev ->
            val timeMap = ev["time"] as? Map<*, *>
            val team = ev["team"] as? Map<*, *>
            val player = ev["player"] as? Map<*, *>
            val assist = ev["assist"] as? Map<*, *>
            MatchEventEntity(
                fixtureId = fixtureId,
                time = (timeMap?.get("elapsed") as? Number)?.toInt() ?: 0,
                extraTime = (timeMap?.get("extra") as? Number)?.toInt(),
                teamId = (team?.get("id") as? Number)?.toInt() ?: 0,
                teamName = team?.get("name")?.toString() ?: "",
                type = ev["type"]?.toString() ?: "",
                detail = ev["detail"]?.toString(),
                playerName = player?.get("name")?.toString(),
                assistPlayerName = assist?.get("name")?.toString()
            )
        }

    // ─────────────────────────────────────────────
    // 현재 스쿼드 조회 (/players/squads)
    // ─────────────────────────────────────────────
    fun fetchTeamSquad(teamId: Int): List<SquadPlayerDto> {
        val players = football.list("/players/squads", "team" to teamId)
            .firstOrNull()?.get("players") as? List<*> ?: return emptyList()
        return players.filterIsInstance<Map<*, *>>().mapNotNull { p ->
            val pid = (p["id"] as? Number)?.toLong() ?: return@mapNotNull null
            SquadPlayerDto(
                playerId = pid,
                name = p["name"]?.toString() ?: "",
                number = (p["number"] as? Number)?.toInt(),
                position = p["position"]?.toString() ?: "",
                age = (p["age"] as? Number)?.toInt(),
                nationality = null,   // DB에서 채움
                photoUrl = p["photo"]?.toString() ?: "https://media.api-sports.io/football/players/$pid.png"
            )
        }
    }

    // ─────────────────────────────────────────────
    // 경기장 정보 조회
    // ─────────────────────────────────────────────
    fun fetchTeamVenue(teamId: Int): VenueDto? {
        val venue = football.list("/teams", "id" to teamId).firstOrNull()?.get("venue") as? Map<*, *> ?: return null
        return VenueDto(
            name = venue["name"]?.toString(),
            address = venue["address"]?.toString(),
            city = venue["city"]?.toString(),
            capacity = (venue["capacity"] as? Number)?.toInt(),
            surface = venue["surface"]?.toString(),
            imageUrl = venue["image"]?.toString()
        )
    }

    // ─────────────────────────────────────────────
    // 리그 우승팀 저장 (순위표 1위 → champions 테이블)
    // ─────────────────────────────────────────────
    @Transactional
    fun syncChampions(leagueId: Int, season: Int) {
        if (championRepository.existsByLeagueIdAndSeason(leagueId, season)) {
            log.info("[우승팀] leagueId={} season={} 이미 저장됨, 스킵", leagueId, season)
            return
        }
        val champion = fetchStandings(leagueId, season).firstOrNull { it.rank == 1 } ?: run {
            log.warn("[우승팀] leagueId={} season={} 1위 데이터 없음", leagueId, season)
            return
        }
        championRepository.save(
            ChampionEntity(leagueId = leagueId, season = season, teamId = champion.teamId, teamName = champion.teamName)
        )
        log.info("[우승팀] {} 시즌 {} 우승: {}", season, leagueId, champion.teamName)
    }

    // ─────────────────────────────────────────────
    // 구단 트로피 (우승만, 리그별 집계)
    // ─────────────────────────────────────────────
    fun fetchTeamTrophies(teamId: Int): List<TrophyDto> {
        val items = football.list("/trophies", "team" to teamId)
        val winners = items.filter { it["place"]?.toString().equals("Winner", ignoreCase = true) }
        log.debug("[트로피] teamId={} 우승 {}건 / 전체 {}건", teamId, winners.size, items.size)

        return winners
            .groupBy { it["league"]?.toString() ?: "Unknown" }
            .map { (league, entries) ->
                val sorted = entries.sortedByDescending { it["season"]?.toString() ?: "" }
                TrophyDto(
                    league = league,
                    country = sorted.firstOrNull()?.get("country")?.toString(),
                    winCount = entries.size,
                    lastWinSeason = sorted.firstOrNull()?.get("season")?.toString() ?: "-"
                )
            }
            .sortedByDescending { it.winCount }
    }

    // ─────────────────────────────────────────────
    // 경기 목록
    // ─────────────────────────────────────────────
    fun fetchLiveMatches(): List<MatchDto> =
        parseFixtures(football.list("/fixtures", "live" to "all"))

    // 시즌 전체 경기 (5대 리그). 한 리그라도 외부 API 오류가 나면 전체를 실패시켜 일부만 저장되는 일이 없게 한다.
    fun fetchMatchesBySeason(season: Int): List<MatchDto> =
        top5LeagueIds.flatMap { leagueId ->
            parseFixtures(football.list("/fixtures", "league" to leagueId, "season" to season))
        }

    fun fetchH2H(homeId: Int, awayId: Int): List<MatchDto> =
        parseFixtures(football.list("/fixtures/headtohead", "h2h" to "$homeId-$awayId"))

    // 읽을 수 없는 항목은 건너뛰고 로그를 남긴다
    private fun parseFixtures(items: List<Map<*, *>>): List<MatchDto> {
        val parsed = items.mapNotNull { FixtureParser.parse(it) }
        if (parsed.size != items.size) log.warn("읽을 수 없는 경기 항목 {}건을 건너뜀 (전체 {}건)", items.size - parsed.size, items.size)
        return parsed
    }

    // ─────────────────────────────────────────────
    // 라인업 조회
    // ─────────────────────────────────────────────
    fun fetchLineups(fixtureId: Long): List<LineupDto> =
        football.list("/fixtures/lineups", "fixture" to fixtureId).map { teamData ->
            val team = teamData["team"] as? Map<*, *>
            val coach = teamData["coach"] as? Map<*, *>
            val startXI = (teamData["startXI"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
            val substitutes = (teamData["substitutes"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

            fun parsePlayer(wrapper: Map<*, *>): PlayerDto {
                val p = wrapper["player"] as? Map<*, *> ?: emptyMap<String, Any>()
                return PlayerDto(
                    id = (p["id"] as? Number)?.toLong(),
                    name = p["name"]?.toString() ?: "",
                    number = (p["number"] as? Number)?.toInt(),
                    pos = p["pos"]?.toString(),
                    grid = p["grid"]?.toString()
                )
            }

            LineupDto(
                teamName = team?.get("name")?.toString() ?: "",
                formation = teamData["formation"]?.toString(),
                coachName = coach?.get("name")?.toString(),
                coachPhotoUrl = coach?.get("photo")?.toString(),
                startXI = startXI.map { parsePlayer(it) },
                substitutes = substitutes.map { parsePlayer(it) }
            )
        }

    // ─────────────────────────────────────────────
    // 경기 상세 정보 (통계/이벤트용). 없는 경기면 null
    // ─────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    fun fetchFixtureDetails(fixtureId: Long): Map<String, Any>? =
        football.list("/fixtures", "id" to fixtureId).firstOrNull() as? Map<String, Any>

    // ─────────────────────────────────────────────
    // 대량 적재용 sync 메서드
    // ─────────────────────────────────────────────
    @Transactional
    fun syncFixtures(leagueId: Int, season: Int) {
        val items = football.list("/fixtures", "league" to leagueId, "season" to season)
        val matches = parseFixtures(items)

        val existingIds = matchRepository.findAll().mapNotNull { it.fixtureId }.toHashSet()
        val toSave = matches.filter { it.fixtureId !in existingIds }.map { m ->
            MatchEntity(
                fixtureId = m.fixtureId, homeTeam = m.homeTeam, awayTeam = m.awayTeam,
                homeTeamId = m.homeTeamId, awayTeamId = m.awayTeamId,
                matchDate = m.matchDate, matchTime = m.matchTime, score = m.score,
                season = m.season, leagueId = m.leagueId,
                matchRound = m.matchRound, stadium = m.stadium, referee = m.referee
            )
        }
        if (toSave.isNotEmpty()) matchRepository.saveAll(toSave)
        log.info("리그 {} 시즌 {} 경기 적재 완료 — 신규 {}건 / 전체 {}건", leagueId, season, toSave.size, items.size)
    }

    @Transactional
    fun syncPlayers(teamId: Int, season: Int) {
        val team = teamRepository.findById(teamId).orElse(null) ?: run {
            log.warn("팀 {} 이 DB에 없어 선수 동기화 스킵", teamId)
            return
        }
        var page = 1
        var totalPages = 1

        do {
            val result = football.page("/players", "team" to teamId, "season" to season, "page" to page)
            totalPages = result.totalPages

            for (item in result.items) {
                val playerInfo = item["player"] as? Map<*, *> ?: continue
                val statsList = (item["statistics"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: continue
                val playerId = (playerInfo["id"] as? Number)?.toLong() ?: continue
                val name = playerInfo["name"]?.toString() ?: continue

                // 올바른 팀+시즌+빅5리그만 → 그중 출장수가 가장 많은 리그 (컵·유럽대회 제외)
                val stats = statsList
                    .filter { st ->
                        val stLeague = st["league"] as? Map<*, *>
                        val stTeam = st["team"] as? Map<*, *>
                        (stLeague?.get("season") as? Number)?.toInt() == season &&
                            (stTeam?.get("id") as? Number)?.toInt() == teamId &&
                            (stLeague?.get("id") as? Number)?.toInt() in top5LeagueIds
                    }
                    .maxByOrNull { st -> ((st["games"] as? Map<*, *>)?.get("appearences") as? Number)?.toInt() ?: 0 }
                    ?: continue

                val existingPlayer = playerRepository.findById(playerId).orElse(null)
                if (existingPlayer == null) {
                    playerRepository.save(
                        PlayerEntity(
                            playerId = playerId,
                            name = name,
                            nationality = playerInfo["nationality"]?.toString(),
                            photoUrl = playerInfo["photo"]?.toString(),
                            position = (stats["games"] as? Map<*, *>)?.get("position")?.toString(),
                            age = (playerInfo["age"] as? Number)?.toInt(),
                            team = team
                        )
                    )
                } else {
                    val newAge = (playerInfo["age"] as? Number)?.toInt()
                    if (newAge != null && existingPlayer.age != newAge) {
                        playerRepository.save(existingPlayer.copy(age = newAge))
                    }
                }

                val games = stats["games"] as? Map<*, *>
                val goals = stats["goals"] as? Map<*, *>
                val cards = stats["cards"] as? Map<*, *>
                val league = stats["league"] as? Map<*, *>

                // 항상 최신 데이터로 upsert
                val existing = playerSeasonStatRepository
                    .findFirstByPlayerIdAndSeasonAndTeamNameOrderByAppearancesDesc(playerId, season, team.name)
                playerSeasonStatRepository.save(
                    PlayerSeasonStatEntity(
                        id = existing?.id,
                        playerId = playerId,
                        season = season,
                        teamName = team.name,
                        leagueName = league?.get("name")?.toString() ?: "",
                        appearances = (games?.get("appearences") as? Number)?.toInt() ?: 0,
                        goals = (goals?.get("total") as? Number)?.toInt() ?: 0,
                        assists = (goals?.get("assists") as? Number)?.toInt() ?: 0,
                        minutesPlayed = (games?.get("minutes") as? Number)?.toInt() ?: 0,
                        rating = (games?.get("rating") as? String)?.toDoubleOrNull(),
                        yellowCards = (cards?.get("yellow") as? Number)?.toInt() ?: 0,
                        redCards = (cards?.get("red") as? Number)?.toInt() ?: 0
                    )
                )
            }
            page++
        } while (page <= totalPages)

        log.info("팀 {} 시즌 {} 선수 데이터 적재 완료", team.name, season)
    }
}
