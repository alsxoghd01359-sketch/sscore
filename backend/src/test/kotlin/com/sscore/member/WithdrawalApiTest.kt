package com.sscore.member

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.auth.PasswordResetRepository
import com.sscore.auth.SignupVerificationRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WithdrawalApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var signups: SignupVerificationRepository
    @Autowired private lateinit var resets: PasswordResetRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var withdrawals: WithdrawalService

    private val email = "fan@example.com"
    private val password = "Passw0rd!"

    private class Tokens(val access: String, val refresh: String)

    @BeforeEach
    fun seed() {
        sessions.deleteAll()
        signups.deleteAll()
        resets.deleteAll()
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

    private fun login(pw: String = password): ResultActionsDsl = mockMvc.post("/api/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = json("email" to email, "password" to pw)
    }

    private fun restore(pw: String = password): ResultActionsDsl = mockMvc.post("/api/auth/restore") {
        contentType = MediaType.APPLICATION_JSON
        content = json("email" to email, "password" to pw)
    }

    private fun tokens(): Tokens {
        val body = login().andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"))
    }

    private fun withdraw(access: String, pw: String = password): ResultActionsDsl =
        mockMvc.post("/api/members/me/withdraw") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = json("password" to pw)
        }

    private fun me(access: String): ResultActionsDsl =
        mockMvc.get("/api/auth/me") { header("Authorization", "Bearer $access") }

    private fun signup(email: String = this.email, nickname: String = "새닉네임"): ResultActionsDsl =
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "password" to password, "nickname" to nickname)
        }

    private fun status() = members.findByEmail(email)?.status

    @Test
    fun `비밀번호가 틀리면 탈퇴되지 않는다`() {
        val t = tokens()
        withdraw(t.access, "Wrong123!").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CURRENT_PASSWORD_MISMATCH") }
        }
        assertEquals(MemberStatus.ACTIVE, status())
        me(t.access).andExpect { status { isOk() } }
    }

    @Test
    fun `탈퇴를 요청하면 모든 기기의 로그인이 즉시 풀리고 로그인할 수 없다`() {
        val phone = tokens()
        val tablet = tokens()

        withdraw(phone.access).andExpect { status { isNoContent() } }

        assertEquals(MemberStatus.WITHDRAWN, status())
        assertNotNull(members.findByEmail(email)!!.withdrawnAt)
        me(phone.access).andExpect { status { isUnauthorized() } }
        me(tablet.access).andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = json("refreshToken" to tablet.refresh)
        }.andExpect { status { isUnauthorized() } }

        login().andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCOUNT_WITHDRAWN") }
        }
    }

    @Test
    fun `유예 중에는 비밀번호가 틀리면 탈퇴 상태를 알려 주지 않는다`() {
        withdraw(tokens().access)
        login("Wrong123!").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `유예 30일 안에는 탈퇴를 취소하고 바로 로그인된다`() {
        withdraw(tokens().access)
        clock.advance(Duration.ofDays(29))

        val body = restore().andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertEquals(MemberStatus.ACTIVE, status())
        assertNull(members.findByEmail(email)!!.withdrawnAt)
        me(JsonPath.read(body, "$.accessToken")).andExpect { status { isOk() } }
        login().andExpect { status { isOk() } }
    }

    @Test
    fun `탈퇴 취소는 비밀번호를 확인하고 탈퇴 상태일 때만 된다`() {
        val t = tokens()
        restore().andExpect { status { isBadRequest() }; jsonPath("$.code") { value("NOT_WITHDRAWN") } }

        withdraw(t.access)
        restore("Wrong123!").andExpect { status { isUnauthorized() } }
        assertEquals(MemberStatus.WITHDRAWN, status())
    }

    @Test
    fun `유예 30일이 지나면 취소할 수 없고 없는 계정처럼 보인다`() {
        withdraw(tokens().access)
        clock.advance(Duration.ofDays(30).plusSeconds(1))

        restore().andExpect { status { isUnauthorized() } }
        login().andExpect { status { isUnauthorized() }; jsonPath("$.code") { value("INVALID_CREDENTIALS") } }
    }

    @Test
    fun `유예 중인 이메일로 가입하려 하면 탈퇴 취소를 안내한다`() {
        withdraw(tokens().access)

        signup().andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ACCOUNT_WITHDRAWN") }
        }
        assertEquals(0, mail.sent.size)
    }

    @Test
    fun `유예 중인 회원에게는 비밀번호 재설정 메일을 보내지 않는다`() {
        withdraw(tokens().access)

        mockMvc.post("/api/auth/password-reset") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email)
        }.andExpect { status { isAccepted() } }
        assertEquals(0, mail.sent.size)
    }

    @Test
    fun `스케줄러는 유예가 끝난 회원만 삭제하고 딸린 데이터도 지운다`() {
        val t = tokens()
        withdraw(t.access)
        signups.save(
            com.sscore.auth.SignupVerification(
                email = email, nickname = "x", passwordHash = "x", codeHash = "x",
                expiresAt = LocalDateTime.now(clock), lastSentAt = LocalDateTime.now(clock), createdAt = LocalDateTime.now(clock)
            )
        )
        members.save(
            Member(
                email = "keep@example.com", password = "x", nickname = "남는회원",
                createdAt = LocalDateTime.now(clock)
            )
        )

        clock.advance(Duration.ofDays(29))
        assertEquals(0, withdrawals.purgeExpired())
        assertNotNull(members.findByEmail(email))

        clock.advance(Duration.ofDays(2))
        assertEquals(1, withdrawals.purgeExpired())
        assertNull(members.findByEmail(email))
        assertNotNull(members.findByEmail("keep@example.com"))
        assertEquals(0, signups.count())
        assertEquals(0, sessions.count())
    }

    @Test
    fun `삭제된 이메일로 다시 가입할 수 있다`() {
        withdraw(tokens().access)
        clock.advance(Duration.ofDays(31))
        withdrawals.purgeExpired()

        signup().andExpect { status { isAccepted() } }
        mockMvc.post("/api/auth/signup/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "code" to mail.lastCodeTo(email))
        }.andExpect { status { isCreated() } }
        assertEquals(MemberStatus.ACTIVE, status())
    }

    @Test
    fun `스케줄러가 돌기 전이라도 유예가 끝난 이메일로 가입을 시작하면 정리하고 진행한다`() {
        withdraw(tokens().access)
        clock.advance(Duration.ofDays(31))

        signup(nickname = "축구팬").andExpect { status { isAccepted() } }
        assertNull(members.findByEmail(email))
    }

    @Test
    fun `로그인하지 않으면 탈퇴를 요청할 수 없다`() {
        mockMvc.post("/api/members/me/withdraw") {
            contentType = MediaType.APPLICATION_JSON
            content = json("password" to password)
        }.andExpect { status { isUnauthorized() } }
    }
}
