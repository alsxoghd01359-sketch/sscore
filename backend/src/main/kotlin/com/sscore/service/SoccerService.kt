package com.sscore.service

import com.sscore.dto.*
import com.sscore.entity.MatchEntity
import com.sscore.entity.ChampionEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.PlayerStatEntity
import com.sscore.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class SoccerService(
    private val restTemplate: RestTemplate,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val playerStatRepository: PlayerStatRepository,
    private val playerSeasonStatRepository: PlayerSeasonStatRepository,
    private val apiService: ApiFootballService,
    private val championRepository: ChampionRepository,
    private val leagueRepository: LeagueRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun syncLiveMatches() {
        val liveMatches = apiService.fetchLiveMatches()

        liveMatches.forEach { liveMatch ->
            val fixtureId = liveMatch.fixtureId ?: return@forEach
            val existing = matchRepository.findByFixtureId(fixtureId)

            if (existing != null && (existing.score != liveMatch.score || existing.matchTime != liveMatch.matchTime)) {
                matchRepository.save(existing.copy(score = liveMatch.score, matchTime = liveMatch.matchTime))
            }
        }
    }

    fun getAllMatchesFromDb(): List<MatchDto> {
        return matchRepository.findAll().map { it.toDto() }
    }

    // 날짜(yyyy-MM-dd) + 리그 필터로 해당 날짜 경기만 반환 — 메인화면 전용
    fun getMatchesByDate(date: String, leagueId: Int?): List<MatchDto> {
        val entities = if (leagueId != null) {
            matchRepository.findByMatchDateAndLeagueId(date, leagueId)
        } else {
            matchRepository.findByMatchDate(date)
        }
        return entities
            .sortedWith(compareBy({ it.leagueId }, { it.matchTime }))
            .map { it.toDto() }
    }

    private fun MatchEntity.toDto() = MatchDto(
        fixtureId  = fixtureId,
        homeTeam   = homeTeam,
        awayTeam   = awayTeam,
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
        matchDate  = matchDate,
        matchTime  = matchTime,
        score      = score,
        season     = season,
        leagueId   = leagueId,
        matchRound = matchRound,
        stadium    = stadium,
        referee    = referee
    )

    @Transactional
    fun saveSeasonsRange(start: Int, end: Int) {
        for (season in start..end) {
            try {
                val matchDtos = apiService.fetchMatchesBySeason(season)
                if (matchDtos.isEmpty()) continue

                val entities = matchDtos.map { dto ->
                    val existing = matchRepository.findByFixtureId(dto.fixtureId!!)
                    MatchEntity(
                        id = existing?.id,
                        fixtureId = dto.fixtureId,
                        homeTeam = dto.homeTeam,
                        awayTeam = dto.awayTeam,
                        homeTeamId = dto.homeTeamId,
                        awayTeamId = dto.awayTeamId,
                        matchDate = dto.matchDate,
                        matchTime = dto.matchTime,
                        score = dto.score,
                        season = dto.season,
                        leagueId = dto.leagueId,
                        matchRound = dto.matchRound,
                        stadium = dto.stadium,
                        referee = dto.referee
                    )
                }
                matchRepository.saveAll(entities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLineups(fixtureId: Long): List<LineupDto> = apiService.fetchLineups(fixtureId)
    fun getPlayerCareerHistory(playerId: Long): List<PlayerSeasonStatEntity> = emptyList()

    fun leagueIdToName(leagueId: Int) = when (leagueId) {
        39  -> "프리미어리그"
        140 -> "라리가"
        78  -> "분데스리가"
        135 -> "세리에 A"
        61  -> "리그 1"
        else -> "리그 $leagueId"
    }

    @Transactional
    fun addChampionManually(leagueId: Int, season: Int, teamId: Int, teamName: String) {
        val existing = championRepository.findByTeamId(teamId)
            .find { it.leagueId == leagueId && it.season == season }
        if (existing != null) return  // 이미 존재하면 스킵
        championRepository.save(
            com.sscore.entity.ChampionEntity(
                leagueId = leagueId,
                season   = season,
                teamId   = teamId,
                teamName = teamName
            )
        )
    }

    fun syncChampions(leagueId: Int, season: Int) =
        apiService.syncChampions(leagueId, season)

    fun getStandings(leagueId: Int, season: Int): List<StandingDto> =
        apiService.fetchStandings(leagueId, season)


    // ── 선수 경기 기록 ─────────────────────────────────────────

    fun getPlayerMatches(playerId: Long): List<PlayerMatchRecordDto> {
        val player = playerRepository.findById(playerId).orElse(null) ?: return emptyList()

        // 최신 시즌 스탯에서 팀 정보 가져오기 (PlayerEntity.team보다 정확)
        val latestStat = playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(playerId)
            .firstOrNull() ?: return emptyList()
        val season = latestStat.season
        val team = teamRepository.findAll().find { it.name == latestStat.teamName }
            ?: player.team ?: return emptyList()

        val matches = matchRepository.findByTeamAndSeason(team.teamId, season)
        val allPlayerStats = playerStatRepository.findByPlayerId(playerId)
            .associateBy { it.fixtureId }

        val lastName = player.name.split(" ").last()

        return matches.map { m ->
            val isHome  = m.homeTeamId == team.teamId
            val oppId   = if (isHome) m.awayTeamId else m.homeTeamId
            val oppName = if (isHome) m.awayTeam   else m.homeTeam
            val oppLogo = oppId?.let { teamRepository.findById(it).orElse(null)?.logoUrl }
            val stat    = m.fixtureId?.let { allPlayerStats[it] }

            val allEvents = m.fixtureId?.let { matchEventRepository.findByFixtureId(it) } ?: emptyList()
            val myEvents   = allEvents.filter {
                it.playerName?.contains(lastName, ignoreCase = true) == true ||
                        it.assistPlayerName?.contains(lastName, ignoreCase = true) == true
            }
            val goals       = myEvents.count { it.type == "Goal" && it.playerName?.contains(lastName, ignoreCase = true) == true }
            val assists     = myEvents.count { it.type == "Goal" && it.assistPlayerName?.contains(lastName, ignoreCase = true) == true }
            val yellowCards = myEvents.count { it.type == "Card" && it.detail?.contains("Yellow") == true }
            val redCards    = myEvents.count { it.type == "Card" && it.detail?.contains("Red")    == true }

            // 출전 여부: events 있으면 출전, player_stats 참고
            val played = goals > 0 || assists > 0 || yellowCards > 0 || redCards > 0 || (stat?.minutesPlayed ?: 0) > 0
            val status = when {
                played && (stat?.minutesPlayed ?: 0) < 60 && (stat?.minutesPlayed ?: 0) > 0 -> "SUBBED_ON"
                played -> "STARTED"
                stat != null && (stat.minutesPlayed ?: 0) == 0 -> "BENCH"
                else   -> "NO_DATA"
            }

            PlayerMatchRecordDto(
                fixtureId     = m.fixtureId ?: 0L,
                matchDate     = m.matchDate,
                opponentName  = oppName,
                opponentLogo  = oppLogo,
                score         = m.score,
                isHome        = isHome,
                status        = status,
                minutesPlayed = stat?.minutesPlayed,
                goals         = goals,
                assists       = assists,
                yellowCards   = yellowCards,
                redCards      = redCards,
                rating        = stat?.rating,
                homeTeam      = m.homeTeam,
                awayTeam      = m.awayTeam,
                homeTeamId    = m.homeTeamId,
                awayTeamId    = m.awayTeamId,
                matchTime     = m.matchTime,
                matchRound    = m.matchRound,
                leagueId      = m.leagueId,
                season        = m.season,
                stadium       = m.stadium
            )
        }.sortedByDescending { it.matchDate }
    }

    // ── 선수 시즌별 통계 목록 ─────────────────────────────────

    fun getPlayerSeasonStatsList(playerId: Long): List<PlayerSeasonStatsDetailDto> {
        val player = playerRepository.findById(playerId).orElse(null)
        val allPlayerStats = playerStatRepository.findByPlayerId(playerId)

        return playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(playerId)
            .map { s ->
                // 해당 시즌+팀의 경기 fixtureId 목록
                val teamEntity = teamRepository.findAll().find { it.name == s.teamName }
                val fixtureIds = if (teamEntity != null)
                    matchRepository.findByTeamAndSeason(teamEntity.teamId, s.season)
                        .mapNotNull { it.fixtureId }.toSet()
                else emptySet()

                val ps = allPlayerStats.filter { it.fixtureId in fixtureIds }

                val totalPasses   = ps.sumOf { it.passesTotal }
                val successPasses = ps.sumOf { it.passesAccuracy }  // 성공 패스 횟수
                val passAccAvg    = if (totalPasses > 0) successPasses.toDouble() / totalPasses * 100 else 0.0

                PlayerSeasonStatsDetailDto(
                    season        = s.season,
                    teamName      = s.teamName,
                    leagueName    = s.leagueName,
                    position      = player?.position,
                    appearances   = s.appearances,
                    goals         = s.goals,
                    assists       = s.assists,
                    avgRating     = s.rating,
                    yellowCards   = s.yellowCards,
                    redCards      = s.redCards,
                    passesTotal   = ps.sumOf { it.passesTotal },
                    passesAccuracyAvg = passAccAvg,
                    passesKey     = ps.sumOf { it.passesKey },
                    shotsTotal    = ps.sumOf { it.shotsTotal },
                    shotsOnTarget = ps.sumOf { it.shotsOnTarget },
                    xgTotal       = ps.mapNotNull { it.xg }.sum(),
                    tacklesTotal  = ps.sumOf { it.tacklesTotal },
                    tacklesBlocks = ps.sumOf { it.tacklesBlocks },
                    tacklesInterceptions = ps.sumOf { it.tacklesInterceptions },
                    duelsTotal    = ps.sumOf { it.duelsTotal },
                    duelsWon      = ps.sumOf { it.duelsWon },
                    foulsDrawn    = ps.sumOf { it.foulsDrawn },
                    foulsCommitted = ps.sumOf { it.foulsCommitted },
                    goalkeeperSaves = ps.sumOf { it.goalkeeperSaves },
                    goalkeeperConceded = ps.sumOf { it.goalkeeperConceded },
                    goalkeeperSavesInsideBox = ps.sumOf { it.goalkeeperSavesInsideBox },
                    matchesWithData = ps.size
                )
            }
    }

    // ── 선수 경기별 상세 스탯 (기존) ──────────────────────────────
    fun getPlayerMatchStats(playerId: Long, fixtureId: Long): PlayerMatchStatDto? {
        val details = apiService.fetchFixtureDetails(fixtureId) ?: return null
        val playersSection = details["players"] as? List<Map<String, Any>> ?: return null

        for (teamEntry in playersSection) {
            val teamName = (teamEntry["team"] as? Map<*, *>)?.get("name")?.toString() ?: ""
            val players  = teamEntry["players"] as? List<Map<*, *>> ?: continue

            for (pd in players) {
                val p   = pd["player"] as? Map<*, *> ?: continue
                val pid = (p["id"] as? Number)?.toLong() ?: continue
                if (pid != playerId) continue

                val st  = (pd["statistics"] as? List<Map<*, *>>)?.firstOrNull() ?: continue
                val games    = st["games"]    as? Map<*, *>
                val shots    = st["shots"]    as? Map<*, *>
                val gls      = st["goals"]    as? Map<*, *>
                val passes   = st["passes"]   as? Map<*, *>
                val tackles  = st["tackles"]  as? Map<*, *>
                val duels    = st["duels"]    as? Map<*, *>
                val dribbles = st["dribbles"] as? Map<*, *>
                val fouls    = st["fouls"]    as? Map<*, *>

                fun num(m: Map<*, *>?, k: String) = (m?.get(k) as? Number)?.toInt()

                // nationality·age: 경기 응답에 없으면 DB PlayerEntity 폴백
                val dbPlayer = playerRepository.findById(pid).orElse(null)
                val nationality = p["nationality"]?.toString() ?: dbPlayer?.nationality
                val age = (p["age"] as? Number)?.toInt() ?: dbPlayer?.age

                return PlayerMatchStatDto(
                    playerId             = pid,
                    playerName           = p["name"]?.toString() ?: "",
                    photoUrl             = "https://media.api-sports.io/football/players/$pid.png",
                    teamName             = teamName,
                    position             = games?.get("position")?.toString(),
                    nationality          = nationality,
                    age                  = age,
                    minutesPlayed        = num(games, "minutes"),
                    rating               = (games?.get("rating") as? String)?.toDoubleOrNull(),
                    goals                = num(gls, "total"),
                    assists              = num(gls, "assists"),
                    passesTotal          = num(passes, "total"),
                    passesAccuracy       = (passes?.get("accuracy") as? String)?.toIntOrNull()
                        ?: (passes?.get("accuracy") as? Number)?.toInt(),
                    keyPasses            = num(passes, "key"),
                    shotsTotal           = num(shots, "total"),
                    shotsOn              = num(shots, "on"),
                    tacklesTotal         = num(tackles, "total"),
                    tacklesBlocks        = num(tackles, "blocks"),
                    tacklesInterceptions = num(tackles, "interceptions"),
                    duelsTotal           = num(duels, "total"),
                    duelsWon             = num(duels, "won"),
                    dribblesAttempts     = num(dribbles, "attempts"),
                    dribblesSuccess      = num(dribbles, "success"),
                    foulsDrawn           = num(fouls, "drawn"),
                    foulsCommitted       = num(fouls, "committed"),
                    saves                = num(gls, "saves"),
                    goalsConceded        = num(gls, "conceded"),
                    opponentXg           = run {
                        // fixture statistics에서 상대팀 expected_goals 추출
                        val statsSection = details["statistics"] as? List<Map<String, Any>>
                        statsSection?.firstOrNull { teamEntry ->
                            (teamEntry["team"] as? Map<*, *>)?.get("name")?.toString() != teamName
                        }?.let { opponentEntry ->
                            (opponentEntry["statistics"] as? List<Map<*, *>>)
                                ?.firstOrNull { it["type"]?.toString()
                                    .equals("expected_goals", ignoreCase = true) }
                                ?.get("value")?.toString()
                                ?.toDoubleOrNull()
                        }
                    }
                )
            }
        }
        return null
    }

    // ── 리그 상세 ──────────────────────────────────────────────────

    // ── 통합 검색 ──────────────────────────────────────────────

    // 선수 스탯(평점·출전시간) 일괄 수집 - 백그라운드 실행
    fun importPlayerStatsForLeague(season: Int, leagueId: Int, delayMs: Long = 200): String {
        val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
        val total = matches.size
        log.info("[PlayerStats] 시작 — 리그={} 시즌={} 총{}경기", leagueId, season, total)

        Thread({
            var done = 0; var skipped = 0; var failed = 0
            matches.forEach { m ->
                val fid = m.fixtureId ?: return@forEach
                if (playerStatRepository.findByFixtureId(fid).isNotEmpty()) {
                    skipped++; return@forEach
                }
                try {
                    getMatchInfo(fid)
                    done++
                    log.info("[PlayerStats] {}/{} fixture={} 저장", done, total, fid)
                    Thread.sleep(delayMs)
                } catch (e: Exception) {
                    failed++
                    log.warn("[PlayerStats] fixture={} 실패: {}", fid, e.message)
                }
            }
            log.info("[PlayerStats] 완료 — 저장:{} 스킵:{} 실패:{}", done, skipped, failed)
        }, "player-stats-import").apply { isDaemon = true; start() }

        return "백그라운드 실행 중 — 총 ${total}경기 (서버 콘솔에서 진행상황 확인)"
    }

    fun importMatchEventsForTeam(teamId: Int, season: Int): String {
        val matches = matchRepository.findByTeamAndSeason(teamId, season)
        var imported = 0; var skipped = 0
        matches.forEach { m ->
            val fid = m.fixtureId ?: return@forEach
            if (matchEventRepository.findByFixtureId(fid).isNotEmpty()) { skipped++; return@forEach }
            try {
                val events = apiService.fetchFixtureEvents(fid)
                if (events.isNotEmpty()) { matchEventRepository.saveAll(events); imported++ }
                Thread.sleep(400)
            } catch (e: Exception) { /* 무시 */ }
        }
        return "완료: 임포트 $imported 경기, 스킵 $skipped 경기"
    }

    // 5대 리그 전체 경기 이벤트 일괄 수집
    fun importAllLeagueMatchEvents(season: Int, delayMs: Long = 400, leagueId: Int? = null): String {
        val bigFive  = if (leagueId != null) listOf(leagueId) else listOf(39, 140, 78, 135, 61)
        var imported = 0; var skipped = 0; var failed = 0

        bigFive.forEach { leagueId ->
            val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
            log.info("[EventImport] 리그={} 시즌={} 경기수={}", leagueId, season, matches.size)

            matches.forEach { m ->
                val fid = m.fixtureId ?: return@forEach
                // 이미 있으면 스킵
                if (matchEventRepository.findByFixtureId(fid).isNotEmpty()) {
                    skipped++; return@forEach
                }
                try {
                    val events = apiService.fetchFixtureEvents(fid)
                    if (events.isNotEmpty()) {
                        matchEventRepository.saveAll(events)
                        imported++
                        log.info("[EventImport] fixture={} 이벤트={}개 저장", fid, events.size)
                    }
                    Thread.sleep(delayMs)
                } catch (e: Exception) {
                    failed++
                    log.warn("[EventImport] fixture={} 실패: {}", fid, e.message)
                }
            }
        }
        return "완료 — 임포트: ${imported}경기 / 스킵(기존): ${skipped}경기 / 실패: ${failed}경기"
    }

    fun search(query: String): List<SearchResultDto> {
        val q = query.trim()
        if (q.length < 2) return emptyList()

        // 리그 순서 (정렬 기준)
        val leagueOrder = mapOf(
            "Premier League" to 1, "La Liga" to 2,
            "Bundesliga" to 3, "Serie A" to 4, "Ligue 1" to 5
        )

        val players    = mutableListOf<SearchResultDto>()
        val teams      = mutableListOf<SearchResultDto>()
        val leagues    = mutableListOf<SearchResultDto>()
        val seenTeams  = mutableSetOf<Int>()

        // 1. 선수 검색 (소속팀은 최근 시즌 스탯 기준)
        playerRepository.searchByName(q).take(10).forEach { p ->
            val latestStat = playerSeasonStatRepository
                .findByPlayerIdOrderBySeasonDesc(p.playerId).firstOrNull()
            val teamName   = latestStat?.teamName ?: p.team?.name
            val leagueName = latestStat?.leagueName
            val order      = leagueOrder[leagueName] ?: 99

            players.add(SearchResultDto("PLAYER", p.playerId,
                p.name, teamName, p.photoUrl, null, order))

            // 선수 소속팀 연관 결과
            p.team?.let { t ->
                if (seenTeams.add(t.teamId)) {
                    val leagueId = matchRepository.findLeagueIdByTeamId(t.teamId).firstOrNull()
                    teams.add(SearchResultDto("TEAM", t.teamId.toLong(),
                        t.name, leagueIdToName(leagueId ?: 39), t.logoUrl, leagueId,
                        leagueOrder[leagueIdToEnglishName(leagueId ?: 39)] ?: 99))
                }
            }
        }

        // 2. 팀 직접 검색
        teamRepository.searchByName(q).take(5).forEach { t ->
            if (seenTeams.add(t.teamId)) {
                val leagueId = matchRepository.findLeagueIdByTeamId(t.teamId).firstOrNull()
                val engName  = leagueIdToEnglishName(leagueId ?: 39)
                teams.add(SearchResultDto("TEAM", t.teamId.toLong(),
                    t.name, leagueIdToName(leagueId ?: 39), t.logoUrl, leagueId,
                    leagueOrder[engName] ?: 99))
            }
        }

        // 3. 리그 검색
        leagueRepository.searchByName(q).take(3).forEach { l ->
            leagues.add(SearchResultDto("LEAGUE", l.leagueId.toLong(),
                l.name, l.country, l.logoUrl, l.leagueId,
                leagueOrder[l.name] ?: 99))
        }

        // 정렬: 선수 우선(리그순) → 팀(리그순) → 리그
        return players.sortedBy { it.sortOrder } +
                teams.sortedBy   { it.sortOrder } +
                leagues.sortedBy { it.sortOrder }
    }

    // ── 리그 플레이어 통계 ────────────────────────────────────

    private fun leagueIdToEnglishName(leagueId: Int) = when (leagueId) {
        39  -> "Premier League"
        140 -> "La Liga"
        78  -> "Bundesliga"
        135 -> "Serie A"
        61  -> "Ligue 1"
        else -> ""
    }

    fun getLeaguePlayerStats(leagueId: Int, season: Int): LeaguePlayerStatsDto {
        val leagueName = leagueIdToEnglishName(leagueId)
        val rawStats   = playerSeasonStatRepository.findBySeasonAndLeagueName(season, leagueName)

        // 선수별 중복 제거 (출장수 최대 기록 1개)
        val stats = rawStats.groupBy { it.playerId }
            .mapValues { (_, e) -> e.maxByOrNull { it.appearances }!! }
            .values.toList()

        val playerMap = playerRepository.findAllById(stats.map { it.playerId })
            .associateBy { it.playerId }

        fun rank(selector: (PlayerSeasonStatEntity) -> Double, minApps: Int = 1) =
            stats.filter { selector(it) > 0 && it.appearances >= minApps }
                .sortedByDescending { selector(it) }
                .take(10)
                .mapIndexed { i, s ->
                    PlayerRankDto(
                        rank        = i + 1,
                        playerId    = s.playerId,
                        playerName  = playerMap[s.playerId]?.name ?: "",
                        photoUrl    = playerMap[s.playerId]?.photoUrl,
                        value       = selector(s),
                        appearances = s.appearances
                    )
                }

        return LeaguePlayerStatsDto(
            goalRanking        = rank({ it.goals.toDouble() }),
            assistRanking      = rank({ it.assists.toDouble() }),
            attackPointRanking = rank({ (it.goals + it.assists).toDouble() }),
            ratingRanking      = rank({ it.rating ?: 0.0 }, minApps = 15)
        )
    }

    // ── 리그 팀 통계 ──────────────────────────────────────────

    fun getLeagueTeamStats(leagueId: Int, season: Int): LeagueTeamStatsDto {
        val today   = java.time.LocalDate.now().toString()
        val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
            .filter { it.matchDate <= today }

        data class TS(var played: Int = 0, var goalsFor: Int = 0,
                      var goalsAgainst: Int = 0, var cleanSheets: Int = 0)

        val tsMap = mutableMapOf<String, TS>()
        matches.forEach { m ->
            // 종료된 경기만 (matchTime이 숫자나 "종료" 포함)
            val finished = m.matchTime?.let {
                it.contains("종료") || it.any { c -> c.isDigit() }
            } ?: false
            if (!finished) return@forEach

            // "1 - 0", "1-0", "2:1" 등 다양한 형식 처리
            val cleaned = m.score?.replace(" ", "") ?: return@forEach
            val parts = cleaned.split("-", ":")
                .mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 2) return@forEach
            val hg = parts[0]; val ag = parts[1]

            tsMap.getOrPut(m.homeTeam) { TS() }.also {
                it.played++; it.goalsFor += hg; it.goalsAgainst += ag
                if (ag == 0) it.cleanSheets++
            }
            tsMap.getOrPut(m.awayTeam) { TS() }.also {
                it.played++; it.goalsFor += ag; it.goalsAgainst += hg
                if (hg == 0) it.cleanSheets++
            }
        }

        val logoMap = teamRepository.findAll().associateBy { it.name }
        fun tr(name: String, value: Double, display: String): TeamRankDto {
            val t = logoMap[name]
            return TeamRankDto(t?.teamId, name, t?.logoUrl, value, display)
        }

        val valid = tsMap.filter { it.value.played > 0 }

        // 선수 평균 평점 (출장 5경기 이상만)
        val leagueName = leagueIdToEnglishName(leagueId)
        val avgRating = playerSeasonStatRepository.findBySeasonAndLeagueName(season, leagueName)
            .filter { (it.rating ?: 0.0) > 0 && it.appearances >= 5 }
            .groupBy { it.teamName }
            .mapValues { (_, e) -> e.mapNotNull { it.rating }.average() }

        return LeagueTeamStatsDto(
            avgRatingRanking = avgRating
                .map { (n, r) -> tr(n, r, "%.2f".format(r)) }
                .sortedByDescending { it.value },

            goalsPerGameRanking = valid
                .map { (n, s) ->
                    val v = s.goalsFor.toDouble() / s.played
                    tr(n, v, "%.2f".format(v))
                }.sortedByDescending { it.value },

            concededPerGameRanking = valid
                .map { (n, s) ->
                    val v = s.goalsAgainst.toDouble() / s.played
                    tr(n, v, "%.2f".format(v))
                }.sortedBy { it.value },   // 낮은 순

            cleanSheetRanking = valid
                .map { (n, s) ->
                    tr(n, s.cleanSheets.toDouble(), "${s.cleanSheets}회")
                }.sortedByDescending { it.value }
        )
    }

    // ── 리그 기본 정보 ─────────────────────────────────────────

    fun getLeagueInfo(leagueId: Int): LeagueInfoDto? {
        val league = leagueRepository.findById(leagueId).orElse(null) ?: return null
        return LeagueInfoDto(
            leagueId   = league.leagueId,
            name       = league.name,
            country    = league.country,
            logoUrl    = league.logoUrl,
            leagueType = league.leagueType
        )
    }

    fun getLeagueMatches(leagueId: Int, season: Int): List<LeagueMatchDayDto> {
        return matchRepository.findByLeagueIdAndSeason(leagueId, season)
            .sortedWith(compareByDescending<MatchEntity> { it.matchDate }.thenByDescending { it.matchTime })
            .groupBy { it.matchDate }
            .map { (date, matches) ->
                LeagueMatchDayDto(
                    date    = date,
                    matches = matches.map { m ->
                        MatchDto(
                            fixtureId   = m.fixtureId,
                            homeTeam    = m.homeTeam,
                            awayTeam    = m.awayTeam,
                            homeTeamId  = m.homeTeamId,
                            awayTeamId  = m.awayTeamId,
                            matchDate   = m.matchDate,
                            matchTime   = m.matchTime,
                            score       = m.score,
                            leagueId    = m.leagueId,
                            season      = m.season,
                            matchRound  = m.matchRound,
                            stadium     = m.stadium,
                            referee     = m.referee,
                            homeLogoUrl = teamRepository.findById(m.homeTeamId ?: 0)
                                .orElse(null)?.logoUrl,
                            awayLogoUrl = teamRepository.findById(m.awayTeamId ?: 0)
                                .orElse(null)?.logoUrl
                        )
                    }
                )
            }
    }

    fun getPlayerDetail(playerId: Long): PlayerDetailDto? {
        val player = playerRepository.findById(playerId).orElse(null) ?: return null

        // api-football에서 키·생년월일·주발·등번호
        // 등번호: 최근 시즌부터 순서대로 시도해서 squadNumber가 있는 결과 사용
        val recentSeasons = playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(playerId)
            .map { it.season }
            .distinct()
            .take(3)
            .ifEmpty { listOf(2025, 2024) }

        val api = recentSeasons.firstNotNullOfOrNull { season ->
            try {
                val result = apiService.fetchPlayerDetails(playerId, season)
                if (result?.squadNumber != null) result else null
            } catch (e: Exception) { null }
        } ?: try { apiService.fetchPlayerDetails(playerId, recentSeasons.first()) } catch (e: Exception) { null }

        // 이번 시즌 스탯 (DB, 빅5 리그)
        val currentStats = playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(playerId)
            .firstOrNull { it.season == 2025 }
            ?.let { s ->
                CareerStatDto(
                    season = s.season, teamName = s.teamName, leagueName = s.leagueName,
                    appearances = s.appearances, goals = s.goals, assists = s.assists,
                    rating = s.rating, minutesPlayed = s.minutesPlayed
                )
            }

        // 트로피: player가 뛴 팀+시즌 vs ChampionEntity 교차
        val careerRecords = playerSeasonStatRepository.findByPlayerIdOrderBySeasonDesc(playerId)
        val careerPairs   = careerRecords.map { it.teamName to it.season }.toSet()
        val trophies = championRepository.findAll()
            .filter { champ -> careerPairs.any { (team, season) -> champ.teamName == team && champ.season == season } }
            .groupBy { it.leagueId }
            .map { (lid, entries) ->
                PlayerTrophyDto(
                    trophyName = leagueIdToName(lid),
                    count      = entries.size,
                    seasons    = entries.sortedByDescending { it.season }
                        .map { "${it.season}/${(it.season + 1).toString().takeLast(2)}" }
                )
            }
            .sortedByDescending { it.count }

        // 소속팀: 가장 최근 시즌 스탯 기준 (PlayerEntity.team보다 정확)
        val recentTeamName = playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(player.playerId)
            .firstOrNull()?.teamName ?: player.team?.name

        return PlayerDetailDto(
            playerId        = player.playerId,
            name            = player.name,
            photoUrl        = player.photoUrl ?: "https://media.api-sports.io/football/players/${player.playerId}.png",
            currentTeamName = recentTeamName,
            height          = api?.height,
            birthDate       = api?.birthDate,
            nationality     = player.nationality ?: api?.nationality,
            age             = player.age ?: api?.age,
            position        = player.position,
            squadNumber     = api?.squadNumber,
            preferredFoot   = api?.preferredFoot,
            currentSeasonStats = currentStats,
            trophies        = trophies
        )
    }

    fun getPlayerProfile(playerId: Long): PlayerProfileDto? {
        val player = playerRepository.findById(playerId).orElse(null) ?: return null

        val careerStats = playerSeasonStatRepository
            .findByPlayerIdOrderBySeasonDesc(playerId)
            .map { s ->
                CareerStatDto(
                    season        = s.season,
                    teamName      = s.teamName,
                    leagueName    = s.leagueName,
                    appearances   = s.appearances,
                    goals         = s.goals,
                    assists       = s.assists,
                    rating        = s.rating,
                    minutesPlayed = s.minutesPlayed
                )
            }

        return PlayerProfileDto(
            playerId        = player.playerId,
            name            = player.name,
            nationality     = player.nationality,
            position        = player.position,
            age             = player.age,
            photoUrl        = player.photoUrl
                ?: "https://media.api-sports.io/football/players/${player.playerId}.png",
            currentTeamName = player.team?.name,
            careerStats     = careerStats
        )
    }

    fun getTeamTrophies(teamId: Int): List<TrophyDto> {
        return championRepository.findByTeamId(teamId)
            .groupBy { it.leagueId }
            .map { (lid, entries) ->
                val sorted = entries.sortedByDescending { it.season }
                TrophyDto(
                    league        = leagueIdToName(lid),
                    country       = null,
                    winCount      = entries.size,
                    lastWinSeason = "${sorted.first().season}/${(sorted.first().season + 1).toString().takeLast(2)}"
                )
            }
            .sortedByDescending { it.winCount }
    }

    fun getTeamSquad(teamId: Int): SquadDto {
        val players = apiService.fetchTeamSquad(teamId)

        // DB에서 국적 보완
        val dbMap = playerRepository.findAllById(players.map { it.playerId }.toSet())
            .associateBy { it.playerId }

        val enriched = players.map { p ->
            p.copy(nationality = p.nationality ?: dbMap[p.playerId]?.nationality)
        }

        fun group(pos: String) = enriched
            .filter { it.position.contains(pos, ignoreCase = true) }
            .sortedBy { it.number ?: 99 }

        return SquadDto(
            goalkeepers = group("Goalkeeper"),
            defenders   = group("Defender"),
            midfielders = group("Midfielder"),
            attackers   = group("Attacker")
        )
    }

    fun getTeamNameById(teamId: Int): String? =
        teamRepository.findById(teamId).orElse(null)?.name

    // DB에 실제로 뭐가 있는지 확인용 (teamName 매핑 검증)
    data class PlayerStatDebug(
        val playerId: Long, val playerName: String,
        val appearances: Int, val teamName: String
    )
    fun debugPlayerStats(teamId: Int, season: Int): List<PlayerStatDebug> {
        val teamName = teamRepository.findById(teamId).orElse(null)?.name ?: return emptyList()
        return playerSeasonStatRepository.findBySeasonAndTeamName(season, teamName).map {
            val name = playerRepository.findById(it.playerId).orElse(null)?.name ?: "unknown"
            PlayerStatDebug(it.playerId, name, it.appearances, it.teamName)
        }
    }

    fun getTeamStats(teamId: Int, leagueId: Int, season: Int): TeamStatsDto {
        // 홈/원정 성적
        val (home, away) = apiService.fetchTeamRecord(teamId, leagueId, season)

        // 팀 이름 조회 (playerSeasonStat은 teamName 기준으로 저장됨)
        val teamName = teamRepository.findById(teamId).orElse(null)?.name
            ?: return TeamStatsDto(home, away, emptyList(), emptyList(), emptyList())

        // PL+CL 등 중복 기록이 있는 선수는 출장수가 가장 많은 기록(주 리그)만 사용
        val stats = playerSeasonStatRepository.findBySeasonAndTeamName(season, teamName)
            .groupBy { it.playerId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.appearances }!! }
            .values.toList()
        if (stats.isEmpty()) return TeamStatsDto(home, away, emptyList(), emptyList(), emptyList())

        // playerId → PlayerEntity 맵
        val playerIds = stats.map { it.playerId }.toSet()
        val playerMap = playerRepository.findAllById(playerIds).associateBy { it.playerId }

        fun buildRanking(
            selector: (PlayerSeasonStatEntity) -> Double,
            minValue: Double = 0.0,
            minAppearances: Int = 1  // 최소 출장 수 미달 시 랭킹 제외
        ): List<PlayerRankDto> {
            val sorted = stats
                .filter { selector(it) > minValue && it.appearances >= minAppearances }
                .sortedByDescending { selector(it) }
            var currentRank = 1
            return sorted.mapIndexed { idx, s ->
                if (idx > 0 && selector(s) != selector(sorted[idx - 1])) currentRank = idx + 1
                val p = playerMap[s.playerId]
                PlayerRankDto(
                    rank        = currentRank,
                    playerId    = s.playerId,
                    playerName  = p?.name ?: "Unknown",
                    photoUrl    = p?.photoUrl,
                    value       = selector(s),
                    appearances = s.appearances
                )
            }
        }

        return TeamStatsDto(
            homeRecord    = home,
            awayRecord    = away,
            ratingRanking = buildRanking({ it.rating ?: 0.0 }, 0.0, minAppearances = 15),
            goalRanking   = buildRanking({ it.goals.toDouble() },    minAppearances = 1),
            assistRanking = buildRanking({ it.assists.toDouble() },  minAppearances = 1)
        )
    }

    fun getTeamMatches(teamId: Int, page: Int, size: Int = 15): TeamMatchesPageDto {
        val pageable = PageRequest.of(
            page, size,
            Sort.by(Sort.Direction.DESC, "matchDate").and(Sort.by(Sort.Direction.DESC, "matchTime"))
        )
        val result = matchRepository.findAllMatchesByTeam(teamId, pageable)
        return TeamMatchesPageDto(
            matches     = result.content.map { it.toDto() },
            hasMore     = !result.isLast,
            currentPage = page,
            totalPages  = result.totalPages
        )
    }

    fun getTeamOverview(teamId: Int, leagueId: Int, season: Int): TeamOverviewDto {
        // 1. 최근 5경기 (종료된 경기만)
        val recentEntities = matchRepository.findRecentFinishedByTeam(
            teamId, PageRequest.of(0, 5)
        )
        val recentMatches = recentEntities.map { m ->
            val parts      = m.score.split("-")
            val homeScore  = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val awayScore  = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val isHome     = m.homeTeamId == teamId
            val myScore    = if (isHome) homeScore else awayScore
            val oppScore   = if (isHome) awayScore else homeScore
            val result     = when {
                myScore > oppScore  -> "W"
                myScore == oppScore -> "D"
                else                -> "L"
            }
            RecentMatchDto(
                fixtureId  = m.fixtureId,
                homeTeamId = m.homeTeamId,
                awayTeamId = m.awayTeamId,
                homeTeam   = m.homeTeam,
                awayTeam   = m.awayTeam,
                score      = m.score,
                result     = result,
                matchDate  = m.matchDate
            )
        }

        // 2. 미니 순위표 (해당팀 ±1)
        val allStandings = apiService.fetchStandings(leagueId, season)
        val teamRank     = allStandings.find { it.teamId == teamId }?.rank ?: 0
        val miniStandings = if (teamRank > 0)
            allStandings.filter { it.rank in (teamRank - 1)..(teamRank + 1) }
        else emptyList()

        // 3. 경기장 정보
        val venue = apiService.fetchTeamVenue(teamId)

        // 4. 리그 트로피 (champions 테이블에서 조회)
        val trophies = championRepository.findByTeamId(teamId)
            .groupBy { it.leagueId }
            .map { (leagueId, entries) ->
                val sorted = entries.sortedByDescending { it.season }
                TrophyDto(
                    league        = sorted.first().teamName.let {
                        leagueIdToName(leagueId)
                    },
                    country       = null,
                    winCount      = entries.size,
                    lastWinSeason = "${sorted.first().season}/${(sorted.first().season + 1).toString().takeLast(2)}"
                )
            }
            .sortedByDescending { it.winCount }

        return TeamOverviewDto(recentMatches, miniStandings, venue, trophies)
    }

    // 🌟 추가된 H2H 로직
    fun getH2H(homeId: Int, awayId: Int): H2HSummaryDto {
        val h2hMatches = apiService.fetchH2H(homeId, awayId)

        val last10Matches = h2hMatches
            .filter { it.score != "0-0" || it.matchTime.contains("종료") }
            .sortedByDescending { it.matchDate }
            .take(10)

        var homeWins = 0
        var draws = 0
        var awayWins = 0

        last10Matches.forEach { match ->
            val scores = match.score.split("-")
            if (scores.size == 2) {
                val hScore = scores[0].trim().toIntOrNull() ?: 0
                val aScore = scores[1].trim().toIntOrNull() ?: 0

                val isRequestHomeIsMatchHome = match.homeTeamId == homeId
                val reqHomeScore = if (isRequestHomeIsMatchHome) hScore else aScore
                val reqAwayScore = if (isRequestHomeIsMatchHome) aScore else hScore

                when {
                    reqHomeScore > reqAwayScore -> homeWins++
                    reqHomeScore < reqAwayScore -> awayWins++
                    else -> draws++
                }
            }
        }

        return H2HSummaryDto(homeWins, draws, awayWins, last10Matches)
    }

    fun getMatchInfo(fixtureId: Long): MatchInfoResponseDto {
        val match = matchRepository.findByFixtureId(fixtureId)
        val fixtureDetails = apiService.fetchFixtureDetails(fixtureId)

        var refereeName = match?.referee
        var bestPlayerId: Long = 0L
        var bestPlayerName = ""
        var bestPlayerTeam = ""
        var highestRating = 0.0
        var foundMom = false
        var kstFullDateString = "일시 정보 없음"

        val matchEvents = mutableListOf<MatchEventDto>()

        val statList = mutableListOf<MatchStatItemDto>()
        val homeStatsMap = mutableMapOf<String, String>()
        val awayStatsMap = mutableMapOf<String, String>()

        var hKeyPasses = 0; var aKeyPasses = 0
        var hTouches = 0; var aTouches = 0
        var hTackles = 0; var aTackles = 0
        var hInterceptions = 0; var aInterceptions = 0
        var hClearances = 0; var aClearances = 0

        var hDuelsTotal = 0; var hDuelsWon = 0
        var aDuelsTotal = 0; var aDuelsWon = 0
        var hDribbles = 0; var aDribbles = 0

        val homePlayers = mutableListOf<MomPlayerDto>()
        val awayPlayers = mutableListOf<MomPlayerDto>()

        if (fixtureDetails != null) {
            val fixtureMap = fixtureDetails["fixture"] as? Map<*, *>
            val fetchedReferee = fixtureMap?.get("referee")?.toString()
            if (!fetchedReferee.isNullOrBlank() && fetchedReferee != "null") {
                refereeName = fetchedReferee
                if (match != null && match.referee != refereeName) {
                    matchRepository.save(match.copy(referee = refereeName))
                }
            }

            val fullDateStr = fixtureMap?.get("date")?.toString()
            if (!fullDateStr.isNullOrBlank() && fullDateStr != "null") {
                try {
                    val zdt = ZonedDateTime.parse(fullDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    val kstZdt = zdt.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    val kstMonth = kstZdt.monthValue
                    val kstDay = kstZdt.dayOfMonth
                    val kstTime = kstZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    kstFullDateString = "${kstZdt.year}년 ${kstMonth}월 ${kstDay}일 $kstTime"
                } catch (e: Exception) {
                    kstFullDateString = fullDateStr
                }
            }

            val statsArray = fixtureDetails["statistics"] as? List<Map<*, *>>
            statsArray?.forEach { teamStatMap ->
                val tId = (teamStatMap["team"] as? Map<*, *>)?.get("id")?.toString()?.toIntOrNull()
                val sArray = teamStatMap["statistics"] as? List<Map<*, *>> ?: emptyList()
                val targetMap = if (tId == match?.homeTeamId) homeStatsMap else awayStatsMap

                sArray.forEach { s ->
                    val type = s["type"]?.toString() ?: ""
                    val value = s["value"]?.toString()?.replace("%", "") ?: "0"
                    if (value != "null") targetMap[type] = value else targetMap[type] = "0"
                }
            }

            val playersArray = fixtureDetails["players"] as? List<Map<*, *>>
            val playerStatsToSave = mutableListOf<PlayerStatEntity>()
            if (playersArray != null) {
                for (teamData in playersArray) {
                    val teamMap = teamData["team"] as? Map<*, *>
                    val tId = (teamMap?.get("id") as? Number)?.toInt() ?: 0
                    val isHome = (tId == match?.homeTeamId)
                    val teamName = teamMap?.get("name")?.toString() ?: "알 수 없는 팀"

                    val squadArray = teamData["players"] as? List<Map<*, *>> ?: continue
                    for (playerData in squadArray) {
                        val playerMap = playerData["player"] as? Map<*, *>
                        val playerId = (playerMap?.get("id") as? Number)?.toLong() ?: 0L
                        val pName = playerMap?.get("name")?.toString() ?: ""

                        val statsDataArray = playerData["statistics"] as? List<Map<*, *>>
                        val firstStat = statsDataArray?.firstOrNull()
                        val gamesMap   = firstStat?.get("games")   as? Map<*, *>
                        val passesMap  = firstStat?.get("passes")  as? Map<*, *>
                        val tacklesMap = firstStat?.get("tackles") as? Map<*, *>
                        val duelsMap   = firstStat?.get("duels")   as? Map<*, *>
                        val dribblesMap= firstStat?.get("dribbles")as? Map<*, *>
                        val shotsMap   = firstStat?.get("shots")   as? Map<*, *>
                        val goalsMap   = firstStat?.get("goals")   as? Map<*, *>
                        val foulsMap   = firstStat?.get("fouls")   as? Map<*, *>

                        val ratingStr = gamesMap?.get("rating")?.toString()
                        val rating = ratingStr?.toDoubleOrNull() ?: 0.0
                        val minutes = (gamesMap?.get("minutes") as? Number)?.toInt() ?: 0
                        val position = gamesMap?.get("position")?.toString()

                        if (rating > highestRating) {
                            highestRating = rating
                            bestPlayerId = playerId
                            bestPlayerName = pName
                            bestPlayerTeam = teamName
                            foundMom = true
                        }

                        val playerDto = MomPlayerDto(
                            playerId = playerId, name = pName, teamName = teamName,
                            photoUrl = "https://media.api-sports.io/football/players/$playerId.png", rating = rating
                        )

                        val kp      = (passesMap?.get("key") as? Number)?.toInt() ?: 0
                        val touches = (passesMap?.get("total") as? Number)?.toInt() ?: 0
                        val tack    = (tacklesMap?.get("total") as? Number)?.toInt() ?: 0
                        val inter   = (tacklesMap?.get("interceptions") as? Number)?.toInt() ?: 0
                        val clear   = (tacklesMap?.get("blocks") as? Number)?.toInt() ?: 0
                        val dTot    = (duelsMap?.get("total") as? Number)?.toInt() ?: 0
                        val dWon    = (duelsMap?.get("won") as? Number)?.toInt() ?: 0
                        val drb     = (dribblesMap?.get("success") as? Number)?.toInt() ?: 0

                        // player_stats 저장용 엔티티 생성
                        val xg = shotsMap?.get("xg")?.toString()?.toDoubleOrNull()

                        playerStatsToSave.add(PlayerStatEntity(
                            fixtureId    = fixtureId,
                            teamId       = tId,
                            playerId     = playerId,
                            playerName   = pName,
                            rating       = if (rating > 0) rating else null,
                            position     = position,
                            minutesPlayed = if (minutes > 0) minutes else null,
                            goals        = (goalsMap?.get("total") as? Number)?.toInt() ?: 0,
                            assists      = (goalsMap?.get("assists") as? Number)?.toInt() ?: 0,
                            xg           = xg,
                            passesTotal  = touches,
                            passesAccuracy = (passesMap?.get("accuracy")?.toString()?.toDoubleOrNull()?.toInt()) ?: 0,
                            passesKey    = kp,
                            shotsTotal   = (shotsMap?.get("total") as? Number)?.toInt() ?: 0,
                            shotsOnTarget = (shotsMap?.get("on") as? Number)?.toInt() ?: 0,
                            shotsOffTarget = 0,
                            ballTouches  = touches,
                            turnovers    = 0,
                            tacklesTotal = tack,
                            tacklesBlocks = clear,
                            tacklesInterceptions = inter,
                            duelsTotal   = dTot,
                            duelsWon     = dWon,
                            foulsDrawn   = (foulsMap?.get("drawn") as? Number)?.toInt() ?: 0,
                            foulsCommitted = (foulsMap?.get("committed") as? Number)?.toInt() ?: 0,
                            goalkeeperSaves = (goalsMap?.get("saves") as? Number)?.toInt() ?: 0,
                            goalkeeperConceded = (goalsMap?.get("conceded") as? Number)?.toInt() ?: 0,
                            goalkeeperSavesInsideBox = 0,
                            goalkeeperPunches = 0
                        ))

                        if (isHome) {
                            hKeyPasses += kp; hTouches += touches; hTackles += tack; hInterceptions += inter; hClearances += clear
                            hDuelsTotal += dTot; hDuelsWon += dWon; hDribbles += drb
                            if (rating > 0) homePlayers.add(playerDto)
                        } else {
                            aKeyPasses += kp; aTouches += touches; aTackles += tack; aInterceptions += inter; aClearances += clear
                            aDuelsTotal += dTot; aDuelsWon += dWon; aDribbles += drb
                            if (rating > 0) awayPlayers.add(playerDto)
                        }
                    }
                }
            }
            // player_stats DB 저장 (기존 데이터 삭제 후 upsert)
            try {
                if (playerStatsToSave.isNotEmpty()) {
                    playerStatRepository.deleteByFixtureId(fixtureId)
                    playerStatRepository.saveAll(playerStatsToSave)
                }
            } catch (e: Exception) {
                log.error("player_stats 저장 실패 fixture={}: {}", fixtureId, e.message)
            }

            val eventsArray = fixtureDetails["events"] as? List<Map<*, *>>
            eventsArray?.forEach { eventMap ->
                val timeMap = eventMap["time"] as? Map<*, *>
                val teamMap = eventMap["team"] as? Map<*, *>
                val playerMap = eventMap["player"] as? Map<*, *>
                val assistMap = eventMap["assist"] as? Map<*, *>

                // 🌟 변수명 에러 완벽 해결 (time, extraTime, teamName, assistPlayerName)
                matchEvents.add(
                    MatchEventDto(
                        time = (timeMap?.get("elapsed") as? Number)?.toInt() ?: 0,
                        extraTime = (timeMap?.get("extra") as? Number)?.toInt(),
                        teamId = (teamMap?.get("id") as? Number)?.toInt() ?: 0,
                        teamName = teamMap?.get("name")?.toString() ?: "",
                        type = eventMap["type"]?.toString() ?: "",
                        detail = eventMap["detail"]?.toString(),
                        playerName = playerMap?.get("name")?.toString(),
                        assistPlayerName = assistMap?.get("name")?.toString()
                    )
                )
            }
        }

        fun addStat(cat: String, name: String, hVal: String, aVal: String) {
            statList.add(MatchStatItemDto(cat, name, hVal, aVal))
        }

        addStat("OVERALL", "점유율", homeStatsMap["Ball Possession"] ?: "50", awayStatsMap["Ball Possession"] ?: "50")
        addStat("OVERALL", "전체 슈팅수", homeStatsMap["Total Shots"] ?: "0", awayStatsMap["Total Shots"] ?: "0")
        addStat("OVERALL", "유효 슈팅수", homeStatsMap["Shots on Goal"] ?: "0", awayStatsMap["Shots on Goal"] ?: "0")
        addStat("OVERALL", "상대편 박스 터치", hTouches.toString(), aTouches.toString())


        val hXg = homeStatsMap["expected_goals"] ?: homeStatsMap["Expected Goals"] ?: "-"
        val aXg = awayStatsMap["expected_goals"] ?: awayStatsMap["Expected Goals"] ?: "-"
        if (hXg != "-" || aXg != "-") {
            addStat("OVERALL", "xG", hXg, aXg)
        }

        val hPassTot = homeStatsMap["Total passes"] ?: "0"
        val hPassAcc = homeStatsMap["Passes accurate"] ?: "0"
        val hPassPct = homeStatsMap["Passes %"] ?: "0"
        val aPassTot = awayStatsMap["Total passes"] ?: "0"
        val aPassAcc = awayStatsMap["Passes accurate"] ?: "0"
        val aPassPct = awayStatsMap["Passes %"] ?: "0"
        addStat("OVERALL", "패스 성공률", "$hPassAcc ($hPassPct%)", "$aPassAcc ($aPassPct%)")
        addStat("OVERALL", "반칙수", homeStatsMap["Fouls"] ?: "0", awayStatsMap["Fouls"] ?: "0")
        addStat("OVERALL", "오프사이드", homeStatsMap["Offsides"] ?: "0", awayStatsMap["Offsides"] ?: "0")
        addStat("OVERALL", "코너킥", homeStatsMap["Corner Kicks"] ?: "0", awayStatsMap["Corner Kicks"] ?: "0")

        addStat("PASSES", "총 패스 횟수", hPassTot, aPassTot)
        addStat("PASSES", "성공 횟수", hPassAcc, aPassAcc)
        if (hKeyPasses > 0 || aKeyPasses > 0) {
            addStat("PASSES", "키 패스", hKeyPasses.toString(), aKeyPasses.toString())
        }

        addStat("DEFENSE", "태클 횟수", hTackles.toString(), aTackles.toString())
        addStat("DEFENSE", "가로채기 횟수", hInterceptions.toString(), aInterceptions.toString())
        addStat("DEFENSE", "걷어내기 횟수", hClearances.toString(), aClearances.toString())
        addStat("DEFENSE", "골키퍼 선방횟수", homeStatsMap["Goalkeeper Saves"] ?: "0", awayStatsMap["Goalkeeper Saves"] ?: "0")

        addStat("DUELS", "볼 경합 성공수", "$hDuelsWon / $hDuelsTotal", "$aDuelsWon / $aDuelsTotal")
        addStat("DUELS", "드리블 성공수", hDribbles.toString(), aDribbles.toString())

        val homeTop5 = homePlayers.sortedByDescending { it.rating }.take(5)
        val awayTop5 = awayPlayers.sortedByDescending { it.rating }.take(5)

        if (!foundMom) {
            val dbPlayerStats = playerStatRepository.findByFixtureId(fixtureId)
            val dbBest = dbPlayerStats.maxByOrNull { it.rating ?: 0.0 }
            if (dbBest != null && (dbBest.rating ?: 0.0) > 0.0) {
                bestPlayerId = dbBest.playerId
                bestPlayerName = dbBest.playerName
                bestPlayerTeam = when (dbBest.teamId) {
                    match?.homeTeamId -> match?.homeTeam ?: "알 수 없는 팀"
                    match?.awayTeamId -> match?.awayTeam ?: "알 수 없는 팀"
                    else -> "알 수 없는 팀"
                }
                highestRating = dbBest.rating ?: 0.0
                foundMom = true
            }
        }

        val momPlayer = if (foundMom) {
            MomPlayerDto(
                playerId = bestPlayerId,
                name = bestPlayerName,
                teamName = bestPlayerTeam,
                photoUrl = "https://media.api-sports.io/football/players/$bestPlayerId.png",
                rating = Math.round(highestRating * 10) / 10.0
            )
        } else null

        return MatchInfoResponseDto(
            fixtureId = fixtureId,
            momPlayer = momPlayer,
            referee = if (!refereeName.isNullOrBlank() && refereeName != "null") refereeName else "심판 배정 전",
            matchFullDate = kstFullDateString,
            events = matchEvents,
            statistics = statList,
            homeTopPlayers = homeTop5,
            awayTopPlayers = awayTop5
        )
    }
}