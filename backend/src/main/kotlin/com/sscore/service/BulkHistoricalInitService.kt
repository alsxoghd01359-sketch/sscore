package com.sscore.service

import com.sscore.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 2010 ~ 현재까지 빅5 리그 경기 데이터를 백그라운드 스레드로 대량 적재하는 서비스.
 *
 * Phase 1 — 팀 + 경기 일정  (리그당 2 API 콜 × 시즌 수 × 5리그)
 * Phase 2 — 선수 스탯        (팀당 1~N 콜 × 시즌 수 × 팀 수) ← 콜 수가 많으므로 별도 트리거
 *
 * Rate-limit 가이드:
 *   Free  (100 콜/일)  → delayMs = 5000 이상 권장, 시즌 1~2개씩 나눠서 실행
 *   Pro (3000 콜/일)   → delayMs = 300 이상이면 Phase1(16시즌) 1분 내 완료
 */
@Service
class BulkHistoricalInitService(
    private val apiService: ApiFootballService,
    private val teamRepository: TeamRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        val LEAGUE_IDS   = listOf(39, 140, 78, 135, 61)
        val LEAGUE_NAMES = mapOf(
            39  to "프리미어리그",
            140 to "라리가",
            78  to "분데스리가",
            135 to "세리에 A",
            61  to "리그 1"
        )
    }

    // ─── 진행 상황 (volatile → 스레드 간 가시성 보장) ───────────────────
    @Volatile var isRunning     = false
    @Volatile var isCancelled   = false
    @Volatile var currentStatus = "대기 중"
    @Volatile var progress      = 0
    @Volatile var total         = 0
    @Volatile var successCount  = 0
    @Volatile var errorCount    = 0
    private  var startTime      = 0L

    // ─────────────────────────────────────────────────────────────────────
    // Phase 1 : 팀 + 경기 일정
    // ─────────────────────────────────────────────────────────────────────
    fun startPhase1(fromSeason: Int, toSeason: Int, delayMs: Long = 350) {
        checkNotRunning()
        resetCounters()

        val seasons = (fromSeason..toSeason).toList()
        total = seasons.size * LEAGUE_IDS.size * 2   // 팀 sync + 경기 sync

        launchBackground("Phase1-TeamFixtures") {
            for (season in seasons) {
                if (isCancelled) break

                for (leagueId in LEAGUE_IDS) {
                    if (isCancelled) break
                    val leagueName = LEAGUE_NAMES[leagueId]

                    // 1-A. 팀 동기화
                    currentStatus = "팀 동기화 중: $leagueName $season 시즌 (${progress + 1}/$total)"
                    runCatching { apiService.syncTeams(leagueId, season) }
                        .onSuccess { successCount++ }
                        .onFailure { errorCount++; log.error("팀 sync 실패 league={} season={}", leagueId, season) }
                    progress++
                    Thread.sleep(delayMs)

                    if (isCancelled) break

                    // 1-B. 경기 일정 동기화
                    currentStatus = "경기 동기화 중: $leagueName $season 시즌 (${progress + 1}/$total)"
                    runCatching { apiService.syncFixtures(leagueId, season) }
                        .onSuccess { successCount++ }
                        .onFailure { errorCount++; log.error("경기 sync 실패 league={} season={}", leagueId, season) }
                    progress++
                    Thread.sleep(delayMs)
                }
            }
            currentStatus = if (isCancelled)
                "⛔ 취소됨 (진행: $progress/$total, 성공: $successCount, 에러: $errorCount)"
            else
                "✅ Phase 1 완료! 팀+경기 적재 완료 (성공: $successCount, 에러: $errorCount)"

            log.info("[BulkImport] Phase1 종료 — {}", currentStatus)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase 2 : 선수 스탯  (콜 수가 많으므로 별도 실행)
    // ─────────────────────────────────────────────────────────────────────
    fun startPhase2(fromSeason: Int, toSeason: Int, delayMs: Long = 600) {
        checkNotRunning()
        resetCounters()

        val teams   = teamRepository.findAll()
        val seasons = (fromSeason..toSeason).toList()
        total = teams.size * seasons.size

        log.info("[BulkImport] Phase2 시작 — 팀 {}개 × {}시즌 = 최대 {} 콜 예상",
            teams.size, seasons.size, total)

        launchBackground("Phase2-PlayerStats") {
            for (season in seasons) {
                if (isCancelled) break

                for (team in teams) {
                    if (isCancelled) break

                    currentStatus = "${team.name} 선수 동기화 중 ($season 시즌) (${progress + 1}/$total)"
                    runCatching { apiService.syncPlayers(team.teamId, season) }
                        .onSuccess { successCount++ }
                        .onFailure { errorCount++ }
                    progress++
                    Thread.sleep(delayMs)
                }
            }
            currentStatus = if (isCancelled)
                "⛔ 취소됨 (진행: $progress/$total, 성공: $successCount, 에러: $errorCount)"
            else
                "✅ Phase 2 완료! 선수 스탯 적재 완료 (성공: $successCount, 에러: $errorCount)"

            log.info("[BulkImport] Phase2 종료 — {}", currentStatus)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 진행 상황 조회
    // ─────────────────────────────────────────────────────────────────────
    fun getStatus(): Map<String, Any> {
        val elapsedSec = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000 else 0L
        val etaSec = if (progress > 0 && isRunning && total > progress) {
            val ratePerSec = progress.toDouble() / elapsedSec.coerceAtLeast(1)
            ((total - progress) / ratePerSec).toLong()
        } else 0L

        return mapOf(
            "isRunning"                to isRunning,
            "status"                   to currentStatus,
            "progress"                 to progress,
            "total"                    to total,
            "percentage"               to if (total > 0) progress * 100 / total else 0,
            "successCount"             to successCount,
            "errorCount"               to errorCount,
            "elapsedSeconds"           to elapsedSec,
            "estimatedRemainingSeconds" to etaSec
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 취소
    // ─────────────────────────────────────────────────────────────────────
    fun cancel() {
        if (!isRunning) return
        isCancelled   = true
        currentStatus = "⛔ 취소 요청됨..."
        log.info("[BulkImport] 취소 요청 수신")
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────
    private fun checkNotRunning() {
        if (isRunning) throw IllegalStateException("이미 적재 작업이 실행 중입니다. /admin/import/status 로 확인하세요.")
    }

    private fun resetCounters() {
        isRunning     = true
        isCancelled   = false
        progress      = 0
        successCount  = 0
        errorCount    = 0
        startTime     = System.currentTimeMillis()
    }

    private fun launchBackground(name: String, block: () -> Unit) {
        Thread({
            try { block() }
            catch (e: InterruptedException) { currentStatus = "⛔ 스레드 인터럽트됨" }
            catch (e: Exception) { log.error("[BulkImport] 예상치 못한 에러", e); currentStatus = "❌ 에러: ${e.message}" }
            finally { isRunning = false }
        }, "bulk-import-$name").apply { isDaemon = true; start() }
    }
}