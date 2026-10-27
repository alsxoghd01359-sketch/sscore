package com.sscore.member

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.auth.MailPurpose
import com.sscore.auth.PasswordResetRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var resets: PasswordResetRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val email = "fan@example.com"
    private val password = "Passw0rd!"
    private val newPassword = "N3wPassw0rd!"

    private class Tokens(val access: String, val refresh: String)

    @BeforeEach
    fun seed() {
        sessions.deleteAll()
        resets.deleteAll()
        members.deleteAll()
        member(email, "축구팬")
    }

    private fun member(email: String, nickname: String) = members.save(
        Member(
            email = email, password = passwordEncoder.encode(password)!!,
            nickname = nickname, createdAt = LocalDateTime.now(clock)
        )
    )

    private fun json(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { "\"${it.first}\":\"${it.second}\"" }

    private fun login(pw: String = password): ResultActionsDsl = mockMvc.post("/api/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = json("email" to email, "password" to pw)
    }

    private fun tokens(pw: String = password): Tokens {
        val body = login(pw).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"))
    }

    private fun me(access: String): ResultActionsDsl =
        mockMvc.get("/api/auth/me") { header("Authorization", "Bearer $access") }

    private fun refresh(token: String): ResultActionsDsl = mockMvc.post("/api/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = json("refreshToken" to token)
    }

    private fun changePassword(access: String, current: String, next: String): ResultActionsDsl =
        mockMvc.post("/api/members/me/password") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = json("currentPassword" to current, "newPassword" to next)
        }

    private fun changeNickname(access: String, nickname: String): ResultActionsDsl =
        mockMvc.patch("/api/members/me/nickname") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = json("nickname" to nickname)
        }

    private fun requestReset(email: String = this.email): ResultActionsDsl =
        mockMvc.post("/api/auth/password-reset") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email)
        }

    private fun resendReset(): ResultActionsDsl = mockMvc.post("/api/auth/password-reset/resend") {
        contentType = MediaType.APPLICATION_JSON
        content = json("email" to email)
    }

    private fun confirmReset(code: String, pw: String = newPassword): ResultActionsDsl =
        mockMvc.post("/api/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "code" to code, "newPassword" to pw)
        }

    // ── 닉네임 변경 ─────────────────────────────────────────

    @Test
    fun `닉네임을 바꿀 수 있다`() {
        val t = tokens()
        changeNickname(t.access, "새닉네임").andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("새닉네임") }
        }
        assertEquals("새닉네임", members.findByEmail(email)!!.nickname)
        me(t.access).andExpect { jsonPath("$.nickname") { value("새닉네임") } }
    }

    @Test
    fun `닉네임 규칙은 가입과 같다`() {
        val t = tokens()
        changeNickname(t.access, "a").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_NICKNAME") } }
        changeNickname(t.access, "특수!문자").andExpect { status { isBadRequest() } }
        assertEquals("축구팬", members.findByEmail(email)!!.nickname)
    }

    @Test
    fun `다른 회원이 쓰는 닉네임은 거절되고 내 닉네임을 그대로 보내면 성공한다`() {
        member("other@example.com", "남의닉네임")
        val t = tokens()

        changeNickname(t.access, "남의닉네임").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("NICKNAME_TAKEN") }
        }
        changeNickname(t.access, "축구팬").andExpect { status { isOk() } }
    }

    @Test
    fun `로그인하지 않으면 프로필을 바꿀 수 없다`() {
        mockMvc.patch("/api/members/me/nickname") {
            contentType = MediaType.APPLICATION_JSON
            content = json("nickname" to "새닉네임")
        }.andExpect { status { isUnauthorized() } }
    }

    // ── 비밀번호 변경 ───────────────────────────────────────

    @Test
    fun `현재 비밀번호를 확인해야 바꿀 수 있고 다른 기기 로그인은 풀리고 이 기기는 유지된다`() {
        val phone = tokens()
        val tablet = tokens()

        changePassword(phone.access, password, newPassword).andExpect { status { isNoContent() } }

        me(phone.access).andExpect { status { isOk() } }
        me(tablet.access).andExpect { status { isUnauthorized() } }
        refresh(tablet.refresh).andExpect { status { isUnauthorized() } }
        refresh(phone.refresh).andExpect { status { isOk() } }

        login(password).andExpect { status { isUnauthorized() } }
        login(newPassword).andExpect { status { isOk() } }
        assertTrue(members.findByEmail(email)!!.password.startsWith("\$2"))
    }

    @Test
    fun `현재 비밀번호가 틀리면 바뀌지 않는다`() {
        val t = tokens()
        changePassword(t.access, "Wrong123!", newPassword).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CURRENT_PASSWORD_MISMATCH") }
        }
        login(password).andExpect { status { isOk() } }
    }

    @Test
    fun `새 비밀번호는 규칙을 지켜야 하고 현재와 달라야 한다`() {
        val t = tokens()
        changePassword(t.access, password, "short1!").andExpect { jsonPath("$.code") { value("INVALID_PASSWORD") } }
        changePassword(t.access, password, "NoSpecialChar1").andExpect { status { isBadRequest() } }
        changePassword(t.access, password, password).andExpect { jsonPath("$.code") { value("SAME_PASSWORD") } }
    }

    // ── 비밀번호 재설정 ─────────────────────────────────────

    @Test
    fun `재설정 코드를 입력하면 새 비밀번호로 바뀌고 모든 기기의 로그인이 풀린다`() {
        val phone = tokens()
        val tablet = tokens()

        requestReset().andExpect { status { isAccepted() } }
        assertEquals(1, mail.sent.size)
        assertEquals(MailPurpose.PASSWORD_RESET, mail.sent.single().purpose)
        assertTrue(mail.sent.single().code.matches("\\d{6}".toRegex()))

        confirmReset(mail.lastCodeTo(email)).andExpect { status { isNoContent() } }

        me(phone.access).andExpect { status { isUnauthorized() } }
        me(tablet.access).andExpect { status { isUnauthorized() } }
        refresh(phone.refresh).andExpect { status { isUnauthorized() } }
        login(password).andExpect { status { isUnauthorized() } }
        login(newPassword).andExpect { status { isOk() } }
    }

    @Test
    fun `가입하지 않은 이메일은 메일을 보내지 않고 같은 응답을 준다`() {
        requestReset("nobody@example.com").andExpect { status { isAccepted() } }
        assertEquals(0, mail.sent.size)
        assertEquals(0, resets.count())
    }

    @Test
    fun `재설정 코드는 10분 후 만료된다`() {
        requestReset()
        val code = mail.lastCodeTo(email)
        clock.advance(Duration.ofMinutes(10).plusSeconds(1))

        confirmReset(code).andExpect {
            status { isGone() }
            jsonPath("$.code") { value("CODE_EXPIRED") }
        }
        login(password).andExpect { status { isOk() } }
    }

    @Test
    fun `재설정 재발송은 30초 간격과 3회로 제한된다`() {
        requestReset()
        resendReset().andExpect { status { isTooManyRequests() }; jsonPath("$.code") { value("RESEND_TOO_SOON") } }

        repeat(3) {
            clock.advance(Duration.ofSeconds(31))
            resendReset().andExpect { status { isAccepted() } }
        }
        clock.advance(Duration.ofSeconds(31))
        resendReset().andExpect { status { isTooManyRequests() }; jsonPath("$.code") { value("RESEND_LIMIT") } }
        confirmReset(mail.lastCodeTo(email)).andExpect { status { isNoContent() } }
    }

    @Test
    fun `틀린 재설정 코드를 5번 입력하면 올바른 코드도 거절된다`() {
        requestReset()
        val code = mail.lastCodeTo(email)
        val wrong = if (code == "000000") "111111" else "000000"

        repeat(5) { confirmReset(wrong).andExpect { jsonPath("$.code") { value("CODE_MISMATCH") } } }
        confirmReset(code).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("TOO_MANY_ATTEMPTS") }
        }
        login(password).andExpect { status { isOk() } }
    }

    @Test
    fun `새 비밀번호가 규칙에 맞지 않으면 코드를 소모하지 않고 거절된다`() {
        requestReset()
        val code = mail.lastCodeTo(email)

        confirmReset(code, pw = "short1!").andExpect { jsonPath("$.code") { value("INVALID_PASSWORD") } }
        confirmReset(code).andExpect { status { isNoContent() } }
    }

    @Test
    fun `진행 중인 재설정이 없으면 확인과 재발송은 404이다`() {
        confirmReset("123456").andExpect { status { isNotFound() } }
        resendReset().andExpect { status { isNotFound() } }
    }

    @Test
    fun `재설정 메일 발송이 실패하면 502이고 요청 기록도 남지 않는다`() {
        mail.failing = true
        requestReset().andExpect { status { isBadGateway() }; jsonPath("$.code") { value("MAIL_SEND_FAILED") } }
        assertEquals(0, resets.count())
    }
}
