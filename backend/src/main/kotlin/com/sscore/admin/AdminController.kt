package com.sscore.admin

import com.sscore.service.BulkHistoricalInitService
import com.sscore.service.PremierLeagueHistorySeed
import com.sscore.service.SoccerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 외부 축구 API에서 데이터를 가져와 DB에 적재하는 관리 기능. ADMIN 권한 회원만 호출할 수 있다(보안 설정에서 /api/admin/** 보호).
// 상태를 바꾸는 기능은 모두 POST이다.
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val soccerService: SoccerService,
    private val bulkInitService: BulkHistoricalInitService,
    private val plHistorySeed: PremierLeagueHistorySeed
) {

    /** 시즌 범위의 경기 데이터를 외부 API에서 받아 적재 */
    @PostMapping("/matches/load")
    fun loadMatches(
        @RequestParam(defaultValue = "2025") start: Int,
        @RequestParam(defaultValue = "2025") end: Int
    ): String {
        soccerService.saveSeasonsRange(start, end)
        return "$start~$end 시즌 경기 데이터를 적재했습니다."
    }

    /** 선수 스탯(평점·출전시간) 일괄 수집 */
    @PostMapping("/player-stats/import")
    fun importPlayerStats(
        @RequestParam(defaultValue = "39") leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int,
        @RequestParam(defaultValue = "200") delayMs: Long
    ): String = soccerService.importPlayerStatsForLeague(season, leagueId, delayMs)

    /** 5대 리그 전체 경기 이벤트 일괄 수집 */
    @PostMapping("/match-events/import-all")
    fun importAllMatchEvents(
        @RequestParam(defaultValue = "2025") season: Int,
        @RequestParam(defaultValue = "400") delayMs: Long,
        @RequestParam(required = false) leagueId: Int?
    ): String = soccerService.importAllLeagueMatchEvents(season, delayMs, leagueId)

    /** 팀 전체 경기 이벤트 일괄 수집 */
    @PostMapping("/match-events/import")
    fun importMatchEvents(
        @RequestParam teamId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): String = soccerService.importMatchEventsForTeam(teamId, season)

    // ── 대량 적재 ────────────────────────────────────────────
    @PostMapping("/import/phase1")
    fun startPhase1(
        @RequestParam(defaultValue = "2010") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason: Int,
        @RequestParam(defaultValue = "350") delayMs: Long
    ): String = try {
        bulkInitService.startPhase1(fromSeason, toSeason, delayMs)
        "Phase 1 시작: $fromSeason~$toSeason 시즌 팀+경기 적재 중. GET /api/admin/import/status 로 확인하세요."
    } catch (e: IllegalStateException) {
        e.message ?: "이미 실행 중입니다."
    }

    @PostMapping("/import/phase2")
    fun startPhase2(
        @RequestParam(defaultValue = "2023") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason: Int,
        @RequestParam(defaultValue = "600") delayMs: Long
    ): String = try {
        bulkInitService.startPhase2(fromSeason, toSeason, delayMs)
        "Phase 2 시작: $fromSeason~$toSeason 시즌 선수 스탯 적재 중."
    } catch (e: IllegalStateException) {
        e.message ?: "이미 실행 중입니다."
    }

    @GetMapping("/import/status")
    fun importStatus(): Map<String, Any> = bulkInitService.getStatus()

    @PostMapping("/import/cancel")
    fun importCancel(): String {
        bulkInitService.cancel()
        return "취소를 요청했습니다."
    }

    // ── 우승팀 데이터 ─────────────────────────────────────────
    /** 각 시즌 리그 1위를 champions 테이블에 저장. Phase 1 완료 후 한 번만 실행하면 된다. */
    @PostMapping("/champions/sync")
    fun syncChampions(
        @RequestParam(defaultValue = "2010") fromSeason: Int,
        @RequestParam(defaultValue = "2025") toSeason: Int,
        @RequestParam(defaultValue = "350") delayMs: Long
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
        return "우승팀 적재를 시작했습니다: $fromSeason~$toSeason 시즌 (5리그)."
    }

    /** 프리미어리그 역대 우승 이력 시드 (서버 최초 세팅 시 한 번) */
    @PostMapping("/champions/seed-pl-history")
    fun seedPlHistory(): Map<String, Any> = plHistorySeed.loadAll()

    /** 외부 API에 데이터가 없는 시즌의 우승팀을 직접 입력 */
    @PostMapping("/champions/add")
    fun addChampion(
        @RequestParam leagueId: Int,
        @RequestParam season: Int,
        @RequestParam teamId: Int,
        @RequestParam teamName: String
    ): String {
        soccerService.addChampionManually(leagueId, season, teamId, teamName)
        return "저장했습니다: $teamName ($season 시즌, 리그 $leagueId)"
    }
}
