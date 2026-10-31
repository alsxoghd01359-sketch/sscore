package com.sscore.admin

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.MatchEntity
import com.sscore.entity.TeamEntity
import com.sscore.match.MatchDetailRepository
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberRole
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestPropertySource(properties = ["sscore.backfill.async=false"])
class MatchBackfillApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var details: MatchDetailRepository
    @Autowired private lateinit var playerStats: PlayerStatRepository
    @Autowired private lateinit var events: MatchEventRepository

    private val base = "https://v3.football.api-sports.io"
    private val password = "Passw0rd!"
    private lateinit var admin: String

    @BeforeEach
    fun seed() {
        details.deleteAll(); playerStats.deleteAll(); events.deleteAll(); matches.deleteAll(); teams.deleteAll()
        sessions.deleteAll(); members.deleteAll()
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        members.save(
            Member(email = "boss@example.com", password = passwordEncoder.encode(password)!!, nickname = "boss",
                role = MemberRole.ADMIN, createdAt = LocalDateTime.now(clock))
        )
        admin = login()
    }

    private fun login(): String {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"boss@example.com","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun match(fixtureId: Long, season: Int, time: String = "23:00 (종료)") = matches.save(
        MatchEntity(
            fixtureId = fixtureId, homeTeam = "Arsenal", awayTeam = "Chelsea", homeTeamId = 42, awayTeamId = 49,
            matchDate = "${season}-10-01", matchTime = time, score = "2-1", leagueId = 39, season = season
        )
    )

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private val lineupTeam = """{"team":{"name":"Arsenal"},"formation":"4-3-3","coach":{"name":"Arteta"},
        "startXI":[{"player":{"id":1,"name":"Raya","number":22,"pos":"G","grid":"1:1"}}],"substitutes":[]}"""

    private fun fixtureDetails(id: Long, withLineups: Boolean) = """
        {"errors":[],"response":[{
          "fixture":{"id":$id,"date":"2021-10-01T14:00:00+00:00","referee":"M. Oliver","status":{"short":"FT"}},
          "events":[{"time":{"elapsed":23,"extra":null},"team":{"id":42,"name":"Arsenal"},"player":{"name":"B. Saka"},
                     "assist":{"name":"M. Odegaard"},"type":"Goal","detail":"Normal Goal"}],
          "statistics":[],
          "players":[{"team":{"id":42,"name":"Arsenal"},"players":[
                       {"player":{"id":10,"name":"B. Saka"},"statistics":[{"games":{"minutes":90,"position":"F","rating":"8.1"}}]}]}],
          "lineups":${if (withLineups) "[$lineupTeam]" else "[]"}
        }]}
    """.trimIndent()

    private val lineupsResponse = """{"errors":[],"response":[$lineupTeam]}"""
    private val emptyResponse = """{"errors":[],"response":[]}"""

    private fun backfill(query: String = ""): ResultActionsDsl =
        mockMvc.post("/api/admin/matches/backfill?delayMs=0&$query") { header("Authorization", "Bearer $admin") }

    private fun status() = mockMvc.get("/api/admin/matches/backfill/status") { header("Authorization", "Bearer $admin") }

    @Test
    fun `상세 시즌은 경기당 호출 1번으로 타임라인 선수 기록 라인업을 모두 저장한다`() {
        match(700, 2021)
        footballApi.expect(requestTo("$base/fixtures?id=700")).andRespond(ok(fixtureDetails(700, withLineups = true)))

        backfill("from=2021&to=2021&detailFrom=2020").andExpect { status { isOk() } }

        footballApi.verify()
        val row = assertNotNull(details.findById(700).orElse(null))
        assertNotNull(row.infoJson)
        assertTrue(row.lineupsJson!!.contains("Raya"))
        assertEquals(1, playerStats.findByFixtureId(700).size)
        assertEquals(1, events.findAll().size)
    }

    @Test
    fun `응답에 라인업이 없으면 라인업을 따로 받는다`() {
        match(701, 2021)
        footballApi.expect(requestTo("$base/fixtures?id=701")).andRespond(ok(fixtureDetails(701, withLineups = false)))
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=701")).andRespond(ok(lineupsResponse))

        backfill("from=2021&to=2021").andExpect { status { isOk() } }

        footballApi.verify()
        assertTrue(details.findById(701).get().lineupsJson!!.contains("Raya"))
        status().andExpect { status { isOk() }; jsonPath("$.calls") { value(2) }; jsonPath("$.stored") { value(1) } }
    }

    @Test
    fun `상세 시즌 이전은 라인업만 받고 선수 기록과 타임라인은 저장하지 않는다`() {
        match(702, 2017)
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=702")).andRespond(ok(lineupsResponse))

        backfill("from=2015&to=2017&detailFrom=2020").andExpect { status { isOk() } }

        footballApi.verify()
        val row = details.findById(702).get()
        assertNull(row.infoJson)
        assertTrue(row.lineupsJson!!.contains("Raya"))
        assertEquals(0, playerStats.findByFixtureId(702).size)
        assertEquals(0, events.findAll().size)
    }

    @Test
    fun `라인업이 없는 옛 경기는 빈 목록으로 저장해 다음 실행에서 다시 받지 않는다`() {
        match(703, 2017)
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=703")).andRespond(ok(emptyResponse))
        backfill("from=2017&to=2017").andExpect { status { isOk() } }
        assertEquals("[]", details.findById(703).get().lineupsJson)

        // 외부 API 기대값이 없으므로 다시 호출이 나가면 실패로 집계된다
        freshFootballApi()
        backfill("from=2017&to=2017").andExpect { status { isOk() } }
        status().andExpect { jsonPath("$.calls") { value(0) }; jsonPath("$.skipped") { value(1) }; jsonPath("$.failed") { value(0) } }
    }

    @Test
    fun `끝나지 않은 경기는 건드리지 않는다`() {
        match(704, 2021, time = "09:30 (진행중 20')")
        match(705, 2021, time = "23:00")

        backfill("from=2021&to=2021").andExpect { status { isOk() } }

        assertEquals(0, details.count())
        status().andExpect { jsonPath("$.calls") { value(0) } }
    }

    @Test
    fun `호출 한도에 닿으면 멈추고 다음 실행에서 이어서 처리한다`() {
        match(706, 2017); match(707, 2017)
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=706")).andRespond(ok(lineupsResponse))

        backfill("from=2017&to=2017&maxCalls=1").andExpect { status { isOk() } }
        assertEquals(1, details.count())
        status().andExpect { jsonPath("$.calls") { value(1) }; jsonPath("$.stoppedBecause") { value("호출 한도(1회)에 도달") } }

        val remaining = if (details.existsById(706)) 707 else 706
        freshFootballApi().expect(requestTo("$base/fixtures/lineups?fixture=$remaining")).andRespond(ok(lineupsResponse))
        backfill("from=2017&to=2017&maxCalls=10").andExpect { status { isOk() } }
        assertEquals(2, details.count())
        status().andExpect { jsonPath("$.stored") { value(1) }; jsonPath("$.skipped") { value(1) } }
    }

    @Test
    fun `관리자만 실행할 수 있다`() {
        mockMvc.post("/api/admin/matches/backfill").andExpect { status { isUnauthorized() } }
    }
}
