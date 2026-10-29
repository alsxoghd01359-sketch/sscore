package com.sscore.follow

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.TeamEntity
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.repository.TeamRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime

class SupportedTeamApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var follows: FollowRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val password = "Passw0rd!"

    @BeforeEach
    fun seed() {
        follows.deleteAll(); sessions.deleteAll(); members.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        members.save(Member(email = "fan@example.com", password = passwordEncoder.encode(password)!!, nickname = "축구팬", createdAt = LocalDateTime.now(clock)))
    }

    private fun login(): String {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"fan@example.com","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun support(token: String, id: Int): ResultActionsDsl =
        mockMvc.put("/api/members/me/supported-team/$id") { header("Authorization", "Bearer $token") }

    private fun me(token: String): ResultActionsDsl = mockMvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }

    private fun followedIds(token: String): List<Int> {
        val body = mockMvc.get("/api/members/me/follows") { header("Authorization", "Bearer $token") }
            .andReturn().response.contentAsString
        return JsonPath.read(body, "$.teams[*].id")
    }

    @Test
    fun `처음에는 응원 팀이 없다`() {
        me(login()).andExpect { jsonPath("$.supportedTeamId") { doesNotExist() } }
    }

    @Test
    fun `응원 팀을 지정하면 내 정보에 나오고 그 팀이 자동으로 팔로우된다`() {
        val t = login()
        support(t, 42).andExpect { status { isOk() }; jsonPath("$.supportedTeamId") { value(42) } }

        me(t).andExpect { jsonPath("$.supportedTeamId") { value(42) } }
        kotlin.test.assertEquals(listOf(42), followedIds(t))
    }

    @Test
    fun `응원 팀을 바꿔도 이전 팀의 팔로우는 남는다`() {
        val t = login()
        support(t, 42)
        support(t, 49).andExpect { jsonPath("$.supportedTeamId") { value(49) } }

        kotlin.test.assertEquals(setOf(42, 49), followedIds(t).toSet())
        // 같은 팀을 다시 지정해도 팔로우가 중복되지 않는다
        support(t, 49)
        kotlin.test.assertEquals(2, follows.count())
    }

    @Test
    fun `응원 팀을 비워도 팔로우는 남는다`() {
        val t = login()
        support(t, 42)
        mockMvc.delete("/api/members/me/supported-team") { header("Authorization", "Bearer $t") }
            .andExpect { status { isOk() }; jsonPath("$.supportedTeamId") { doesNotExist() } }

        kotlin.test.assertEquals(listOf(42), followedIds(t))
    }

    @Test
    fun `다른 기기에서 로그인해도 응원 팀이 복원된다`() {
        support(login(), 42)

        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"fan@example.com","password":"$password"}"""
        }.andExpect { status { isOk() }; jsonPath("$.member.supportedTeamId") { value(42) } }.andReturn().response.contentAsString
        me(JsonPath.read(body, "$.accessToken")).andExpect { jsonPath("$.supportedTeamId") { value(42) } }
    }

    @Test
    fun `없는 팀은 404이고 로그인하지 않으면 401이다`() {
        val t = login()
        support(t, 9999).andExpect { status { isNotFound() }; jsonPath("$.code") { value("TEAM_NOT_FOUND") } }
        me(t).andExpect { jsonPath("$.supportedTeamId") { doesNotExist() } }
        mockMvc.put("/api/members/me/supported-team/42").andExpect { status { isUnauthorized() } }
    }
}
