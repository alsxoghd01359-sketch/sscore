package com.sscore.controller

import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.dto.*
import com.sscore.service.BulkHistoricalInitService
import com.sscore.service.PremierLeagueHistorySeed
import com.sscore.service.SoccerService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/soccer")
class SoccerController(
    private val soccerService: SoccerService,
    private val bulkInitService: BulkHistoricalInitService,
    private val plHistorySeed: PremierLeagueHistorySeed,
    @org.springframework.beans.factory.annotation.Value("\${API_FOOTBALL_KEY}") private val apiKey: String
) {

    // 1. 전체 경기 데이터 DB에서 가져오기
    @GetMapping("/matches")
    fun getMatches(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) leagueId: Int?
    ): List<MatchDto> {
        return if (date != null) {
            soccerService.getMatchesByDate(date, leagueId)
        } else {
            soccerService.getAllMatchesFromDb()
        }
    }

    // 2. 특정 범위 시즌 데이터 수집 (기존 엔드포인트)
    @GetMapping("/init-range")
    fun initRange(@RequestParam start: Int, @RequestParam end: Int): String {
        soccerService.saveSeasonsRange(start, end)
        return "Season $start to $end 데이터 저장 완료!"
    }

    // 🌟 [새로 추가된 치트키] 브라우저 주소창으로 파라미터 없이 쉽게 강제 적재하는 엔드포인트
    // 기본적으로 2025 시즌(25/26 리그 경기들) 데이터를 긁어옵니다.
    @GetMapping("/force-load")
    fun forceLoadMatches(
        @RequestParam(defaultValue = "2025") start: Int,
        @RequestParam(defaultValue = "2025") end: Int
    ): String {
        return try {
            soccerService.saveSeasonsRange(start, end)
            "🟢 [성공] 외부 축구 API로부터 $start ~ $end 시즌 경기 데이터 수집 및 DB 적재를 완료했습니다! MySQL에서 확인해 보세요."
        } catch (e: Exception) {
            "❌ [실패] 에러 발생 원인: ${e.message}"
        }
    }

    // 3. 상세 페이지용 API
    // 🌟 안드로이드 ApiService 규격과 일치하도록 List<LineupDto> 반환 타입 강제 지정
    @GetMapping("/lineups/{fixtureId}")
    fun getLineups(@PathVariable fixtureId: Long): List<LineupDto> {
        return soccerService.getLineups(fixtureId)
    }

    @GetMapping("/player/{playerId}/career")
    fun getPlayerCareer(@PathVariable playerId: Long): List<PlayerSeasonStatEntity> {
        return soccerService.getPlayerCareerHistory(playerId)
    }

    @GetMapping("/standings")
    fun getStandings(@RequestParam leagueId: Int, @RequestParam season: Int) =
        soccerService.getStandings(leagueId, season)

    @GetMapping("/h2h")
    fun getH2H(@RequestParam homeId: Int, @RequestParam awayId: Int) =
        soccerService.getH2H(homeId, awayId)

    @GetMapping("/matches/{fixtureId}/info")
    fun getMatchInfo(@PathVariable fixtureId: Long): MatchInfoResponseDto {
        return soccerService.getMatchInfo(fixtureId)
    }

    /** 디버그: 특정 팀+시즌 player stat DB 내용 확인 */
    @GetMapping("/admin/debug/player-stats")
    fun debugPlayerStats(
        @RequestParam teamId: Int,
        @RequestParam season: Int
    ): Map<String, Any> {
        val teamName = soccerService.getTeamNameById(teamId)
        val stats = soccerService.debugPlayerStats(teamId, season)
        val playerList: List<Map<String, Any>> = stats.map {
            mapOf<String, Any>(
                "playerId"    to it.playerId,
                "playerName"  to it.playerName,
                "appearances" to it.appearances,
                "teamName"    to it.teamName
            )
        }
        return mapOf<String, Any>(
            "teamId"    to teamId,
            "teamName"  to (teamName ?: "NOT FOUND"),
            "season"    to season,
            "count"     to stats.size,
            "players"   to playerList
        )
    }

    /** 선수 스탯(평점·출전시간) 일괄 수집 */
    @GetMapping("/admin/import-player-stats")
    fun importPlayerStats(
        @RequestParam(defaultValue = "39")   leagueId: Int,
        @RequestParam(defaultValue = "2025") season:   Int,
        @RequestParam(defaultValue = "200")  delayMs:  Long
    ): String = soccerService.importPlayerStatsForLeague(season, leagueId, delayMs)

    /** 5대 리그 전체 경기 이벤트 일괄 수집 */
    @GetMapping("/admin/import-match-events/all")
    fun importAllMatchEvents(
        @RequestParam(defaultValue = "2025") season: Int,
        @RequestParam(defaultValue = "400")  delayMs: Long,
        @RequestParam(required = false)      leagueId: Int?
    ): String = soccerService.importAllLeagueMatchEvents(season, delayMs, leagueId)

    /** 팀 전체 경기 이벤트 일괄 임포트 (경기 탭 데이터 준비용) */
    @GetMapping("/admin/import-match-events")
    fun importMatchEvents(
        @RequestParam teamId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): String = soccerService.importMatchEventsForTeam(teamId, season)

    /** 통합 검색 (선수·팀·리그) */
    @GetMapping("/search")
    fun search(@RequestParam q: String): List<SearchResultDto> =
        soccerService.search(q)

    /** 리그 기본 정보 */
    @GetMapping("/league/{leagueId}")
    fun getLeagueInfo(@PathVariable leagueId: Int): LeagueInfoDto? =
        soccerService.getLeagueInfo(leagueId)

    /** 리그 플레이어 통계 (득점/도움/공격P/평점) */
    @GetMapping("/league/{leagueId}/player-stats")
    fun getLeaguePlayerStats(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): LeaguePlayerStatsDto = soccerService.getLeaguePlayerStats(leagueId, season)

    /** 리그 팀 통계 (평균평점/득점/실점/클린시트) */
    @GetMapping("/league/{leagueId}/team-stats")
    fun getLeagueTeamStats(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): LeagueTeamStatsDto = soccerService.getLeagueTeamStats(leagueId, season)

    /** 리그 경기 일정/결과 (날짜별 그룹) */
    @GetMapping("/league/{leagueId}/matches")
    fun getLeagueMatches(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): List<LeagueMatchDayDto> = soccerService.getLeagueMatches(leagueId, season)

    /** 선수 경기 기록 */
    @GetMapping("/player/{playerId}/matches")
    fun getPlayerMatches(@PathVariable playerId: Long): List<PlayerMatchRecordDto> =
        soccerService.getPlayerMatches(playerId)

    /** 선수 시즌별 통계 목록 */
    @GetMapping("/player/{playerId}/season-stats")
    fun getPlayerSeasonStatsList(@PathVariable playerId: Long): List<PlayerSeasonStatsDetailDto> =
        soccerService.getPlayerSeasonStatsList(playerId)

    /** 선수 세부 페이지 (프로필+트로피+이번시즌) */
    @GetMapping("/player/{playerId}/detail")
    fun getPlayerDetail(@PathVariable playerId: Long): PlayerDetailDto? =
        soccerService.getPlayerDetail(playerId)

    /** 선수 프로필 + 커리어 스탯 */
    @GetMapping("/player/{playerId}/profile")
    fun getPlayerProfile(@PathVariable playerId: Long): PlayerProfileDto? =
        soccerService.getPlayerProfile(playerId)

    /** 구단 트로피 전체 목록 (DB champions 테이블) */
    @GetMapping("/team/{teamId}/trophies")
    fun getTeamTrophies(@PathVariable teamId: Int): List<TrophyDto> =
        soccerService.getTeamTrophies(teamId)

    /** 구단 현재 스쿼드 */
    @GetMapping("/team/{teamId}/squad")
    fun getTeamSquad(@PathVariable teamId: Int): SquadDto =
        soccerService.getTeamSquad(teamId)

    /** 선수 경기 스탯 */
    @GetMapping("/player/{playerId}/match-stats")
    fun getPlayerMatchStats(
        @PathVariable playerId: Long,
        @RequestParam fixtureId: Long
    ): PlayerMatchStatDto? = soccerService.getPlayerMatchStats(playerId, fixtureId)

    /** 구단 통계: 홈/원정 성적 + 평점/득점/도움 랭킹 */
    @GetMapping("/team/{teamId}/stats")
    fun getTeamStats(
        @PathVariable teamId:  Int,
        @RequestParam leagueId: Int,
        @RequestParam season:   Int
    ): TeamStatsDto = soccerService.getTeamStats(teamId, leagueId, season)

    /** 구단 경기 목록 (페이지네이션, 최신순) */
    @GetMapping("/team/{teamId}/matches")
    fun getTeamMatches(
        @PathVariable teamId: Int,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "15") size: Int
    ): TeamMatchesPageDto = soccerService.getTeamMatches(teamId, page, size)

    /** 구단 개요: 최근 5경기 + 미니순위 + 경기장 + 리그트로피 */
    @GetMapping("/team/{teamId}/overview")
    fun getTeamOverview(
        @PathVariable teamId: Int,
        @RequestParam leagueId: Int,
        @RequestParam season: Int
    ): TeamOverviewDto = soccerService.getTeamOverview(teamId, leagueId, season)

    // ── 어드민: 대량 적재 ────────────────────────────────────
    @GetMapping("/admin/import/phase1")
    fun startPhase1(
        @RequestParam(defaultValue = "2010") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason: Int,
        @RequestParam(defaultValue = "350")  delayMs: Long
    ): String = try {
        bulkInitService.startPhase1(fromSeason, toSeason, delayMs)
        "🚀 Phase 1 시작! $fromSeason~$toSeason 시즌 팀+경기 적재 중... /api/soccer/admin/import/status 확인하세요."
    } catch (e: IllegalStateException) { "⚠️ ${e.message}" }

    @GetMapping("/admin/import/phase2")
    fun startPhase2(
        @RequestParam(defaultValue = "2023") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason: Int,
        @RequestParam(defaultValue = "600")  delayMs: Long
    ): String = try {
        bulkInitService.startPhase2(fromSeason, toSeason, delayMs)
        "🚀 Phase 2 시작! $fromSeason~$toSeason 시즌 선수 스탯 적재 중..."
    } catch (e: IllegalStateException) { "⚠️ ${e.message}" }

    @GetMapping("/admin/import/status")
    fun importStatus(): Map<String, Any> = bulkInitService.getStatus()

    @GetMapping("/admin/import/cancel")
    fun importCancel(): String { bulkInitService.cancel(); return "⛔ 취소 요청 완료" }

    // ── 우승팀 데이터 일괄 적재 ───────────────────────────────
    /**
     * 각 시즌 리그 1위를 champions 테이블에 저장.
     * Phase 1 완료 후 한 번만 실행하면 됨.
     * 예: /api/soccer/admin/sync-champions?fromSeason=2010&toSeason=2025&delayMs=350
     */
    @GetMapping("/admin/sync-champions")
    fun syncChampions(
        @RequestParam(defaultValue = "2010") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason:   Int,
        @RequestParam(defaultValue = "350")  delayMs:    Long
    ): String {
        Thread({
            val leagues = listOf(39, 140, 78, 135, 61)
            for (season in fromSeason..toSeason) {
                for (leagueId in leagues) {
                    runCatching { soccerService.syncChampions(leagueId, season) }
                    Thread.sleep(delayMs)
                }
            }
        }, "sync-champions").apply { isDaemon = true; start() }
        return "🏆 우승팀 적재 시작! $fromSeason~$toSeason 시즌 (5리그). /admin/import/status 로 확인하세요."
    }

    // ── PL 역대 우승 이력 시드 (한 번만 실행) ────────────────
    /**
     * 25/26 PL 20개팀 잉글랜드 1부 우승 이력 전체 삽입.
     * 서버 최초 세팅 시 한 번만 실행하면 됨.
     * http://localhost:8080/api/soccer/admin/seed-pl-history
     */
    @GetMapping("/admin/seed-pl-history")
    fun seedPlHistory(): Map<String, Any> = plHistorySeed.loadAll()

    // ── 우승팀 수동 입력 (빈 시즌 보완용) ──────────────────────
    /**
     * api-football 데이터 없는 시즌 수동으로 직접 입력.
     * 예: /api/soccer/admin/add-champion?leagueId=39&season=1993&teamId=33&teamName=Manchester United
     */
    @GetMapping("/admin/add-champion")
    fun addChampion(
        @RequestParam leagueId:  Int,
        @RequestParam season:    Int,
        @RequestParam teamId:    Int,
        @RequestParam teamName:  String
    ): String {
        return try {
            soccerService.addChampionManually(leagueId, season, teamId, teamName)
            "✅ 저장 완료: $teamName ($season 시즌, 리그 $leagueId)"
        } catch (e: Exception) {
            "⚠️ 실패: ${e.message}"
        }
    }

    // ── 트로피 API 원시 응답 확인용 (디버그 전용) ──────────────
    @GetMapping("/admin/debug/trophies/{teamId}")
    fun debugTrophies(@PathVariable teamId: Int): Map<String, Any?> {
        val url = "https://v3.football.api-sports.io/trophies?team=$teamId"
        val headers = org.springframework.http.HttpHeaders().apply {
            set("x-apisports-key", apiKey)
        }
        val entity = org.springframework.http.HttpEntity<String>(headers)
        return try {
            val raw = org.springframework.web.client.RestTemplate()
                .exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body

            val items = raw?.get("response") as? List<*>
            mapOf(
                "총_항목수"     to (items?.size ?: 0),
                "에러"          to raw?.get("errors"),
                "place_값_목록" to items?.filterIsInstance<Map<*, *>>()
                    ?.mapNotNull { it["place"]?.toString() }
                    ?.distinct(),
                "전체_원시응답" to raw
            )
        } catch (e: Exception) {
            mapOf("에러" to e.message)
        }
    }
}