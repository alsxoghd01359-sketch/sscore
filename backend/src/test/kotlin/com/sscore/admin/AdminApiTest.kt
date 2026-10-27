package com.sscore.admin

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberRole
import com.sscore.repository.ChampionRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals

@TestPropertySource(properties = ["sscore.admin.emails=boss@example.com, other-boss@example.com"])
class AdminApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var champions: ChampionRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var adminAccounts: AdminAccounts

    private val password = "Passw0rd!"

    @BeforeEach
    fun clean() {
        sessions.deleteAll()
        members.deleteAll()
        champions.deleteAll()
    }

    private fun member(email: String, role: MemberRole, nickname: String) {
        members.save(
            Member(
                email = email, password = passwordEncoder.encode(password)!!, nickname = nickname,
                role = role, createdAt = LocalDateTime.now(clock)
            )
        )
    }

    private fun accessTokenOf(email: String): String {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun addChampion(token: String?): ResultActionsDsl =
        mockMvc.post("/api/admin/champions/add?leagueId=39&season=1993&teamId=33&teamName=Manchester United") {
            if (token != null) header("Authorization", "Bearer $token")
        }

    @Test
    fun `게스트는 관리 기능을 호출할 수 없다`() {
        addChampion(null).andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/admin/import/status").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/admin/import/phase1").andExpect { status { isUnauthorized() } }
        assertEquals(0, champions.count())
    }

    @Test
    fun `일반 회원도 관리 기능을 호출할 수 없다`() {
        member("user@example.com", MemberRole.USER, "일반회원")
        val token = accessTokenOf("user@example.com")

        addChampion(token).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/admin/import/status") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
        assertEquals(0, champions.count())
    }

    @Test
    fun `ADMIN은 관리 기능을 호출할 수 있다`() {
        member("admin@example.com", MemberRole.ADMIN, "관리자")
        val token = accessTokenOf("admin@example.com")

        addChampion(token).andExpect { status { isOk() } }
        assertEquals(1, champions.count())

        mockMvc.get("/api/admin/import/status") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `상태를 바꾸는 관리 기능은 조회 요청으로는 실행되지 않는다`() {
        member("admin@example.com", MemberRole.ADMIN, "관리자")
        val token = accessTokenOf("admin@example.com")

        mockMvc.get("/api/admin/champions/add?leagueId=39&season=1993&teamId=33&teamName=X") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isMethodNotAllowed() } }
        assertEquals(0, champions.count())
    }

    @Test
    fun `예전 관리 주소와 디버그 주소는 사라졌다`() {
        mockMvc.get("/api/soccer/admin/import/status").andExpect { status { isNotFound() } }
        mockMvc.get("/api/soccer/admin/debug/trophies/33").andExpect { status { isNotFound() } }
        mockMvc.get("/api/soccer/admin/debug/player-stats?teamId=33&season=2025").andExpect { status { isNotFound() } }
        mockMvc.get("/api/soccer/init-range?start=2025&end=2025").andExpect { status { isNotFound() } }
        mockMvc.get("/api/soccer/force-load").andExpect { status { isNotFound() } }
    }

    @Test
    fun `게스트의 조회 기능은 그대로 동작한다`() {
        mockMvc.get("/api/soccer/matches?date=2026-03-01").andExpect { status { isOk() } }
    }

    @Test
    fun `ADMIN_EMAILS에 있는 이메일로 서버를 시작하면 이미 가입한 회원이 ADMIN이 된다`() {
        member("boss@example.com", MemberRole.USER, "사장님")
        member("someone@example.com", MemberRole.USER, "다른회원")

        adminAccounts.run(DefaultApplicationArguments())

        assertEquals(MemberRole.ADMIN, members.findByEmail("boss@example.com")!!.role)
        assertEquals(MemberRole.USER, members.findByEmail("someone@example.com")!!.role)
    }

    @Test
    fun `ADMIN_EMAILS에 있는 이메일로 가입을 마치면 ADMIN이 된다`() {
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"other-boss@example.com","password":"$password","nickname":"운영자"}"""
        }.andExpect { status { isAccepted() } }
        mockMvc.post("/api/auth/signup/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"other-boss@example.com","code":"${mail.lastCodeTo("other-boss@example.com")}"}"""
        }.andExpect { status { isCreated() } }

        assertEquals(MemberRole.ADMIN, members.findByEmail("other-boss@example.com")!!.role)
    }
}
