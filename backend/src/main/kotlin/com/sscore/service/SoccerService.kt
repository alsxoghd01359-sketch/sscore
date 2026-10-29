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
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.sscore.football.SeasonResolver
import com.sscore.league.LeagueCatalog
import com.sscore.dto.toMatchDto
import com.sscore.football.optionalExternal

@Service
class SoccerService(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val playerStatRepository: PlayerStatRepository,
    private val playerSeasonStatRepository: PlayerSeasonStatRepository,
    private val apiService: ApiFootballService,
    private val championRepository: ChampionRepository,
    private val leagueRepository: LeagueRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasons: SeasonResolver,
    private val leagueCatalog: LeagueCatalog
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

    private fun MatchEntity.toDto() = toMatchDto()

    // 시즌별로 외부 API에서 받은 경기를 저장한다. 외부 API 오류가 나면 그 시즌부터 중단하고 오류를 그대로 올린다(앞 시즌 저장분은 유지).
    fun saveSeasonsRange(start: Int, end: Int) {
        for (season in start..end) {
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
        }
    }

    fun getLineups(fixtureId: Long): List<LineupDto> = apiService.fetchLineups(fixtureId)

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
        val matches = matchRepository.findByTeamAndSeason(teamId, season, leagueCatalog.ids())
        var imported = 0; var skipped = 0; var failed = 0
        matches.forEach { m ->
            val fid = m.fixtureId ?: return@forEach
            if (matchEventRepository.findByFixtureId(fid).isNotEmpty()) { skipped++; return@forEach }
            try {
                val events = apiService.fetchFixtureEvents(fid)
                if (events.isNotEmpty()) { matchEventRepository.saveAll(events); imported++ }
                Thread.sleep(400)
            } catch (e: Exception) {
                failed++
                log.warn("[EventImport] fixture={} 실패: {}", fid, e.message)
            }
        }
        return "완료: 임포트 $imported 경기, 스킵 $skipped 경기, 실패 $failed 경기"
    }

    // 5대 리그 전체 경기 이벤트 일괄 수집
    fun importAllLeagueMatchEvents(season: Int, delayMs: Long = 400, leagueId: Int? = null): String {
        val bigFive  = if (leagueId != null) listOf(leagueId) else leagueCatalog.ids()
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
                        t.name, leagueCatalog.nameKo(leagueId ?: 39), t.logoUrl, leagueId,
                        leagueOrder[leagueCatalog.nameEn(leagueId ?: 39)] ?: 99))
                }
            }
        }

        // 2. 팀 직접 검색
        teamRepository.searchByName(q).take(5).forEach { t ->
            if (seenTeams.add(t.teamId)) {
                val leagueId = matchRepository.findLeagueIdByTeamId(t.teamId).firstOrNull()
                val engName  = leagueCatalog.nameEn(leagueId ?: 39)
                teams.add(SearchResultDto("TEAM", t.teamId.toLong(),
                    t.name, leagueCatalog.nameKo(leagueId ?: 39), t.logoUrl, leagueId,
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