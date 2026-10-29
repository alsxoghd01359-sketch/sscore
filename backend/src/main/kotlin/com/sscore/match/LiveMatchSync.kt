package com.sscore.match

import com.sscore.common.ApiException
import com.sscore.dto.MatchDto
import com.sscore.entity.MatchEntity
import com.sscore.football.FootballApiClient
import com.sscore.league.LeagueCatalog
import com.sscore.notification.MatchNotifier
import com.sscore.repository.MatchRepository
import com.sscore.service.ApiFootballService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class LiveSyncResult(
    val candidates: Int = 0,      // 지금 진행 중일 수 있는 경기 수 (DB 기준)
    val liveFixtures: Int = 0,    // 외부 API가 진행 중이라고 알려 준 경기 수
    val updated: Int = 0,         // 점수·경기 시간이 바뀌어 DB에 반영한 경기 수
    val apiCalls: Int = 0,
    val remainingCalls: Int? = null,   // 오늘 남은 외부 API 호출 수 (모르면 null)
    val failed: Boolean = false,
    val finished: Int = 0         // 이번 주기에 끝난 것으로 확인되어 확정본을 저장한 경기 수
)

// 라이브 갱신 한 번에서 바뀐 경기 하나 (바뀌기 전, 바뀐 뒤)
private data class MatchChange(val before: MatchEntity, val after: MatchEntity) {
    val ended get() = after.matchTime.contains("종료") && !before.matchTime.contains("종료")
    val scoreChanged get() = before.score != after.score

    // 이제 막 시작한 경기. 서버가 경기 도중에 처음 확인한 경우(분이 많이 지난 경우)는 시작 알림을 보내지 않는다.
    val started get() = !before.matchTime.contains("진행중") && !before.matchTime.contains("종료") &&
        after.matchTime.contains("진행중") && (minuteOf(after.matchTime) ?: 0) <= 15

