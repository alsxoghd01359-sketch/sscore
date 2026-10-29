package com.sscore.match

import com.sscore.entity.MatchEntity
import com.sscore.entity.TeamEntity
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
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

class MatchDetailApiTest : ApiTest() {

    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var details: MatchDetailRepository
    @Autowired private lateinit var playerStats: PlayerStatRepository
    @Autowired private lateinit var events: MatchEventRepository

    private val base = "https://v3.football.api-sports.io"
    private val finished = 500L
    private val live = 501L

    @BeforeEach
    fun seed() {
        details.deleteAll(); playerStats.deleteAll(); events.deleteAll(); matches.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        match(finished, "23:00 (종료)", "2-1")
        match(live, "09:30 (진행중 20')", "1-0")
    }

    private fun match(fixtureId: Long, time: String, score: String) = matches.save(
        MatchEntity(
            fixtureId = fixtureId, homeTeam = "Arsenal", awayTeam = "Chelsea", homeTeamId = 42, awayTeamId = 49,
            matchDate = "2026-01-01", matchTime = time, score = score, leagueId = 39, season = 2025
        )
    )

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun fixtureDetails(id: Long, status: String) = """
        {"errors":[],"response":[{
          "fixture":{"id":$id,"date":"2026-01-01T14:00:00+00:00","referee":"M. Oliver","status":{"short":"$status"}},
          "events":[{"time":{"elapsed":23,"extra":null},"team":{"id":42,"name":"Arsenal"},"player":{"name":"B. Saka"},
                     "assist":{"name":"M. Odegaard"},"type":"Goal","detail":"Normal Goal"}],
          "statistics":[{"team":{"id":42},"statistics":[{"type":"Ball Possession","value":"60%"},{"type":"Total Shots","value":12}]},
                        {"team":{"id":49},"statistics":[{"type":"Ball Possession","value":"40%"}]}],
          "players":[{"team":{"id":42,"name":"Arsenal"},"players":[
                       {"player":{"id":10,"name":"B. Saka"},"statistics":[{"games":{"minutes":90,"position":"F","rating":"8.1"},
                         "goals":{"total":1,"assists":0},"passes":{"total":40,"key":3,"accuracy":"30"}}]}]},
                     {"team":{"id":49,"name":"Chelsea"},"players":[
                       {"player":{"id":20,"name":"C. Palmer"},"statistics":[{"games":{"minutes":90,"position":"M","rating":"6.5"}}]}]}]
        }]}
    """.trimIndent()

    private val lineups = """
        {"errors":[],"response":[{"team":{"name":"Arsenal"},"formation":"4-3-3","coach":{"name":"Arteta"},
         "startXI":[{"player":{"id":1,"name":"Raya","number":22,"pos":"G","grid":"1:1"}}],"substitutes":[]}]}
    """.trimIndent()

    private fun info(id: Long) = mockMvc.get("/api/soccer/matches/$id/info")
    private fun lineups(id: Long) = mockMvc.get("/api/soccer/lineups/$id")

    // ── 없는 경기 ───────────────────────────────────────────────

    @Test
    fun `우리 DB에 없는 경기는 외부 호출 없이 404이다`() {
        // 외부 API 기대값이 없으므로 호출이 나가면 502가 된다
        info(9999).andExpect { status { isNotFound() }; jsonPath("$.code") { value("MATCH_NOT_FOUND") } }
        lineups(9999).andExpect { status { isNotFound() } }
    }

    @Test
    fun `경기 번호로 경기 하나를 조회할 수 있다 (알림을 눌렀을 때)`() {
        mockMvc.get("/api/soccer/matches/$live").andExpect {
            status { isOk() }
            jsonPath("$.fixtureId") { value(live) }
            jsonPath("$.homeTeam") { value("Arsenal") }
            jsonPath("$.score") { value("1-0") }
        }
        mockMvc.get("/api/soccer/matches/9999").andExpect { status { isNotFound() }; jsonPath("$.code") { value("MATCH_NOT_FOUND") } }
    }

    // ── 진행 중인 경기: 짧게 캐시 ────────────────────────────────

    @Test
    fun `진행 중인 경기는 30초 동안 외부 호출 없이 캐시에서 주고 확정본은 저장하지 않는다`() {
        footballApi.expect(requestTo("$base/fixtures?id=$live")).andRespond(ok(fixtureDetails(live, "1H")))

        info(live).andExpect {
            status { isOk() }
            jsonPath("$.referee") { value("M. Oliver") }
            jsonPath("$.events[0].type") { value("Goal") }
            jsonPath("$.momPlayer.name") { value("B. Saka") }
            jsonPath("$.playerRatings.10") { value(8.1) }
            jsonPath("$.playerRatings.20") { value(6.5) }
        }
        // 같은 경기를 여러 번 봐도 외부 호출이 늘지 않는다 (기대값이 하나뿐이므로 더 부르면 실패)
        repeat(5) { info(live).andExpect { status { isOk() }; jsonPath("$.momPlayer.name") { value("B. Saka") } } }

        assertFalse(details.existsById(live))
        assertEquals(0, playerStats.findByFixtureId(live).size)

        // 30초가 지나면 새로 받는다
        clock.advance(Duration.ofSeconds(31))
        freshFootballApi().expect(requestTo("$base/fixtures?id=$live")).andRespond(ok(fixtureDetails(live, "1H")))
        info(live).andExpect { status { isOk() } }
        footballApi.verify()
    }

    @Test
    fun `진행 중인 경기의 라인업도 짧게 캐시한다`() {
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=$live")).andRespond(ok(lineups))

        repeat(4) { lineups(live).andExpect { status { isOk() }; jsonPath("$[0].formation") { value("4-3-3") } } }
        assertFalse(details.existsById(live))
    }

