package com.sscore.admin

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.MatchEntity
import com.sscore.entity.TeamEntity
import com.sscore.match.InjuryRepository
import com.sscore.match.MatchDetailRepository
import com.sscore.match.MatchTeamStatRepository
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
    @Autowired private lateinit var teamStats: MatchTeamStatRepository
    @Autowired private lateinit var injuryRepository: InjuryRepository

    private val base = "https://v3.football.api-sports.io"
    private val password = "Passw0rd!"
    private lateinit var admin: String

    @BeforeEach
    fun seed() {
        details.deleteAll(); playerStats.deleteAll(); events.deleteAll(); teamStats.deleteAll(); injuryRepository.deleteAll()
        matches.deleteAll(); teams.deleteAll()
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

    private val teamStatistics = """[
        {"team":{"id":42},"statistics":[{"type":"Ball Possession","value":"60%"},{"type":"Total Shots","value":12},
          {"type":"Shots on Goal","value":5},{"type":"Yellow Cards","value":null},{"type":"expected_goals","value":"1.83"}]},
        {"team":{"id":49},"statistics":[{"type":"Ball Possession","value":"40%"},{"type":"expected_goals","value":"0.7"}]}]"""

    private fun fixtureDetails(id: Long, withLineups: Boolean, withStats: Boolean = true) = """
        {"errors":[],"response":[{
          "fixture":{"id":$id,"date":"2021-10-01T14:00:00+00:00","referee":"M. Oliver","status":{"short":"FT"}},
          "events":[{"time":{"elapsed":23,"extra":null},"team":{"id":42,"name":"Arsenal"},"player":{"name":"B. Saka"},
                     "assist":{"name":"M. Odegaard"},"type":"Goal","detail":"Normal Goal"}],
          "statistics":${if (withStats) teamStatistics else "[]"},
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
    fun `팀 통계를 홈 원정별로 저장하고 xG는 소수로 받는다`() {
        match(700, 2021)
        footballApi.expect(requestTo("$base/fixtures?id=700")).andRespond(ok(fixtureDetails(700, withLineups = true)))

        backfill("from=2021&to=2021").andExpect { status { isOk() } }

        val stats = teamStats.findByFixtureId(700).associateBy { it.teamId }
        assertEquals(2, stats.size)
        val home = stats.getValue(42)
        assertTrue(home.home)
        assertEquals(60, home.possession)
        assertEquals(12, home.shotsTotal)
        assertEquals(5, home.shotsOnGoal)
        assertNull(home.yellowCards)            // API가 null로 준 값은 비워 둔다
        assertEquals("1.83", home.expectedGoals.toString())
        assertEquals("0.70", stats.getValue(49).expectedGoals.toString())
        assertEquals(false, stats.getValue(49).home)
    }

    @Test
    fun `통계가 없는 경기도 빈 통계 두 줄을 남겨 다시 받지 않는다`() {
        match(708, 2021)
        footballApi.expect(requestTo("$base/fixtures?id=708")).andRespond(ok(fixtureDetails(708, withLineups = true, withStats = false)))
        backfill("from=2021&to=2021").andExpect { status { isOk() } }
        val stats = teamStats.findByFixtureId(708)
        assertEquals(2, stats.size)
        assertTrue(stats.all { it.possession == null && it.expectedGoals == null })

        freshFootballApi()   // 기대값이 없으므로 다시 호출하면 실패로 집계된다
        backfill("from=2021&to=2021").andExpect { status { isOk() } }
        status().andExpect { jsonPath("$.calls") { value(0) }; jsonPath("$.failed") { value(0) } }
    }

    @Test
    fun `확정본은 있지만 팀 통계가 없는 경기는 한 번 더 받아 통계를 채운다`() {
        match(709, 2021)
        details.save(com.sscore.match.MatchDetail(709, infoJson = "{\"fixtureId\":709}", lineupsJson = "[]", finalizedAt = LocalDateTime.now(clock)))
        footballApi.expect(requestTo("$base/fixtures?id=709")).andRespond(ok(fixtureDetails(709, withLineups = true)))

        backfill("from=2021&to=2021").andExpect { status { isOk() } }

        footballApi.verify()
        assertEquals(2, teamStats.findByFixtureId(709).size)
        status().andExpect { jsonPath("$.calls") { value(1) } }
    }

    // ── 부상/결장 명단 ──────────────────────────────────────────

    private val injuriesResponse = """
        {"errors":[],"response":[
          {"player":{"id":7,"name":"B. Saka","type":"Missing Fixture","reason":"Thigh Injury"},"team":{"id":42,"name":"Arsenal"},
           "fixture":{"id":700},"league":{"id":39,"season":2021}},
          {"player":{"id":7,"name":"B. Saka","type":"Missing Fixture","reason":"Thigh Injury"},"team":{"id":42,"name":"Arsenal"},
           "fixture":{"id":700},"league":{"id":39,"season":2021}},
          {"player":{"id":8,"name":"G. Jesus","type":"Questionable","reason":"Knock"},"team":{"id":42,"name":"Arsenal"},
           "fixture":{"id":701},"league":{"id":39,"season":2021}}]}
    """.trimIndent()

    @Test
    fun `부상 명단을 리그 시즌별로 저장하고 같은 경기 같은 선수는 한 번만 둔다`() {
        // 5대 리그 중 프리미어리그(39)만 내용이 있고 나머지는 비어 있다
        listOf(39, 140, 78, 135, 61).forEach { id ->
            footballApi.expect(requestTo("$base/injuries?league=$id&season=2021"))
                .andRespond(ok(if (id == 39) injuriesResponse else emptyResponse))
        }

        mockMvc.post("/api/admin/injuries/load?from=2021&to=2021") { header("Authorization", "Bearer $admin") }
            .andExpect { status { isOk() } }

        footballApi.verify()
        assertEquals(2, injuryRepository.count())
        val saka = injuryRepository.findByFixtureId(700).single()
        assertEquals("Missing Fixture", saka.type)
        assertEquals("Thigh Injury", saka.reason)
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
