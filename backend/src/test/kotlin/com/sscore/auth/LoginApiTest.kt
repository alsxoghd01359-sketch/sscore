package com.sscore.auth

import com.jayway.jsonpath.JsonPath
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LoginApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val email = "fan@example.com"
    private val password = "Passw0rd!"

    private class Tokens(val access: String, val refresh: String)

    @BeforeEach
    fun seedMember() {
        sessions.deleteAll()
        members.deleteAll()
        members.save(
            Member(
                email = email, password = passwordEncoder.encode(password)!!,
                nickname = "축구팬", createdAt = LocalDateTime.now(clock)
            )
        )
    }

    private fun json(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { "\"${it.first}\":\"${it.second}\"" }

    private fun login(email: String = this.email, password: String = this.password): ResultActionsDsl =
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "password" to password)
        }

    private fun loginTokens(): Tokens {
        val body = login().andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"))
    }

    private fun refresh(token: String): ResultActionsDsl = mockMvc.post("/api/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = json("refreshToken" to token)
    }

    private fun me(access: String?): ResultActionsDsl = mockMvc.get("/api/auth/me") {
        if (access != null) header("Authorization", "Bearer $access")
    }

    private fun logout(access: String): ResultActionsDsl = mockMvc.post("/api/auth/logout") {
        header("Authorization", "Bearer $access")
    }

    @Test
    fun `로그인하면 토큰이 발급되고 회원 정보를 볼 수 있다`() {
        login().andExpect {
            status { isOk() }
            jsonPath("$.accessExpiresInSeconds") { value(1800) }
            jsonPath("$.refreshExpiresInSeconds") { value(14 * 24 * 3600) }
            jsonPath("$.member.email") { value(email) }
            jsonPath("$.member.nickname") { value("축구팬") }
            jsonPath("$.member.role") { value("USER") }
        }
        val tokens = loginTokens()
        me(tokens.access).andExpect {
            status { isOk() }
            jsonPath("$.email") { value(email) }
        }
    }

    @Test
    fun `이메일과 비밀번호 중 어느 쪽이 틀려도 같은 응답이다`() {
        val wrongPassword = login(password = "Wrong123!").andExpect { status { isUnauthorized() } }
            .andReturn().response.contentAsString
        val unknownEmail = login(email = "nobody@example.com").andExpect { status { isUnauthorized() } }
            .andReturn().response.contentAsString

        assertEquals(wrongPassword, unknownEmail)
        assertEquals("INVALID_CREDENTIALS", JsonPath.read<String>(wrongPassword, "$.code"))
    }

    @Test
    fun `로그인 정보가 없으면 개인화 주소는 401이고 조회는 게스트도 된다`() {
        me(null).andExpect { status { isUnauthorized() } }
        me("not-a-token").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/soccer/matches?date=2026-03-01").andExpect { status { isOk() } }
    }

    @Test
    fun `액세스 토큰은 30분 뒤 만료되고 갱신 토큰으로 새 토큰을 받는다`() {
        val tokens = loginTokens()
        clock.advance(Duration.ofMinutes(29))
        me(tokens.access).andExpect { status { isOk() } }

        clock.advance(Duration.ofMinutes(2))
        me(tokens.access).andExpect { status { isUnauthorized() } }

        val body = refresh(tokens.refresh).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val newAccess = JsonPath.read<String>(body, "$.accessToken")
        assertNotEquals(tokens.access, newAccess)
        me(newAccess).andExpect { status { isOk() } }
    }

    @Test
    fun `갱신 토큰은 쓸 때마다 바뀌고 이전 토큰은 쓸 수 없다`() {
        val tokens = loginTokens()
        val body = refresh(tokens.refresh).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val next = JsonPath.read<String>(body, "$.refreshToken")

        refresh(tokens.refresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
        refresh(next).andExpect { status { isOk() } }
    }

    @Test
    fun `갱신 토큰은 14일 뒤 만료된다`() {
        val tokens = loginTokens()
        clock.advance(Duration.ofDays(13))
        // 13일째에 한 번 쓰면 만료가 다시 14일로 늘어난다
        val renewed = refresh(tokens.refresh).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val refresh2 = JsonPath.read<String>(renewed, "$.refreshToken")

        clock.advance(Duration.ofDays(14).plusSeconds(1))
        refresh(refresh2).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `로그아웃하면 이 기기의 액세스 토큰과 갱신 토큰이 즉시 무효가 된다`() {
        val tokens = loginTokens()
        logout(tokens.access).andExpect { status { isNoContent() } }

        me(tokens.access).andExpect { status { isUnauthorized() } }
        refresh(tokens.refresh).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `다른 기기의 로그인은 로그아웃해도 유지된다`() {
        val phone = loginTokens()
        val tablet = loginTokens()
        logout(phone.access).andExpect { status { isNoContent() } }

        me(tablet.access).andExpect { status { isOk() } }
    }

    @Test
    fun `인증을 마치지 않은 가입은 로그인할 수 없다`() {
        login(email = "pending@example.com").andExpect { status { isUnauthorized() } }
    }
}
