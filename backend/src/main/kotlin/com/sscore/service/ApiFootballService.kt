package com.sscore.service

import com.sscore.dto.*
import com.sscore.entity.MatchEntity
import com.sscore.entity.MatchEventEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.entity.ChampionEntity
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ApiFootballService(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val playerSeasonStatRepository: PlayerSeasonStatRepository,
    private val matchRepository: MatchRepository,
    private val championRepository: ChampionRepository,
    private val restTemplate: RestTemplate,
    @Value("\${API_FOOTBALL_KEY}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val apiUrl = "https://v3.football.api-sports.io"

    // ─────────────────────────────────────────────
    // 팀 동기화
    // ─────────────────────────────────────────────
    @Transactional
    fun syncTeams(leagueId: Int, season: Int): String {
        val url = "$apiUrl/teams?league=$leagueId&season=$season"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity = HttpEntity<String>(headers)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, ApiFootballTeamResponse::class.java)
            val body = response.body
            if (body?.response != null) {
                val existingTeamIds = teamRepository.findAll().map { it.teamId }.toSet()
                val newTeamsToSave = body.response
                    .filter { !existingTeamIds.contains(it.team.id) }
                    .map { wrapper ->
                        TeamEntity(
                            teamId  = wrapper.team.id,
                            name    = wrapper.team.name,
                            country = wrapper.team.country,
                            logoUrl = wrapper.team.logo
                        )
                    }
                if (newTeamsToSave.isNotEmpty()) {
                    teamRepository.saveAll(newTeamsToSave)
                    log.info("{} 시즌 리그 {} 새로운 팀 {}개 저장 완료", season, leagueId, newTeamsToSave.size)
                    "성공: 새로운 팀 ${newTeamsToSave.size}개 동기화 완료"
                } else {
                    "성공: 모든 팀이 이미 최신 상태입니다."
                }
            } else {
                "실패: 응답 데이터가 없습니다."
            }
        } catch (e: Exception) {
            "실패: ${e.message}"
        }
    }

    // ─────────────────────────────────────────────
    // 시간 파싱 (UTC → KST)
    // ─────────────────────────────────────────────
    private fun parseToKst(fullDateStr: String, statusShort: String): Pair<String, String> {
        var kstDate = if (fullDateStr.contains("T")) fullDateStr.substringBefore("T") else fullDateStr
        var kstTimeAndStatus = statusShort
        try {
            val zdt    = ZonedDateTime.parse(fullDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val kstZdt = zdt.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            kstDate = kstZdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val timeStr = kstZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
            kstTimeAndStatus = when (statusShort) {
                "NS", "TBD"          -> timeStr
                "FT", "AET", "PEN"   -> "$timeStr (종료)"
                "HT", "1H", "2H", "ET", "P" -> "$timeStr (진행중)"
                "CANC", "PSTP"       -> "$timeStr (취소)"
                else                 -> "$timeStr ($statusShort)"
            }
        } catch (e: Exception) {
            log.error("시간 파싱 에러: ${e.message}")
        }
        return Pair(kstDate, kstTimeAndStatus)
    }

    // ─────────────────────────────────────────────
    // 라이브 경기 조회
    // ─────────────────────────────────────────────
    // ─────────────────────────────────────────────
    // 순위표 조회
    // ─────────────────────────────────────────────
    fun fetchStandings(leagueId: Int, season: Int): List<StandingDto> {
        val url = "$apiUrl/standings?league=$leagueId&season=$season"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val leagueObj = (response?.get("response") as? List<Map<String, Any>>)
                ?.firstOrNull()?.get("league") as? Map<*, *> ?: return emptyList()
            val rows = ((leagueObj["standings"] as? List<*>)
                ?.firstOrNull() as? List<Map<String, Any>>) ?: return emptyList()

            rows.mapNotNull { row ->
                val team  = row["team"]  as? Map<*, *> ?: return@mapNotNull null
                val all   = row["all"]   as? Map<*, *>
                val goals = row["goals"] as? Map<*, *>
                StandingDto(
                    rank         = (row["rank"]          as? Number)?.toInt() ?: 0,
                    teamId       = (team["id"]           as? Number)?.toInt() ?: 0,
                    teamName     = team["name"]?.toString() ?: "",
                    points       = (row["points"]        as? Number)?.toInt() ?: 0,
                    played       = (all?.get("played")   as? Number)?.toInt() ?: 0,
                    win          = (all?.get("win")      as? Number)?.toInt() ?: 0,
                    draw         = (all?.get("draw")     as? Number)?.toInt() ?: 0,
                    lose         = (all?.get("lose")     as? Number)?.toInt() ?: 0,
                    goalsFor     = (goals?.get("for")    as? Number)?.toInt() ?: 0,
                    goalsAgainst = (goals?.get("against") as? Number)?.toInt() ?: 0,
                    goalsDiff    = (row["goalsDiff"]     as? Number)?.toInt() ?: 0,
                    form         = row["form"]?.toString()
                )
            }
        } catch (e: Exception) {
            log.error("순위표 조회 실패 league={} season={}: {}", leagueId, season, e.message)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // 홈/원정 성적 조회 (standings home/away 분리)
    // ─────────────────────────────────────────────
    fun fetchTeamRecord(teamId: Int, leagueId: Int, season: Int): Pair<RecordDto?, RecordDto?> {
        val url = "$apiUrl/standings?league=$leagueId&season=$season"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response  = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val leagueObj = (response?.get("response") as? List<Map<String, Any>>)
                ?.firstOrNull()?.get("league") as? Map<*, *> ?: return Pair(null, null)
            val rows = ((leagueObj["standings"] as? List<*>)
                ?.firstOrNull() as? List<Map<String, Any>>) ?: return Pair(null, null)

            val row = rows.firstOrNull {
                ((it["team"] as? Map<*, *>)?.get("id") as? Number)?.toInt() == teamId
            } ?: return Pair(null, null)

            fun parseRecord(key: String): RecordDto? {
                val rec   = row[key] as? Map<*, *> ?: return null
                val goals = rec["goals"] as? Map<*, *>
                val w = (rec["win"]   as? Number)?.toInt() ?: 0
                val d = (rec["draw"]  as? Number)?.toInt() ?: 0
                val l = (rec["lose"]  as? Number)?.toInt() ?: 0
                val gf = (goals?.get("for")     as? Number)?.toInt() ?: 0
                val ga = (goals?.get("against") as? Number)?.toInt() ?: 0
                return RecordDto(
                    played       = (rec["played"] as? Number)?.toInt() ?: 0,
                    win          = w, draw = d, lose = l,
                    goalsFor     = gf, goalsAgainst = ga,
                    goalsDiff    = gf - ga,
                    points       = w * 3 + d
                )
            }

            Pair(parseRecord("home"), parseRecord("away"))
        } catch (e: Exception) {
            log.error("홈/원정 성적 조회 실패 teamId={}: {}", teamId, e.message)
            Pair(null, null)
        }
    }

    // ─────────────────────────────────────────────
    // 선수 상세 정보 (키, 생년월일, 주발, 등번호)
    // ─────────────────────────────────────────────
    data class PlayerApiInfo(
        val height: String?, val birthDate: String?,
        val nationality: String?, val age: Int?,
        val squadNumber: Int?, val preferredFoot: String?
    )

    fun fetchPlayerDetails(playerId: Long, season: Int = 2025): PlayerApiInfo? {
        val url     = "$apiUrl/players?id=$playerId&season=$season"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val item  = (response?.get("response") as? List<Map<String, Any>>)
                ?.firstOrNull() ?: return null
            val p     = item["player"] as? Map<*, *> ?: return null
            val stats = (item["statistics"] as? List<Map<*, *>>)?.firstOrNull()
            val birth = p["birth"] as? Map<*, *>
            PlayerApiInfo(
                height        = p["height"]?.toString()?.takeIf { it != "null" },
                birthDate     = birth?.get("date")?.toString()?.takeIf { it != "null" },
                nationality   = p["nationality"]?.toString(),
                age           = (p["age"] as? Number)?.toInt(),
                squadNumber   = (stats?.get("games") as? Map<*, *>)
                    ?.get("number")?.let { (it as? Number)?.toInt() },
                preferredFoot = p["foot"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            log.error("선수 상세 조회 실패 playerId={}: {}", playerId, e.message)
            null
        }
    }

    // ─────────────────────────────────────────────
    // 경기 이벤트만 조회 (목표/카드/교체)
    // ─────────────────────────────────────────────
    fun fetchFixtureEvents(fixtureId: Long): List<MatchEventEntity> {
        val url     = "$apiUrl/fixtures/events?fixture=$fixtureId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val events   = (response?.get("response") as? List<Map<*, *>>) ?: return emptyList()
            events.mapNotNull { ev ->
                val timeMap = ev["time"] as? Map<*, *>
                val team    = ev["team"] as? Map<*, *>
                val player  = ev["player"] as? Map<*, *>
                val assist  = ev["assist"] as? Map<*, *>
                MatchEventEntity(
                    fixtureId          = fixtureId,
                    time               = (timeMap?.get("elapsed") as? Number)?.toInt() ?: 0,
                    extraTime          = (timeMap?.get("extra") as? Number)?.toInt(),
                    teamId             = (team?.get("id") as? Number)?.toInt() ?: 0,
                    teamName           = team?.get("name")?.toString() ?: "",
                    type               = ev["type"]?.toString() ?: "",
                    detail             = ev["detail"]?.toString(),
                    playerName         = player?.get("name")?.toString(),
                    assistPlayerName   = assist?.get("name")?.toString()
                )
            }
        } catch (e: Exception) {
            log.error("이벤트 조회 실패 fixtureId={}: {}", fixtureId, e.message)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // 현재 스쿼드 조회 (/players/squads)
    // ─────────────────────────────────────────────
    fun fetchTeamSquad(teamId: Int): List<SquadPlayerDto> {
        val url     = "$apiUrl/players/squads?team=$teamId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val teams    = (response?.get("response") as? List<Map<String, Any>>)
                ?: return emptyList()
            val players  = (teams.firstOrNull()?.get("players") as? List<Map<*, *>>)
                ?: return emptyList()

            players.mapNotNull { p ->
                val pid = (p["id"] as? Number)?.toLong() ?: return@mapNotNull null
                SquadPlayerDto(
                    playerId    = pid,
                    name        = p["name"]?.toString() ?: "",
                    number      = (p["number"] as? Number)?.toInt(),
                    position    = p["position"]?.toString() ?: "",
                    age         = (p["age"] as? Number)?.toInt(),
                    nationality = null,   // DB에서 채움
                    photoUrl    = p["photo"]?.toString()
                        ?: "https://media.api-sports.io/football/players/$pid.png"
                )
            }
        } catch (e: Exception) {
            log.error("스쿼드 조회 실패 teamId={}: {}", teamId, e.message)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // 경기장 정보 조회
    // ─────────────────────────────────────────────
    fun fetchTeamVenue(teamId: Int): VenueDto? {
        val url = "$apiUrl/teams?id=$teamId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val teamData = (response?.get("response") as? List<Map<String, Any>>)
                ?.firstOrNull() ?: return null
            val venue = teamData["venue"] as? Map<*, *> ?: return null
            VenueDto(
                name     = venue["name"]?.toString(),
                address  = venue["address"]?.toString(),
                city     = venue["city"]?.toString(),
                capacity = (venue["capacity"] as? Number)?.toInt(),
                surface  = venue["surface"]?.toString(),
                imageUrl = venue["image"]?.toString()
            )
        } catch (e: Exception) {
            log.error("경기장 정보 조회 실패 teamId={}: {}", teamId, e.message)
            null
        }
    }

    // ─────────────────────────────────────────────
    // 리그 트로피 조회 (우승만, 리그별 집계)
    // ─────────────────────────────────────────────
    // ─────────────────────────────────────────────
    // 리그 우승팀 저장 (순위표 1위 → champions 테이블)
    // ─────────────────────────────────────────────
    @Transactional
    fun syncChampions(leagueId: Int, season: Int) {
        if (championRepository.existsByLeagueIdAndSeason(leagueId, season)) {
            log.info("[우승팀] leagueId={} season={} 이미 저장됨, 스킵", leagueId, season)
            return
        }
        val standings = fetchStandings(leagueId, season)
        val champion  = standings.firstOrNull { it.rank == 1 } ?: run {
            log.warn("[우승팀] leagueId={} season={} 1위 데이터 없음", leagueId, season)
            return
        }
        championRepository.save(ChampionEntity(
            leagueId = leagueId,
            season   = season,
            teamId   = champion.teamId,
            teamName = champion.teamName
        ))
        log.info("[우승팀] {} 시즌 {} 우승: {}", season, leagueId, champion.teamName)
    }

    fun fetchTeamTrophies(teamId: Int): List<TrophyDto> {
        val url = "$apiUrl/trophies?team=$teamId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body

            // 에러 체크
            val errors = response?.get("errors")
            if (errors != null && errors.toString() != "[]" && errors.toString() != "{}") {
                log.error("[트로피] API 에러 teamId={}: {}", teamId, errors)
                return emptyList()
            }

            val items = response?.get("response") as? List<Map<String, Any>>
            log.info("[트로피] teamId={} 전체 항목 수: {}", teamId, items?.size ?: 0)

            if (items.isNullOrEmpty()) {
                log.warn("[트로피] teamId={} 항목 없음. 응답 키: {}", teamId, response?.keys)
                return emptyList()
            }

            log.info("[트로피] 첫 항목 샘플: {}", items.first())

            val winners = items.filter { it["place"]?.toString().equals("Winner", ignoreCase = true) }
            log.info("[트로피] teamId={} 우승 {}건 / 전체 {}건", teamId, winners.size, items.size)

            if (winners.isEmpty()) {
                val placeSamples = items.mapNotNull { it["place"]?.toString() }.distinct()
                log.warn("[트로피] Winner 없음. 실제 place 값 목록: {}", placeSamples)
            }

            winners
                .groupBy { it["league"]?.toString() ?: "Unknown" }
                .map { (league, entries) ->
                    val sorted = entries.sortedByDescending { it["season"]?.toString() ?: "" }
                    TrophyDto(
                        league        = league,
                        country       = sorted.firstOrNull()?.get("country")?.toString(),
                        winCount      = entries.size,
                        lastWinSeason = sorted.firstOrNull()?.get("season")?.toString() ?: "-"
                    )
                }
                .sortedByDescending { it.winCount }
        } catch (e: Exception) {
            log.error("[트로피] 조회 실패 teamId={}: {}", teamId, e.message)
            emptyList()
        }
    }

    fun fetchLiveMatches(): List<MatchDto> {
        val url = "$apiUrl/fixtures?live=all"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)

        val response  = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
        val apiMatches = response?.get("response") as? List<Map<String, Any>> ?: return emptyList()

        return apiMatches.map { item ->
            val fixture = item["fixture"] as Map<*, *>
            val teams   = item["teams"]   as Map<*, *>
            val goals   = item["goals"]   as Map<*, *>
            val league  = item["league"]  as Map<*, *>
            val home    = teams["home"]   as Map<*, *>
            val away    = teams["away"]   as Map<*, *>
            val status  = fixture["status"] as Map<*, *>
            val (kstDate, kstTime) = parseToKst(fixture["date"].toString(), status["short"].toString())
            MatchDto(
                fixtureId  = (fixture["id"] as Number).toLong(),
                homeTeam   = home["name"].toString(),
                awayTeam   = away["name"].toString(),
                homeTeamId = (home["id"] as Number).toInt(),
                awayTeamId = (away["id"] as Number).toInt(),
                matchDate  = kstDate,
                matchTime  = kstTime,
                score      = "${goals["home"]}-${goals["away"]}",
                season     = (league["season"] as Number).toInt(),
                leagueId   = (league["id"] as Number).toInt(),
                matchRound = league["round"]?.toString(),
                stadium    = (fixture["venue"] as Map<*, *>)["name"]?.toString(),
                referee    = fixture["referee"]?.toString()
            )
        }
    }

    // ─────────────────────────────────────────────
    // 시즌 전체 경기 조회 (5대 리그)
    // ─────────────────────────────────────────────
    fun fetchMatchesBySeason(season: Int): List<MatchDto> {
        val bigFiveLeagueIds = listOf(39, 140, 78, 135, 61)
        val allCollectedMatches = mutableListOf<MatchDto>()
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)

        for (leagueId in bigFiveLeagueIds) {
            val url = "$apiUrl/fixtures?league=$leagueId&season=$season"
            try {
                val response   = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
                val apiMatches = response?.get("response") as? List<Map<String, Any>> ?: continue
                val mapped = apiMatches.map { item ->
                    val fixture = item["fixture"] as Map<*, *>
                    val teams   = item["teams"]   as Map<*, *>
                    val goals   = item["goals"]   as Map<*, *>
                    val league  = item["league"]  as Map<*, *>
                    val home    = teams["home"]   as Map<*, *>
                    val away    = teams["away"]   as Map<*, *>
                    val status  = fixture["status"] as Map<*, *>
                    val (kstDate, kstTime) = parseToKst(fixture["date"].toString(), status["short"].toString())
                    MatchDto(
                        fixtureId  = (fixture["id"] as Number).toLong(),
                        homeTeam   = home["name"].toString(),
                        awayTeam   = away["name"].toString(),
                        homeTeamId = (home["id"] as Number).toInt(),
                        awayTeamId = (away["id"] as Number).toInt(),
                        matchDate  = kstDate,
                        matchTime  = kstTime,
                        score      = if (goals["home"] != null && goals["away"] != null)
                            "${goals["home"]}-${goals["away"]}" else "0-0",
                        season     = (league["season"] as Number).toInt(),
                        leagueId   = (league["id"] as Number).toInt(),
                        matchRound = league["round"]?.toString(),
                        stadium    = (fixture["venue"] as? Map<*, *>)?.get("name")?.toString(),
                        referee    = fixture["referee"]?.toString()
                    )
                }
                allCollectedMatches.addAll(mapped)
            } catch (e: Exception) {
                log.error("리그 {} 경기 수집 중 에러 발생", leagueId)
            }
        }
        return allCollectedMatches
    }

    // ─────────────────────────────────────────────
    // H2H (맞대결 전적)
    // ─────────────────────────────────────────────
    fun fetchH2H(homeId: Int, awayId: Int): List<MatchDto> {
        val url = "$apiUrl/fixtures/headtohead?h2h=$homeId-$awayId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)

        return try {
            val response   = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val apiMatches = response?.get("response") as? List<Map<String, Any>> ?: return emptyList()
            apiMatches.mapNotNull { item ->
                val fixture = item["fixture"] as? Map<*, *> ?: return@mapNotNull null
                val teams   = item["teams"]   as? Map<*, *> ?: return@mapNotNull null
                val goals   = item["goals"]   as? Map<*, *> ?: return@mapNotNull null
                val league  = item["league"]  as? Map<*, *> ?: return@mapNotNull null
                val home    = teams["home"]   as? Map<*, *> ?: return@mapNotNull null
                val away    = teams["away"]   as? Map<*, *> ?: return@mapNotNull null
                val status  = fixture["status"] as? Map<*, *> ?: return@mapNotNull null
                val (kstDate, kstTime) = parseToKst(fixture["date"].toString(), status["short"].toString())
                MatchDto(
                    fixtureId  = (fixture["id"] as Number).toLong(),
                    homeTeam   = home["name"].toString(),
                    awayTeam   = away["name"].toString(),
                    homeTeamId = (home["id"] as Number).toInt(),
                    awayTeamId = (away["id"] as Number).toInt(),
                    matchDate  = kstDate,
                    matchTime  = kstTime,
                    score      = if (goals["home"] != null && goals["away"] != null)
                        "${goals["home"]}-${goals["away"]}" else "0-0",
                    season     = (league["season"] as Number).toInt(),
                    leagueId   = (league["id"] as Number).toInt(),
                    matchRound = league["round"]?.toString(),
                    stadium    = (fixture["venue"] as? Map<*, *>)?.get("name")?.toString(),
                    referee    = fixture["referee"]?.toString()
                )
            }
        } catch (e: Exception) {
            log.error("H2H 데이터 수집 중 에러 발생", e)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // 라인업 조회
    // ─────────────────────────────────────────────
    fun fetchLineups(fixtureId: Long): List<LineupDto> {
        val url = "$apiUrl/fixtures/lineups?fixture=$fixtureId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)

        return try {
            val response     = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val responseList = response?.get("response") as? List<Map<String, Any>> ?: return emptyList()

            responseList.map { teamData ->
                val team          = teamData["team"]        as? Map<*, *>
                val coach         = teamData["coach"]       as? Map<*, *>
                val formation     = teamData["formation"]?.toString()
                val startXIList   = teamData["startXI"]     as? List<Map<*, *>> ?: emptyList()
                val substitutesList = teamData["substitutes"] as? List<Map<*, *>> ?: emptyList()

                fun parsePlayer(wrapper: Map<*, *>): PlayerDto {
                    val p = wrapper["player"] as? Map<*, *> ?: emptyMap<String, Any>()
                    return PlayerDto(
                        id     = (p["id"]     as? Number)?.toLong(),
                        name   = p["name"]?.toString() ?: "",
                        number = (p["number"] as? Number)?.toInt(),
                        pos    = p["pos"]?.toString(),
                        grid   = p["grid"]?.toString()
                    )
                }

                LineupDto(
                    teamName     = team?.get("name")?.toString() ?: "",
                    formation    = formation,
                    coachName    = coach?.get("name")?.toString(),
                    coachPhotoUrl = coach?.get("photo")?.toString(),
                    startXI      = startXIList.map    { parsePlayer(it) },
                    substitutes  = substitutesList.map { parsePlayer(it) }
                )
            }
        } catch (e: Exception) {
            log.error("라인업 데이터 수집 중 에러: {}", e.message)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // 경기 상세 정보 (통계/이벤트용)
    // ─────────────────────────────────────────────
    fun fetchFixtureDetails(fixtureId: Long): Map<String, Any>? {
        val url = "$apiUrl/fixtures?id=$fixtureId"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        return try {
            val response     = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val responseList = response?.get("response") as? List<Map<String, Any>>
            responseList?.firstOrNull()
        } catch (e: Exception) {
            log.error("경기 상세 정보 API 통신 에러: {}", e.message)
            null
        }
    }

    // ─────────────────────────────────────────────
    // 대량 적재용 sync 메서드
    // ─────────────────────────────────────────────

    @Transactional
    fun syncFixtures(leagueId: Int, season: Int) {
        val url = "$apiUrl/fixtures?league=$leagueId&season=$season"
        val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
        val entity  = HttpEntity<String>(headers)
        try {
            val response   = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body
            val apiMatches = response?.get("response") as? List<Map<String, Any>> ?: return

            val existingIds = matchRepository.findAll().mapNotNull { it.fixtureId }.toHashSet()
            val toSave = mutableListOf<MatchEntity>()

            for (item in apiMatches) {
                val fixture   = item["fixture"] as? Map<*, *> ?: continue
                val fixtureId = (fixture["id"] as? Number)?.toLong() ?: continue
                if (existingIds.contains(fixtureId)) continue

                val teams  = item["teams"]  as? Map<*, *> ?: continue
                val goals  = item["goals"]  as? Map<*, *> ?: continue
                val league = item["league"] as? Map<*, *> ?: continue
                val home   = teams["home"]  as? Map<*, *> ?: continue
                val away   = teams["away"]  as? Map<*, *> ?: continue
                val status = fixture["status"] as? Map<*, *> ?: continue
                val (kstDate, kstTime) = parseToKst(fixture["date"].toString(), status["short"].toString())

                toSave.add(MatchEntity(
                    fixtureId   = fixtureId,
                    homeTeam    = home["name"].toString(),
                    awayTeam    = away["name"].toString(),
                    homeTeamId  = (home["id"] as? Number)?.toInt(),
                    awayTeamId  = (away["id"] as? Number)?.toInt(),
                    matchDate   = kstDate,
                    matchTime   = kstTime,
                    score       = if (goals["home"] != null && goals["away"] != null)
                        "${goals["home"]}-${goals["away"]}" else "0-0",
                    season      = (league["season"] as? Number)?.toInt() ?: season,
                    leagueId    = (league["id"]     as? Number)?.toInt() ?: leagueId,
                    matchRound  = league["round"]?.toString(),
                    stadium     = (fixture["venue"] as? Map<*, *>)?.get("name")?.toString(),
                    referee     = fixture["referee"]?.toString()
                ))
            }

            if (toSave.isNotEmpty()) matchRepository.saveAll(toSave)
            log.info("✅ 리그 {} 시즌 {} 경기 적재 완료 — 신규 {}건 / 전체 {}건",
                leagueId, season, toSave.size, apiMatches.size)
        } catch (e: Exception) {
            log.error("❌ 리그 {} 시즌 {} 경기 적재 에러: {}", leagueId, season, e.message)
            throw e
        }
    }

    @Transactional
    fun syncPlayers(teamId: Int, season: Int = 2025) {
        val team = teamRepository.findById(teamId).orElse(null) ?: run {
            log.warn("팀 {} 이 DB에 없어 선수 동기화 스킵", teamId)
            return
        }
        var page       = 1
        var totalPages = 1

        do {
            val url = "$apiUrl/players?team=$teamId&season=$season&page=$page"
            val headers = HttpHeaders().apply { set("x-apisports-key", apiKey) }
            val entity  = HttpEntity<String>(headers)
            try {
                val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java).body ?: break
                val paging   = response["paging"] as? Map<*, *>
                totalPages   = (paging?.get("total") as? Number)?.toInt() ?: 1
                val players  = response["response"] as? List<Map<String, Any>> ?: break

                for (item in players) {
                    val playerInfo = item["player"]     as? Map<*, *> ?: continue
                    val statsList  = item["statistics"] as? List<Map<*, *>> ?: continue
                    val playerId   = (playerInfo["id"] as? Number)?.toLong() ?: continue
                    val name       = playerInfo["name"]?.toString() ?: continue

                    // ① 올바른 팀+시즌+빅5리그 필터링 → ② 출장수 가장 많은 리그 선택
                    // 빅5 리그만: PL(39), 라리가(140), 분데스(78), 세리에A(135), 리그1(61)
                    val top5LeagueIds = setOf(39, 140, 78, 135, 61)
                    val stats = statsList
                        .filter { st ->
                            val stLeague   = st["league"] as? Map<*, *>
                            val stTeam     = st["team"]   as? Map<*, *>
                            val stLeagueId = (stLeague?.get("id")     as? Number)?.toInt()
                            val stSeason   = (stLeague?.get("season") as? Number)?.toInt()
                            val stTeamId   = (stTeam?.get("id")       as? Number)?.toInt()
                            stSeason == season &&
                                    stTeamId == teamId &&
                                    stLeagueId in top5LeagueIds   // 컵·유럽대회 제외
                        }
                        .maxByOrNull { st ->
                            (st["games"] as? Map<*, *>)
                                ?.get("appearences")
                                ?.let { (it as? Number)?.toInt() } ?: 0
                        } ?: continue   // 빅5 리그 출장 없으면 스킵

                    if (!playerRepository.existsById(playerId)) {
                        playerRepository.save(PlayerEntity(
                            playerId    = playerId,
                            name        = name,
                            nationality = playerInfo["nationality"]?.toString(),
                            photoUrl    = playerInfo["photo"]?.toString(),
                            position    = (stats["games"] as? Map<*, *>)?.get("position")?.toString(),
                            age         = (playerInfo["age"] as? Number)?.toInt(),
                            team        = team
                        ))
                    } else {
                        // age 업데이트 (기존 선수도 나이 갱신)
                        val existing = playerRepository.findById(playerId).orElse(null)
                        val newAge = (playerInfo["age"] as? Number)?.toInt()
                        if (existing != null && newAge != null && existing.age != newAge) {
                            playerRepository.save(existing.copy(age = newAge))
                        }
                    }

                    // 항상 upsert: 시즌 초반 데이터가 있어도 최신 스탯으로 덮어쓰기
                    val games  = stats["games"]  as? Map<*, *>
                    val goals  = stats["goals"]  as? Map<*, *>
                    val cards  = stats["cards"]  as? Map<*, *>
                    val league = stats["league"] as? Map<*, *>
                    val newAppearances = (games?.get("appearences") as? Number)?.toInt() ?: 0

                    // findFirst: PL+CL 등 여러 대회 기록이 있어도 안전하게 1개 반환
                    val existing = playerSeasonStatRepository
                        .findFirstByPlayerIdAndSeasonAndTeamNameOrderByAppearancesDesc(playerId, season, team.name)

                    // 항상 최신 데이터로 upsert (초반에 잘못 들어온 데이터도 갱신)
                    playerSeasonStatRepository.save(PlayerSeasonStatEntity(
                        id            = existing?.id,
                        playerId      = playerId,
                        season        = season,
                        teamName      = team.name,
                        leagueName    = league?.get("name")?.toString() ?: "",
                        appearances   = newAppearances,
                        goals         = (goals?.get("total")   as? Number)?.toInt() ?: 0,
                        assists       = (goals?.get("assists")  as? Number)?.toInt() ?: 0,
                        minutesPlayed = (games?.get("minutes")  as? Number)?.toInt() ?: 0,
                        rating        = (games?.get("rating")   as? String)?.toDoubleOrNull(),
                        yellowCards   = (cards?.get("yellow")   as? Number)?.toInt() ?: 0,
                        redCards      = (cards?.get("red")      as? Number)?.toInt() ?: 0
                    ))
                }
                page++
            } catch (e: Exception) {
                log.error("팀 {} 시즌 {} 선수 적재 에러 (page {}): {}", teamId, season, page, e.message)
                break
            }
        } while (page <= totalPages)

        log.info("✅ 팀 {} 시즌 {} 선수 데이터 적재 완료", team.name, season)
    }
}

data class ApiFootballTeamResponse(val response: List<TeamWrapper>?)
data class TeamWrapper(val team: TeamDto)
data class TeamDto(val id: Int, val name: String, val country: String?, val logo: String?)