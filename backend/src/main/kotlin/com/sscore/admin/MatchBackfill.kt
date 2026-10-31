package com.sscore.admin

import com.sscore.common.ApiException
import com.sscore.league.LeagueCatalog
import com.sscore.match.MatchDetailService
import com.sscore.repository.MatchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

// 지난 시즌 경기의 상세를 한꺼번에 채우는 작업 상태
data class BackfillStatus(
    val running: Boolean = false,
    val season: Int? = null,
    val calls: Int = 0,
    val stored: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val stoppedBecause: String? = null
)

// 끝난 지난 경기의 확정본을 외부 API에서 받아 DB에 채운다. 하루 호출 한도를 넘기지 않도록 한 번에 쓸 호출 수(maxCalls)를 정한다.
// - detailFrom 시즌 이후: 타임라인·선수 기록·평점·라인업 전부
// - 그 이전 시즌: 라인업만
// 이미 저장된 경기는 건너뛰므로 멈췄다가 다시 시작해도 이어서 진행된다. 최근 시즌부터 채운다.
@Service
class MatchBackfillService(
    private val matches: MatchRepository,
    private val details: MatchDetailService,
    private val catalog: LeagueCatalog,
    @Value("\${sscore.backfill.async:true}") private val async: Boolean
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val MAX_CONSECUTIVE_FAILURES = 5
    }

    @Volatile private var status = BackfillStatus()
    @Volatile private var cancelRequested = false

    fun status(): BackfillStatus = status

    fun cancel() { cancelRequested = true }

    // 시작할 수 있으면 true. 이미 돌고 있으면 false.
    @Synchronized
    fun start(from: Int, to: Int, detailFrom: Int, maxCalls: Int, delayMs: Long): Boolean {
        if (status.running) return false
        cancelRequested = false
        status = BackfillStatus(running = true)
        if (async) {
            Thread({ run(from, to, detailFrom, maxCalls, delayMs) }, "match-backfill").apply { isDaemon = true; start() }
        } else {
            run(from, to, detailFrom, maxCalls, delayMs)
        }
        return true
    }

    private fun run(from: Int, to: Int, detailFrom: Int, maxCalls: Int, delayMs: Long) {
        var calls = 0; var stored = 0; var skipped = 0; var failed = 0; var consecutiveFailures = 0
        var stopped: String? = null
        log.info("[Backfill] 시작 — 시즌 {}~{} (상세는 {} 시즌부터) 호출 한도 {}", from, to, detailFrom, maxCalls)

        seasons@ for (season in to downTo from) {
            val todo = catalog.ids().flatMap { matches.findByLeagueIdAndSeason(it, season) }
                .filter { it.matchTime.contains("종료") }.mapNotNull { it.fixtureId }
            status = status.copy(season = season)
            for (fixtureId in todo) {
                if (cancelRequested) { stopped = "취소됨"; break@seasons }
                if (calls >= maxCalls) { stopped = "호출 한도(${maxCalls}회)에 도달"; break@seasons }
                try {
                    val used = if (season >= detailFrom) details.backfillFull(fixtureId) else details.backfillLineups(fixtureId)
                    calls += used
                    if (used == 0) skipped++ else stored++
                    consecutiveFailures = 0
                    if (used > 0) {
                        if (stored % 100 == 0) log.info("[Backfill] 시즌 {} — 저장 {} 건너뜀 {} 실패 {} 호출 {}", season, stored, skipped, failed, calls)
                        Thread.sleep(delayMs)
                    }
                } catch (e: ApiException) {
                    failed++; calls++; consecutiveFailures++
                    log.warn("[Backfill] fixture={} 실패: {}", fixtureId, e.message)
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) { stopped = "연속 실패 ${MAX_CONSECUTIVE_FAILURES}회 (API 한도나 연결을 확인하세요)"; break@seasons }
                }
                status = status.copy(calls = calls, stored = stored, skipped = skipped, failed = failed)
            }
        }
        status = BackfillStatus(false, null, calls, stored, skipped, failed, stopped ?: "모두 처리함")
        log.info("[Backfill] 끝 — 저장:{} 건너뜀:{} 실패:{} 호출:{} ({})", stored, skipped, failed, calls, status.stoppedBecause)
    }
}
