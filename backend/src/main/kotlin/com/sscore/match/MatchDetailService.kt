package com.sscore.match

import com.sscore.common.ApiException
import com.sscore.dto.H2HSummaryDto
import com.sscore.dto.LineupDto
import com.sscore.dto.MatchInfoResponseDto
import com.sscore.dto.MatchDto
import com.sscore.entity.MatchEntity
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.service.ApiFootballService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

// 끝난 경기의 확정본(JSON). info(타임라인·통계·평점)와 lineups가 준비되는 대로 채워진다.
@Entity
@Table(name = "match_details")
class MatchDetail(
    @Id
    @Column(name = "fixture_id")
    val fixtureId: Long,

    @Column(name = "info_json", columnDefinition = "MEDIUMTEXT")
    var infoJson: String? = null,

    @Column(name = "lineups_json", columnDefinition = "MEDIUMTEXT")
    var lineupsJson: String? = null,

    @Column(name = "finalized_at", nullable = false)
    var finalizedAt: LocalDateTime
)

@Repository
interface MatchDetailRepository : JpaRepository<MatchDetail, Long>

// 값을 일정 시간만 기억하는 간단한 메모리 캐시 (진행 중 경기용)
class TtlCache<V : Any>(private val clock: Clock, private val ttl: Duration) {
    private data class Entry<V>(val value: V, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<Any, Entry<V>>()

    fun get(key: Any): V? {
        val entry = entries[key] ?: return null
        if (clock.instant().isAfter(entry.expiresAt)) {
            entries.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(key: Any, value: V) {
        val now = clock.instant()
        if (entries.size > MAX_ENTRIES) entries.entries.removeIf { now.isAfter(it.value.expiresAt) }
        entries[key] = Entry(value, now.plus(ttl))
    }

    fun remove(key: Any) { entries.remove(key) }

    fun clear() = entries.clear()

    private companion object {
        const val MAX_ENTRIES = 500
    }
}

// 경기 상세(타임라인·통계·평점·라인업·상대전적) 조회.
// - 끝난 경기: 확정본을 DB에 한 번 저장해 두고 외부 API 없이 DB에서 준다 (첫 조회 때 또는 종료를 감지했을 때 저장)
// - 진행 중인 경기: 짧게(기본 30초) 메모리에 기억해, 많은 사용자가 봐도 외부 호출이 늘지 않게 한다
@Service
class MatchDetailService(
    private val matches: MatchRepository,
    private val teams: TeamRepository,
    private val details: MatchDetailRepository,
    private val playerStats: PlayerStatRepository,
    private val matchEvents: MatchEventRepository,
    private val apiService: ApiFootballService,
    private val json: JsonMapper,
    private val clock: Clock,
    private val tx: TransactionTemplate,
    @Value("\${sscore.match.cache-seconds:30}") cacheSeconds: Long
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val liveTtl = Duration.ofSeconds(cacheSeconds)
    private val infoCache = TtlCache<MatchInfoResponseDto>(clock, liveTtl)
    private val lineupCache = TtlCache<List<LineupDto>>(clock, liveTtl)
    private val h2hCache = TtlCache<H2HSummaryDto>(clock, Duration.ofMinutes(10))

    // 메모리 캐시를 모두 비운다 (테스트에서 시계를 되돌렸을 때처럼 캐시가 남아 있으면 안 되는 경우에 쓴다)
    fun clearCaches() {
        infoCache.clear(); lineupCache.clear(); h2hCache.clear()
    }

    // 우리 DB에 없는 경기로는 외부 API를 부르지 않는다
    private fun requireMatch(fixtureId: Long): MatchEntity = matches.findByFixtureId(fixtureId)
        ?: throw ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "경기를 찾을 수 없습니다.")

    private fun isFinished(match: MatchEntity) = match.matchTime.contains("종료")

    // ── 경기 정보 (타임라인·통계·평점) ────────────────────────────

    fun getInfo(fixtureId: Long): MatchInfoResponseDto {
        val match = requireMatch(fixtureId)
        details.findById(fixtureId).orElse(null)?.infoJson?.let { return json.readValue(it, MatchInfoResponseDto::class.java) }
        infoCache.get(fixtureId)?.let { return it }

        val raw = apiService.fetchFixtureDetails(fixtureId)
        val built = MatchInfoBuilder.build(fixtureId, match, raw) { playerStats.findByFixtureId(fixtureId) }
        built.referee?.takeIf { it != match.referee }?.let { matches.save(match.copy(referee = it)) }

        if (raw != null && (built.finished || isFinished(match))) {
            storeInfo(fixtureId, built)
        } else {
            infoCache.put(fixtureId, built.info)
        }
        return built.info
    }

    // 선수 기록·타임라인·확정본을 한 트랜잭션으로 저장한다
    private fun storeInfo(fixtureId: Long, built: BuiltMatchInfo) = tx.executeWithoutResult {
        if (built.playerStats.isNotEmpty()) {
            playerStats.deleteByFixtureId(fixtureId)
            playerStats.saveAll(built.playerStats)
        }
        if (built.events.isNotEmpty()) {
            matchEvents.deleteByFixtureId(fixtureId)
            matchEvents.saveAll(built.events)
        }
        val row = details.findById(fixtureId).orElseGet { MatchDetail(fixtureId, finalizedAt = LocalDateTime.now(clock)) }
        row.infoJson = json.writeValueAsString(built.info)
        row.finalizedAt = LocalDateTime.now(clock)
        details.save(row)
    }

    // 캐시를 건너뛰고 지금 상태를 다시 받는다 (득점 직후 알림을 위해). 받은 값은 캐시에 남아 화면 조회도 같은 값을 쓴다.
    fun refreshInfo(fixtureId: Long): MatchInfoResponseDto {
        infoCache.remove(fixtureId)
        return getInfo(fixtureId)
    }

    // ── 라인업 ──────────────────────────────────────────────────

    fun getLineups(fixtureId: Long): List<LineupDto> {
        val match = requireMatch(fixtureId)
        details.findById(fixtureId).orElse(null)?.lineupsJson?.let { return readLineups(it) }
        lineupCache.get(fixtureId)?.let { return it }

        val lineups = apiService.fetchLineups(fixtureId)
        // 끝난 경기의 라인업은 바뀌지 않으므로 저장한다. 라인업이 아직 없으면(빈 목록) 저장하지 않는다.
        if (isFinished(match) && lineups.isNotEmpty()) storeLineups(fixtureId, lineups) else lineupCache.put(fixtureId, lineups)
        return lineups
    }

    private fun storeLineups(fixtureId: Long, lineups: List<LineupDto>) = tx.executeWithoutResult {
        val row = details.findById(fixtureId).orElseGet { MatchDetail(fixtureId, finalizedAt = LocalDateTime.now(clock)) }
        row.lineupsJson = json.writeValueAsString(lineups)
        row.finalizedAt = LocalDateTime.now(clock)
        details.save(row)
    }

    private fun readLineups(text: String): List<LineupDto> =
        json.readValue(text, json.typeFactory.constructCollectionType(List::class.java, LineupDto::class.java))

    // ── 종료 확정 ───────────────────────────────────────────────

    // 경기가 끝난 것을 감지했을 때 부른다: 확정본이 없으면 외부에서 받아 저장한다. 실패해도 예외를 던지지 않는다
    // (사용자가 처음 조회할 때 다시 저장되므로 괜찮다).
    fun finalizeMatch(fixtureId: Long) {
        try {
            infoCache.clear(); lineupCache.clear()
            getInfo(fixtureId)
            getLineups(fixtureId)
        } catch (e: ApiException) {
            log.warn("경기 확정본 저장 실패 fixture={}, 다음 조회 때 다시 시도합니다: {}", fixtureId, e.message)
        }
    }

    // ── 상대전적 ────────────────────────────────────────────────

    fun getH2H(homeId: Int, awayId: Int): H2HSummaryDto {
        if (!teams.existsById(homeId) || !teams.existsById(awayId)) {
            throw ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "팀을 찾을 수 없습니다.")
        }
        h2hCache.get(homeId to awayId)?.let { return it }

        val last10: List<MatchDto> = apiService.fetchH2H(homeId, awayId)
            .filter { it.score != "0-0" || it.matchTime.contains("종료") }
            .sortedByDescending { it.matchDate }
            .take(10)

        var homeWins = 0
        var draws = 0
        var awayWins = 0
        last10.forEach { match ->
            val scores = match.score.split("-")
            if (scores.size == 2) {
                val hScore = scores[0].trim().toIntOrNull() ?: 0
                val aScore = scores[1].trim().toIntOrNull() ?: 0
                val requestedHomeIsMatchHome = match.homeTeamId == homeId
                val reqHomeScore = if (requestedHomeIsMatchHome) hScore else aScore
                val reqAwayScore = if (requestedHomeIsMatchHome) aScore else hScore
                when {
                    reqHomeScore > reqAwayScore -> homeWins++
                    reqHomeScore < reqAwayScore -> awayWins++
                    else -> draws++
                }
            }
        }
        return H2HSummaryDto(homeWins, draws, awayWins, last10).also { h2hCache.put(homeId to awayId, it) }
    }
}
