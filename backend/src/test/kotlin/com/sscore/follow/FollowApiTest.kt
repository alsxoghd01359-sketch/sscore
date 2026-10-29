package com.sscore.follow

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.PlayerEntity
import com.sscore.entity.TeamEntity
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.repository.PlayerRepository
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
import kotlin.test.assertEquals

class FollowApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var follows: FollowRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val password = "Passw0rd!"

    @BeforeEach
    fun seed() {
        follows.deleteAll(); sessions.deleteAll(); members.deleteAll(); players.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        players.save(PlayerEntity(playerId = 10, name = "B. Saka", nationality = "England", photoUrl = "saka.png", position = "Attacker", team = null))
        member("fan@example.com", "축구팬")
        member("other@example.com", "다른팬")
    }

    private fun member(email: String, nickname: String) = members.save(
        Member(email = email, password = passwordEncoder.encode(password)!!, nickname = nickname, createdAt = LocalDateTime.now(clock))
    )

    private fun login(email: String): String {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun list(token: String): ResultActionsDsl = mockMvc.get("/api/members/me/follows") { header("Authorization", "Bearer $token") }
    private fun followTeam(token: String, id: Int) = mockMvc.put("/api/members/me/follows/teams/$id") { header("Authorization", "Bearer $token") }
    private fun unfollowTeam(token: String, id: Int) = mockMvc.delete("/api/members/me/follows/teams/$id") { header("Authorization", "Bearer $token") }
    private fun followPlayer(token: String, id: Long) = mockMvc.put("/api/members/me/follows/players/$id") { header("Authorization", "Bearer $token") }
    private fun unfollowPlayer(token: String, id: Long) = mockMvc.delete("/api/members/me/follows/players/$id") { header("Authorization", "Bearer $token") }

    @Test
    fun `처음에는 팔로우가 비어 있다`() {
        list(login("fan@example.com")).andExpect {
            status { isOk() }
            jsonPath("$.teams.length()") { value(0) }
            jsonPath("$.players.length()") { value(0) }
        }
    }

    @Test
    fun `팀과 선수를 팔로우하면 목록에 이름과 함께 나온다`() {
        val t = login("fan@example.com")
        followTeam(t, 42).andExpect { status { isNoContent() } }
        followTeam(t, 49).andExpect { status { isNoContent() } }
        followPlayer(t, 10).andExpect { status { isNoContent() } }

        list(t).andExpect {
            jsonPath("$.teams.length()") { value(2) }
            jsonPath("$.teams[0].id") { value(42) }
            jsonPath("$.teams[0].name") { value("Arsenal") }
            jsonPath("$.teams[1].name") { value("Chelsea") }
            jsonPath("$.players[0].id") { value(10) }
            jsonPath("$.players[0].name") { value("B. Saka") }
        }
    }

    @Test
    fun `같은 대상을 여러 번 팔로우해도 한 번만 저장된다`() {
        val t = login("fan@example.com")
        repeat(3) { followTeam(t, 42).andExpect { status { isNoContent() } } }
        repeat(3) { followPlayer(t, 10).andExpect { status { isNoContent() } } }

        assertEquals(2, follows.count())
        list(t).andExpect { jsonPath("$.teams.length()") { value(1) }; jsonPath("$.players.length()") { value(1) } }
    }

    @Test
    fun `팔로우를 해제하면 목록에서 사라지고 다시 해제해도 괜찮다`() {
        val t = login("fan@example.com")
        followTeam(t, 42); followPlayer(t, 10)

        unfollowTeam(t, 42).andExpect { status { isNoContent() } }
        unfollowTeam(t, 42).andExpect { status { isNoContent() } }
        unfollowPlayer(t, 10).andExpect { status { isNoContent() } }

        list(t).andExpect { jsonPath("$.teams.length()") { value(0) }; jsonPath("$.players.length()") { value(0) } }
    }

    @Test
    fun `없는 팀이나 선수는 404이다`() {
        val t = login("fan@example.com")
        followTeam(t, 9999).andExpect { status { isNotFound() }; jsonPath("$.code") { value("TEAM_NOT_FOUND") } }
        followPlayer(t, 9999).andExpect { status { isNotFound() }; jsonPath("$.code") { value("PLAYER_NOT_FOUND") } }
        assertEquals(0, follows.count())
    }

    @Test
    fun `다른 기기에서 로그인해도 같은 팔로우가 복원되고 다른 회원과는 섞이지 않는다`() {
        val phone = login("fan@example.com")
        followTeam(phone, 42); followPlayer(phone, 10)

        val tablet = login("fan@example.com")   // 새 로그인 = 다른 기기
        list(tablet).andExpect {
            jsonPath("$.teams[0].id") { value(42) }
            jsonPath("$.players[0].id") { value(10) }
        }

        list(login("other@example.com")).andExpect {
            jsonPath("$.teams.length()") { value(0) }
            jsonPath("$.players.length()") { value(0) }
        }
    }

    @Test
    fun `로그인하지 않으면 401이다`() {
        mockMvc.get("/api/members/me/follows").andExpect { status { isUnauthorized() } }
        mockMvc.put("/api/members/me/follows/teams/42").andExpect { status { isUnauthorized() } }
        mockMvc.delete("/api/members/me/follows/teams/42").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `회원이 지워지면 팔로우도 함께 지워진다`() {
        val t = login("fan@example.com")
        followTeam(t, 42)
        sessions.deleteAll()
        members.deleteAll()
        assertEquals(0, follows.count())
    }
}
