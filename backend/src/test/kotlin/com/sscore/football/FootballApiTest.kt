package com.sscore.football

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberRole
import com.sscore.repository.MatchRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals

class FootballApiTest : ApiTest() {

    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var seasons: SeasonResolver
    @Autowired private lateinit var restTemplate: org.springframework.web.client.RestTemplate

    private val base = "https://v3.football.api-sports.io"

    @Autowired private lateinit var teams: com.sscore.repository.TeamRepository

    @BeforeEach
    fun clean() {
        matches.deleteAll()
        sessions.deleteAll()
        members.deleteAll()
        teams.deleteAll()
        // 라인업·상대전적은 우리 DB에 있는 경기/팀만 조회할 수 있다
        teams.save(com.sscore.entity.TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(com.sscore.entity.TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        listOf(1L, 2L, 3L, 100L).forEach { id ->
            matches.save(
                com.sscore.entity.MatchEntity(
                    fixtureId = id, homeTeam = "Arsenal", awayTeam = "Chelsea", homeTeamId = 42, awayTeamId = 49,
                    matchDate = "2026-01-01", matchTime = "09:30 (진행중 20')", score = "0-0", leagueId = 39, season = 2025
                )
            )
        }
    }

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    // MockRestServiceServer는 요청이 나간 뒤 기대값을 더 추가할 수 없어서, 한 테스트에서 여러 번 부를 때는 매번 새로 만든다
    private fun expectFootball(): MockRestServiceServer {
        footballApi = MockRestServiceServer.bindTo(restTemplate).build()
        return footballApi
    }

    private fun adminToken(): String {
        members.save(
            Member(
                email = "admin@example.com", password = passwordEncoder.encode("Passw0rd!")!!, nickname = "관리자",
                role = MemberRole.ADMIN, createdAt = LocalDateTime.now(clock)
            )
        )
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@example.com","password":"Passw0rd!"}"""
        }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private val standings = """
        {"errors":[],"response":[{"league":{"id":39,"season":2025,"standings":[[
          {"rank":1,"team":{"id":42,"name":"Arsenal"},"points":80,"goalsDiff":40,"form":"WWDLW",
           "all":{"played":38,"win":25,"draw":5,"lose":8,"goals":{"for":70,"against":30}},
           "goals":{"for":70,"against":30}},
          {"rank":2,"team":{"id":50,"name":"Manchester City"},"points":78,"goalsDiff":35,"form":"WWWWW",
           "all":{"played":38,"win":24,"draw":6,"lose":8},"goals":{"for":75,"against":40}}
        ]]}}]}
    """.trimIndent()

    private fun fixture(id: Long, leagueId: Int, date: String = "2025-08-16T14:00:00+00:00", status: String = "FT") = """
        {"fixture":{"id":$id,"date":"$date","status":{"short":"$status"},"venue":{"name":"Emirates"},"referee":"R. Ref"},
         "league":{"id":$leagueId,"season":2025,"round":"Regular Season - 1"},
         "teams":{"home":{"id":42,"name":"Arsenal"},"away":{"id":49,"name":"Chelsea"}},
         "goals":{"home":2,"away":1}}
    """.trimIndent()

    // ── 외부 API 응답을 우리 응답으로 바꾸는 동작 ───────────────────

    @Test
    fun `순위표는 외부 API 응답을 읽어 돌려주고 API 키를 헤더로 보낸다`() {
        footballApi.expect(requestTo("$base/standings?league=39&season=2025"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-apisports-key", "test-key"))
            .andRespond(ok(standings))

        mockMvc.get("/api/soccer/standings?leagueId=39&season=2025").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].rank") { value(1) }
            jsonPath("$[0].teamName") { value("Arsenal") }
            jsonPath("$[0].points") { value(80) }
            jsonPath("$[0].goalsFor") { value(70) }
        }
        footballApi.verify()
    }

    @Test
    fun `라인업과 상대전적도 외부 API 응답을 읽어 돌려준다`() {
        expectFootball().expect(requestTo("$base/fixtures/lineups?fixture=100")).andRespond(
            ok(
                """{"errors":[],"response":[{"team":{"name":"Arsenal"},"formation":"4-3-3","coach":{"name":"Arteta"},
                "startXI":[{"player":{"id":1,"name":"Raya","number":22,"pos":"G","grid":"1:1"}}],"substitutes":[]}]}"""
            )
        )
        mockMvc.get("/api/soccer/lineups/100").andExpect {
            status { isOk() }
            jsonPath("$[0].teamName") { value("Arsenal") }
            jsonPath("$[0].formation") { value("4-3-3") }
            jsonPath("$[0].startXI[0].name") { value("Raya") }
        }

        expectFootball().expect(requestTo("$base/fixtures/headtohead?h2h=42-49"))
            .andRespond(ok("""{"errors":[],"response":[${fixture(7, 39)}]}"""))
        mockMvc.get("/api/soccer/h2h?homeId=42&awayId=49").andExpect { status { isOk() } }
        footballApi.verify()
    }

    // ── 외부 API가 잘못 응답할 때 ────────────────────────────────

    @Test
    fun `외부 API가 오류를 알리면 502와 이유를 돌려준다`() {
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(
            ok("""{"errors":{"requests":"You have reached the request limit for the day"},"response":[]}""")
        )
        mockMvc.get("/api/soccer/standings?leagueId=39&season=2025").andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("FOOTBALL_API_ERROR") }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `외부 API 서버 오류나 이상한 응답 형식도 502이다`() {
        expectFootball().expect(requestTo("$base/fixtures/lineups?fixture=1")).andRespond(withServerError())
        mockMvc.get("/api/soccer/lineups/1").andExpect { status { isBadGateway() }; jsonPath("$.code") { value("FOOTBALL_API_ERROR") } }

        expectFootball().expect(requestTo("$base/fixtures/lineups?fixture=2")).andRespond(ok("""{"errors":[],"response":"oops"}"""))
        mockMvc.get("/api/soccer/lineups/2").andExpect { status { isBadGateway() } }

        expectFootball().expect(requestTo("$base/fixtures/lineups?fixture=3")).andRespond(ok("""{"errors":[]}"""))
        mockMvc.get("/api/soccer/lineups/3").andExpect { status { isBadGateway() } }
    }

    @Test
    fun `외부 API 오류로 경기 적재가 실패하면 아무것도 저장하지 않는다`() {
        val token = adminToken()
        matches.deleteAll()
        footballApi.expect(requestTo("$base/fixtures?league=39&season=2025")).andRespond(
            ok("""{"errors":{"token":"Error/Missing application key"},"response":[]}""")
        )

        mockMvc.post("/api/admin/matches/load?start=2025&end=2025") { header("Authorization", "Bearer $token") }
            .andExpect { status { isBadGateway() } }
        assertEquals(0, matches.count())
    }

    @Test
    fun `읽을 수 없는 경기 항목은 건너뛰고 읽을 수 있는 경기만 저장한다`() {
        val token = adminToken()
        matches.deleteAll()
        val broken = """{"fixture":{"id":999,"date":"not-a-date","status":{"short":"NS"}},"league":{"id":39,"season":2025},
            "teams":{"home":{"id":1,"name":"A"},"away":{"id":2,"name":"B"}}}"""
        val noTeams = """{"fixture":{"id":998,"date":"2025-08-16T14:00:00+00:00","status":{"short":"NS"}},"league":{"id":39,"season":2025}}"""
        listOf(39, 140, 78, 135, 61).forEachIndexed { i, leagueId ->
            footballApi.expect(requestTo("$base/fixtures?league=$leagueId&season=2025")).andRespond(
                ok("""{"errors":[],"response":[${fixture(100L + i, leagueId)},$broken,$noTeams]}""")
            )
        }

        mockMvc.post("/api/admin/matches/load?start=2025&end=2025") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }

        assertEquals(5, matches.count())
        val saved = matches.findByFixtureId(100)!!
        assertEquals("2025-08-16", saved.matchDate)
        assertEquals("23:00 (종료)", saved.matchTime)   // UTC 14:00 → 한국 시간 23:00
        assertEquals("2-1", saved.score)
        assertEquals(39, saved.leagueId)
        assertEquals(2025, saved.season)
        assertEquals(null, matches.findByFixtureId(999))
        assertEquals(null, matches.findByFixtureId(998))
    }

    // ── 시즌 ────────────────────────────────────────────────────

    @Test
    fun `현재 시즌은 날짜로 정해진다`() {
        // 테스트 시계의 시작은 2026-01-01(KST 오전 9시)
        assertEquals(2025, seasons.current())
        clock.advance(Duration.ofDays(195))   // 2026-07-15
        assertEquals(2026, seasons.current())
    }

    @Test
    fun `현재 시즌 데이터가 아직 없으면 데이터가 있는 가장 최근 시즌을 쓰고 있으면 현재 시즌을 쓴다`() {
        matches.deleteAll()
        matches.save(com.sscore.entity.MatchEntity(
            fixtureId = 9001, homeTeam = "A", awayTeam = "B", matchDate = "2025-10-01", matchTime = "20:00",
            score = "1-0", leagueId = 39, season = 2025
        ))
        clock.advance(Duration.ofDays(300))   // 2026-10: 현재 시즌은 2026이지만 데이터는 2025뿐
        assertEquals(2026, seasons.current())
        assertEquals(2025, seasons.effective())
        assertEquals(2024, seasons.resolve(2024))   // 요청한 시즌이 있으면 그대로

        matches.save(com.sscore.entity.MatchEntity(
            fixtureId = 9002, homeTeam = "A", awayTeam = "B", matchDate = "2026-09-01", matchTime = "20:00",
            score = "0-0", leagueId = 39, season = 2026
        ))
        assertEquals(2026, seasons.effective())
    }

    @Test
    fun `시즌을 보내지 않으면 데이터가 있는 최근 시즌으로 리그 경기를 돌려준다`() {
        matches.deleteAll()   // 기본으로 넣어 둔 경기는 빼고 이 테스트의 경기만 둔다
        matches.save(com.sscore.entity.MatchEntity(
            fixtureId = 9003, homeTeam = "Arsenal", awayTeam = "Chelsea", matchDate = "2025-10-01", matchTime = "20:00",
            score = "1-0", leagueId = 39, season = 2025
        ))
        clock.advance(Duration.ofDays(300))

        mockMvc.get("/api/soccer/league/39/matches").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    // ── 공통 오류 응답 ──────────────────────────────────────────

    @Test
    fun `필수 값이 없거나 형식이 틀리면 400 JSON이다`() {
        mockMvc.get("/api/soccer/h2h?homeId=1").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        mockMvc.get("/api/soccer/h2h?homeId=abc&awayId=2").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `없는 주소는 404 JSON이고 예상 못한 오류는 내부 정보 없이 500 JSON이다`() {
        mockMvc.get("/api/soccer/nothing-here").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
        mockMvc.get("/test/boom").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))) }
        }
    }
}