    // ── 끝난 경기: 확정본 ────────────────────────────────────────

    @Test
    fun `끝난 경기는 첫 조회 때 확정본을 저장하고 그 뒤에는 외부 호출 없이 DB에서 준다`() {
        footballApi.expect(requestTo("$base/fixtures?id=$finished")).andRespond(ok(fixtureDetails(finished, "FT")))

        val first = info(finished).andExpect { status { isOk() } }.andReturn().response.contentAsString

        // 확정본: 경기 정보 + 선수 평점 + 타임라인
        assertTrue(details.findById(finished).get().infoJson != null)
        assertEquals(2, playerStats.findByFixtureId(finished).size)
        assertEquals(8.1, playerStats.findByFixtureId(finished).first { it.playerId == 10L }.rating)
        assertEquals(1, events.findByFixtureId(finished).size)

        // 시간이 한참 지나도 외부 API를 부르지 않고 같은 내용을 준다
        clock.advance(Duration.ofHours(5))
        val second = info(finished).andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertEquals(first, second)
        footballApi.verify()
    }

    @Test
    fun `끝난 경기의 라인업도 한 번 저장하면 외부 호출 없이 DB에서 준다`() {
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=$finished")).andRespond(ok(lineups))

        lineups(finished).andExpect { status { isOk() } }
        assertTrue(details.findById(finished).get().lineupsJson != null)

        clock.advance(Duration.ofHours(2))
        lineups(finished).andExpect {
            status { isOk() }
            jsonPath("$[0].teamName") { value("Arsenal") }
            jsonPath("$[0].startXI[0].name") { value("Raya") }
        }
        footballApi.verify()
    }

    @Test
    fun `외부 API가 외부 라인업을 아직 주지 않으면 저장하지 않고 다음에 다시 받는다`() {
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=$finished")).andRespond(ok("""{"errors":[],"response":[]}"""))
        lineups(finished).andExpect { jsonPath("$.length()") { value(0) } }
        assertFalse(details.findById(finished).map { it.lineupsJson != null }.orElse(false))
    }

    @Test
    fun `끝난 경기의 첫 조회에서 외부 API가 실패하면 502이고 아무것도 저장하지 않는다`() {
        footballApi.expect(requestTo("$base/fixtures?id=$finished")).andRespond(withServerError())

        info(finished).andExpect { status { isBadGateway() }; jsonPath("$.code") { value("FOOTBALL_API_ERROR") } }
        assertFalse(details.existsById(finished))

        // 다음 조회에서 성공하면 그때 저장된다
        freshFootballApi().expect(requestTo("$base/fixtures?id=$finished")).andRespond(ok(fixtureDetails(finished, "FT")))
        info(finished).andExpect { status { isOk() } }
        assertTrue(details.existsById(finished))
    }

    // ── 경기 종료 감지 ───────────────────────────────────────────

    @Test
    fun `라이브 갱신이 경기 종료를 감지하면 확정본을 바로 저장한다`() {
        matches.deleteAll()
        match(1, "09:30 (진행중 88')", "2-1")
        clock.advance(Duration.ofMinutes(130))   // 11:10 — 킥오프 1시간 40분 뒤

        footballApi.expect(requestTo("$base/fixtures?live=39-140-78-135-61")).andRespond(ok("""{"errors":[],"response":[]}"""))
        footballApi.expect(requestTo("$base/fixtures?ids=1")).andRespond(
            ok(
                """{"errors":[],"response":[{"fixture":{"id":1,"date":"2026-01-01T00:30:00+00:00","status":{"short":"FT","elapsed":90}},
                "league":{"id":39,"season":2025,"round":"R1"},"teams":{"home":{"id":42,"name":"Arsenal"},"away":{"id":49,"name":"Chelsea"}},
                "goals":{"home":3,"away":1}}]}"""
            )
        )
        // 종료를 확인한 뒤 확정본을 받아 저장한다 (경기 상세 + 라인업)
        footballApi.expect(requestTo("$base/fixtures?id=1")).andRespond(ok(fixtureDetails(1, "FT")))
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=1")).andRespond(ok(lineups))

        val result = liveSync.sync()

        assertEquals(1, result.finished)
        assertTrue(details.findById(1).get().infoJson != null)
        assertTrue(details.findById(1).get().lineupsJson != null)

        // 이후 사용자의 조회는 외부 호출이 없다
        info(1).andExpect { status { isOk() } }
        lineups(1).andExpect { status { isOk() } }
        footballApi.verify()
    }

    @Autowired private lateinit var liveSync: LiveMatchSync

    // ── 상대전적 ────────────────────────────────────────────────

    @Test
    fun `상대전적은 10분 동안 캐시하고 없는 팀은 외부 호출 없이 404이다`() {
        footballApi.expect(requestTo("$base/fixtures/headtohead?h2h=42-49")).andRespond(
            ok(
                """{"errors":[],"response":[{"fixture":{"id":7,"date":"2025-09-01T14:00:00+00:00","status":{"short":"FT"}},
                "league":{"id":39,"season":2025},"teams":{"home":{"id":42,"name":"Arsenal"},"away":{"id":49,"name":"Chelsea"}},
                "goals":{"home":2,"away":1}}]}"""
            )
        )
        repeat(3) {
            mockMvc.get("/api/soccer/h2h?homeId=42&awayId=49").andExpect {
                status { isOk() }
                jsonPath("$.homeWins") { value(1) }
            }
        }
        mockMvc.get("/api/soccer/h2h?homeId=42&awayId=9999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("TEAM_NOT_FOUND") }
        }
        footballApi.verify()
    }
}
