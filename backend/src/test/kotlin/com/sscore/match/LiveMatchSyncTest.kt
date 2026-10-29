package com.sscore.match

import com.sscore.entity.MatchEntity
import com.sscore.repository.MatchRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.get
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 테스트 시계의 시작은 2026-01-01 09:00 (한국 시간)이다.
class LiveMatchSyncTest : ApiTest() {

    @Autowired private lateinit var sync: LiveMatchSync
    @Autowired private lateinit var matches: MatchRepository

    private val base = "https://v3.football.api-sports.io"
    private val liveUrl = "$base/fixtures?live=39-140-78-135-61"
    private val today = "2026-01-01"

    @BeforeEach
    fun clean() {
        matches.deleteAll()
    }

    private fun match(fixtureId: Long, time: String, leagueId: Int = 39, date: String = today, score: String = "0-0") =
        matches.save(
            MatchEntity(
                fixtureId = fixtureId, homeTeam = "Arsenal", awayTeam = "Chelsea", homeTeamId = 42, awayTeamId = 49,
                matchDate = date, matchTime = time, score = score, leagueId = leagueId, season = 2025
            )
        )

    // 킥오프 09:30(한국) = 00:30(UTC)
    private fun fixture(id: Long, status: String, elapsed: Int?, home: Int, away: Int, leagueId: Int = 39) = """
        {"fixture":{"id":$id,"date":"2026-01-01T00:30:00+00:00","status":{"short":"$status","elapsed":${elapsed ?: "null"}},"venue":{"name":"Emirates"}},
         "league":{"id":$leagueId,"season":2025,"round":"Regular Season - 1"},
         "teams":{"home":{"id":42,"name":"Arsenal"},"away":{"id":49,"name":"Chelsea"}},
         "goals":{"home":$home,"away":$away}}
    """.trimIndent()

    private fun body(vararg items: String) = """{"errors":[],"response":[${items.joinToString(",")}]}"""

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun reload(fixtureId: Long) = matches.findByFixtureId(fixtureId)!!

    // ── 외부 호출을 하지 않는 경우 ──────────────────────────────

    @Test
    fun `진행 중일 수 있는 경기가 없으면 외부 API를 부르지 않는다`() {
        // 기대값이 없으므로 외부 호출이 나가면 테스트가 실패한다
        match(1, "09:30 (종료)")                 // 이미 끝남
        match(2, "09:30 (취소)")
        match(3, "09:30 (PST)")                  // 연기
        match(4, "23:00")                        // 아직 멀었음
        match(5, "09:30", leagueId = 2)          // 지원하지 않는 리그
        match(6, "09:30", date = "2025-12-20")   // 오래된 날짜
        clock.advance(Duration.ofMinutes(40))    // 09:40

        val result = sync.sync()

        assertEquals(0, result.candidates)
        assertEquals(0, result.apiCalls)
        footballApi.verify()
    }