    private fun minuteOf(label: String) = Regex("""진행중 (\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull()
}

// 진행 중인 경기의 점수·경기 시간을 외부 API에서 받아 DB에 반영한다.
// 외부 API는 "지금 진행 중일 수 있는 경기"가 DB에 있을 때만 부른다(하루 호출 한도를 지키기 위해).
@Service
class LiveMatchSync(
    private val matches: MatchRepository,
    private val apiService: ApiFootballService,
    private val football: FootballApiClient,
    private val catalog: LeagueCatalog,
    private val details: MatchDetailService,
    private val notifier: MatchNotifier,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        val BEFORE_KICKOFF: Duration = Duration.ofMinutes(5)
        val AFTER_KICKOFF: Duration = Duration.ofHours(3).plusMinutes(30)   // 연장·승부차기·지연까지
        val STALE_AFTER: Duration = Duration.ofMinutes(10)                  // 킥오프가 한참 지났는데 아직 시작 표시인 경기
        val STALE_CHECK_EVERY: Duration = Duration.ofMinutes(5)
        const val IDS_PER_CALL = 20                                         // 외부 API가 한 번에 받는 경기 ID 수
        val ENDED_MARKERS = listOf("(종료)", "(취소)", "(PST)", "(CANC)", "(ABD)", "(AWD)", "(WO)")
        val TIME = Regex("""^(\d{2}):(\d{2})""")
    }

    private var lastStaleCheck: LocalDateTime? = null
    private var cycles = 0

    fun sync(): LiveSyncResult {
        val now = LocalDateTime.now(clock)
        val candidates = candidates(now)
        if (candidates.isEmpty()) return LiveSyncResult()

        var calls = 0
        var failed = false

        // 1. 진행 중인 경기 (한 번의 호출로 모든 지원 리그)
        val live = try {
            calls++
            apiService.fetchLiveMatches(catalog.ids())
        } catch (e: ApiException) {
            log.warn("라이브 경기 조회 실패, 다음 주기에 다시 시도합니다: {}", e.message)
            failed = true
            emptyList()
        }
        val changes = mutableListOf<MatchChange>()
        var updated = apply(live, changes)

        // 2. 라이브 목록에서 사라진 경기는 끝났을 수 있으므로 최종 결과를 확인한다
        if (!failed) {
            val liveIds = live.mapNotNull { it.fixtureId }.toSet()
            val toCheck = idsToCheck(candidates, liveIds, now)
            for (chunk in toCheck.chunked(IDS_PER_CALL)) {
                try {
                    calls++
                    updated += apply(apiService.fetchFixturesByIds(chunk), changes)
                } catch (e: ApiException) {
                    log.warn("경기 결과 확인 실패, 다음 주기에 다시 시도합니다: {}", e.message)
                    failed = true
                }
            }
        }

        // 방금 끝난 경기는 타임라인·통계·평점·라인업의 확정본을 저장해 둔다 (이후 조회는 외부 호출 없이 DB에서 준다)
        val endedNow = changes.filter { it.ended }
        endedNow.forEach { it.after.fixtureId?.let { id -> details.finalizeMatch(id) } }
        calls += endedNow.size * 2

        // 시작·득점·종료를 팔로우한 회원에게 알린다. 알림에서 오류가 나도 갱신은 계속된다.
        changes.forEach { c ->
            try {
                if (c.started) notifier.matchStarted(c.after)
                if (c.scoreChanged) notifier.scoreChanged(c.after)
                if (c.ended) notifier.matchEnded(c.after)
            } catch (e: Exception) {
                log.error("경기 알림 처리 중 오류 fixture={}", c.after.fixtureId, e)
            }
        }

        val result = LiveSyncResult(candidates.size, live.size, updated, calls, football.lastRemaining, failed, endedNow.size)
        report(result)
        return result
    }

    // 점수/시간이 바뀌었으면 그때만 로그를 남기고, 평소에는 가끔(약 5분마다) 한 줄로 현황을 남긴다
    private fun report(r: LiveSyncResult) {
        cycles++
        if (r.updated > 0 || cycles % 20 == 0) {
            log.info("라이브 갱신: 후보 {}경기, 진행 중 {}경기, 갱신 {}건, 호출 {}회, 오늘 남은 호출 {}", r.candidates, r.liveFixtures, r.updated, r.apiCalls, r.remainingCalls ?: "알 수 없음")
        } else {
            log.debug("라이브 갱신: 후보 {}경기, 진행 중 {}경기, 호출 {}회", r.candidates, r.liveFixtures, r.apiCalls)
        }
    }

    // 지금 진행 중일 수 있는 경기: 지원 리그의 어제·오늘 경기 중 끝나지 않았고 킥오프 시간이 가까운(또는 지난) 경기
    private fun candidates(now: LocalDateTime): List<MatchEntity> {
        val dates = listOf(now.toLocalDate().minusDays(1), now.toLocalDate()).map { it.toString() }
        return matches.findByMatchDateInAndLeagueIdIn(dates, catalog.ids()).filter { m ->
            val kickoff = kickoffOf(m) ?: return@filter false
            !hasEnded(m) && now >= kickoff.minus(BEFORE_KICKOFF) && now <= kickoff.plus(AFTER_KICKOFF)
        }
    }

    private fun hasEnded(m: MatchEntity) = ENDED_MARKERS.any { m.matchTime.contains(it) }

    // matchTime 은 "HH:mm" 또는 "HH:mm (상태)" 형태이다 (한국 시간)
    private fun kickoffOf(m: MatchEntity): LocalDateTime? {
        val match = TIME.find(m.matchTime) ?: return null
        val date = runCatching { LocalDate.parse(m.matchDate) }.getOrNull() ?: return null
        return LocalDateTime.of(date, LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
    }

    // 확인이 필요한 경기: 진행 중이었는데 라이브 목록에서 사라진 경기(방금 끝남), 그리고 킥오프가 한참 지났는데도
    // 시작 전 표시인 경기(연기 등)는 5분에 한 번만 확인한다.
    private fun idsToCheck(candidates: List<MatchEntity>, liveIds: Set<Long>, now: LocalDateTime): List<Long> {
        val missing = candidates.filter { it.fixtureId != null && it.fixtureId !in liveIds }
        val justEnded = missing.filter { it.matchTime.contains("진행중") }.mapNotNull { it.fixtureId }

        val last = lastStaleCheck
        val staleDue = last == null || now < last || Duration.between(last, now) >= STALE_CHECK_EVERY
        val stale = if (staleDue) {
            missing.filter { !it.matchTime.contains("진행중") && now >= (kickoffOf(it) ?: now).plus(STALE_AFTER) }
                .mapNotNull { it.fixtureId }
        } else emptyList()
        if (staleDue) lastStaleCheck = now

        return (justEnded + stale).distinct()
    }

    // 바뀐 경기만 저장하고, 바뀐 내용(시작·득점·종료)을 changes 에 모은다.
    private fun apply(fresh: List<MatchDto>, changes: MutableList<MatchChange>): Int {
        val ids = fresh.mapNotNull { it.fixtureId }
        if (ids.isEmpty()) return 0
        val existing = matches.findByFixtureIdIn(ids).associateBy { it.fixtureId }
        val changed = fresh.mapNotNull { dto ->
            val current = existing[dto.fixtureId] ?: return@mapNotNull null
            if (current.score == dto.score && current.matchTime == dto.matchTime) return@mapNotNull null
            val after = current.copy(score = dto.score, matchTime = dto.matchTime)
            changes += MatchChange(current, after)
            after
        }
        if (changed.isNotEmpty()) matches.saveAll(changed)
        return changed.size
    }
}

// 15초마다 라이브 갱신을 돌린다. 어떤 오류가 나도 서버가 멈추지 않고 다음 주기에 다시 시도한다.
@Component
@ConditionalOnProperty(name = ["sscore.live.enabled"], havingValue = "true", matchIfMissing = true)
class LiveMatchScheduler(private val sync: LiveMatchSync, private val notifier: MatchNotifier) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelayString = "\${sscore.live.interval-ms:15000}", initialDelayString = "\${sscore.live.interval-ms:15000}")
    fun tick() {
        try {
            sync.sync()
        } catch (e: Exception) {
            log.error("라이브 갱신 중 예상하지 못한 오류", e)
        }
        try {
            notifier.checkLineups()
        } catch (e: Exception) {
            log.error("라인업 알림 확인 중 예상하지 못한 오류", e)
        }
    }
}