    @Test
    fun `킥오프 5분 전부터 3시간 30분 뒤까지만 후보이다`() {
        match(1, "09:30")

        clock.advance(Duration.ofMinutes(24))    // 09:24 — 킥오프 6분 전
        assertEquals(0, sync.sync().candidates)

        clock.advance(Duration.ofMinutes(3))     // 09:27 — 킥오프 3분 전
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body()))
        assertEquals(1, sync.sync().candidates)

        clock.advance(Duration.ofHours(3).plusMinutes(4))    // 12:31 — 킥오프 3시간 1분 뒤 (시작 표시 그대로라 연기 여부도 확인한다)
        freshFootballApi().apply {
            expect(requestTo(liveUrl)).andRespond(ok(body()))
            expect(requestTo("$base/fixtures?ids=1")).andRespond(ok(body()))
        }
        assertEquals(1, sync.sync().candidates)

        clock.advance(Duration.ofMinutes(30))    // 13:01 — 3시간 31분 뒤
        freshFootballApi()
        assertEquals(0, sync.sync().candidates)
    }

    // ── 갱신 ────────────────────────────────────────────────────

    @Test
    fun `진행 중인 경기는 점수와 경기 시간이 갱신되고 앱이 보는 경기 목록에도 반영된다`() {
        match(1, "09:30")
        clock.advance(Duration.ofMinutes(40))
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body(fixture(1, "1H", 10, 1, 0))))

        val result = sync.sync()

        assertEquals(1, result.candidates)
        assertEquals(1, result.liveFixtures)
        assertEquals(1, result.updated)
        assertEquals(1, result.apiCalls)
        assertEquals("1-0", reload(1).score)
        assertEquals("09:30 (진행중 10')", reload(1).matchTime)
        mockMvc.get("/api/soccer/matches?date=$today").andExpect {
            jsonPath("$[0].score") { value("1-0") }
            jsonPath("$[0].matchTime") { value("09:30 (진행중 10')") }
        }
        footballApi.verify()
    }

    @Test
    fun `바뀐 것이 없으면 저장하지 않는다`() {
        match(1, "09:30 (진행중 10')", score = "1-0")
        clock.advance(Duration.ofMinutes(40))
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body(fixture(1, "1H", 10, 1, 0))))

        val result = sync.sync()

        assertEquals(0, result.updated)
        assertEquals("1-0", reload(1).score)
    }

    @Test
    fun `하프타임 등 경기 시간이 없는 상태도 진행 중으로 표시한다`() {
        match(1, "09:30 (진행중 45')")
        clock.advance(Duration.ofMinutes(60))
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body(fixture(1, "HT", 45, 1, 1))))

        sync.sync()

        assertEquals("09:30 (진행중)", reload(1).matchTime)
        assertEquals("1-1", reload(1).score)
    }

    @Test
    fun `라이브 목록에서 사라진 경기는 한 번 더 확인해 종료 결과를 반영한다`() {
        match(1, "09:30 (진행중 88')", score = "2-1")
        clock.advance(Duration.ofMinutes(130))
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body()))
        footballApi.expect(requestTo("$base/fixtures?ids=1")).andRespond(ok(body(fixture(1, "FT", 90, 3, 1))))
        // 끝난 경기의 확정본 저장: 경기 상세 + 라인업
        footballApi.expect(requestTo("$base/fixtures?id=1")).andRespond(ok(body(fixture(1, "FT", 90, 3, 1))))
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=1")).andRespond(ok(body()))

        val result = sync.sync()

        assertEquals(1, result.finished)
        assertEquals(4, result.apiCalls)
        assertEquals(1, result.updated)
        assertEquals("3-1", reload(1).score)
        assertEquals("09:30 (종료)", reload(1).matchTime)

        // 끝난 경기는 더 이상 후보가 아니므로 다음 주기에는 호출이 없다
        freshFootballApi()
        assertEquals(0, sync.sync().apiCalls)
    }

    @Test
    fun `킥오프가 한참 지났는데도 시작 전 표시인 경기는 연기 여부를 확인한다`() {
        match(1, "09:30")
        clock.advance(Duration.ofMinutes(45))   // 09:45 — 킥오프 15분 뒤
        footballApi.expect(requestTo(liveUrl)).andRespond(ok(body()))
        footballApi.expect(requestTo("$base/fixtures?ids=1")).andRespond(ok(body(fixture(1, "PST", null, 0, 0))))

        sync.sync()

        assertEquals("09:30 (PST)", reload(1).matchTime)
    }

    // ── 오류 ────────────────────────────────────────────────────

    @Test
    fun `외부 API 오류가 나도 예외 없이 넘어가고 다음 주기에 다시 시도한다`() {
        match(1, "09:30")
        clock.advance(Duration.ofMinutes(40))
        footballApi.expect(requestTo(liveUrl)).andRespond(withServerError())

        val failed = sync.sync()
        assertTrue(failed.failed)
        assertEquals(0, failed.updated)
        assertEquals("09:30", reload(1).matchTime)

        freshFootballApi().expect(requestTo(liveUrl)).andRespond(ok(body(fixture(1, "1H", 12, 0, 1))))
        val recovered = sync.sync()
        assertFalse(recovered.failed)
        assertEquals("0-1", reload(1).score)
    }

    @Test
    fun `외부 API가 오류 응답을 주면 저장하지 않는다`() {
        match(1, "09:30")
        clock.advance(Duration.ofMinutes(40))
        footballApi.expect(requestTo(liveUrl)).andRespond(
            ok("""{"errors":{"requests":"You have reached the request limit for the day"},"response":[]}""")
        )

        val result = sync.sync()

        assertTrue(result.failed)
        assertEquals("0-0", reload(1).score)
    }

    // ── 호출량 ──────────────────────────────────────────────────

    @Test
    fun `응답 헤더로 오늘 남은 호출 수를 알려 준다`() {
        match(1, "09:30")
        clock.advance(Duration.ofMinutes(40))
        footballApi.expect(requestTo(liveUrl)).andRespond(
            ok(body(fixture(1, "1H", 10, 0, 0))).let { creator ->
                org.springframework.test.web.client.ResponseCreator { request ->
                    creator.createResponse(request).also { it.headers.add("x-ratelimit-requests-remaining", "6900") }
                }
            }
        )

        assertEquals(6900, sync.sync().remainingCalls)
    }
}
