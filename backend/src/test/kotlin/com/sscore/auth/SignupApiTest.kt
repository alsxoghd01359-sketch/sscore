package com.sscore.auth

import com.sscore.member.MemberRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignupApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var pendings: SignupVerificationRepository

    private val email = "fan@example.com"
    private val password = "Passw0rd!"
    private val nickname = "축구팬"

    @BeforeEach
    fun clean() {
        members.deleteAll()
        pendings.deleteAll()
    }

    private fun json(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { "\"${it.first}\":\"${it.second}\"" }

    private fun signup(
        email: String = this.email, password: String = this.password, nickname: String = this.nickname
    ): ResultActionsDsl = mockMvc.post("/api/auth/signup") {
        contentType = MediaType.APPLICATION_JSON
        content = json("email" to email, "password" to password, "nickname" to nickname)
    }

    private fun verify(code: String, email: String = this.email): ResultActionsDsl =
        mockMvc.post("/api/auth/signup/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "code" to code)
        }

    private fun resend(email: String = this.email): ResultActionsDsl =
        mockMvc.post("/api/auth/signup/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email)
        }

    @Test
    fun `가입을 요청하면 6자리 인증코드가 메일로 가고 아직 회원은 아니다`() {
        signup().andExpect { status { isAccepted() } }

        assertEquals(1, mail.sent.size)
        assertTrue(mail.sent.single().code.matches("\\d{6}".toRegex()))
        assertEquals(0, members.count())
    }

    @Test
    fun `올바른 코드를 입력하면 회원이 생기고 비밀번호는 해시로 저장된다`() {
        signup()
        verify(mail.lastCodeTo(email)).andExpect {
            status { isCreated() }
            jsonPath("$.email") { value(email) }
            jsonPath("$.nickname") { value(nickname) }
        }

        val member = members.findByEmail(email)!!
        assertNotEquals(password, member.password)
        assertTrue(member.password.startsWith("\$2"))
        assertEquals(0, pendings.count())
    }

    @Test
    fun `인증 전에는 로그인할 수 없다`() {
        signup()
        mockMvc.post("/api/user/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "password" to password)
        }.andExpect { content { string("실패") } }
    }

    @Test
    fun `코드는 10분 후 만료된다`() {
        signup()
        val code = mail.lastCodeTo(email)
        clock.advance(Duration.ofMinutes(10).plusSeconds(1))

        verify(code).andExpect {
            status { isGone() }
            jsonPath("$.code") { value("CODE_EXPIRED") }
        }
        assertEquals(0, members.count())
    }

    @Test
    fun `재발송은 30초 간격과 3회까지만 되고 새 코드가 나가면 이전 코드는 쓸 수 없다`() {
        signup()
        val first = mail.lastCodeTo(email)

        resend().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("RESEND_TOO_SOON") }
        }

        repeat(3) {
            clock.advance(Duration.ofSeconds(31))
            resend().andExpect { status { isAccepted() } }
        }
        clock.advance(Duration.ofSeconds(31))
        resend().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("RESEND_LIMIT") }
        }

        val latest = mail.lastCodeTo(email)
        if (latest != first) {
            verify(first).andExpect { jsonPath("$.code") { value("CODE_MISMATCH") } }
        }
        verify(latest).andExpect { status { isCreated() } }
    }

    @Test
    fun `틀린 코드를 5번 입력하면 올바른 코드도 거절된다`() {
        signup()
        val code = mail.lastCodeTo(email)
        val wrong = if (code == "000000") "111111" else "000000"

        repeat(5) {
            verify(wrong).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("CODE_MISMATCH") }
            }
        }
        verify(code).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("TOO_MANY_ATTEMPTS") }
        }
        assertEquals(0, members.count())
    }

    @Test
    fun `이미 가입된 이메일은 거절된다`() {
        signup()
        verify(mail.lastCodeTo(email))

        signup(nickname = "다른닉네임").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_TAKEN") }
        }
    }

    @Test
    fun `이미 쓰는 닉네임은 거절된다`() {
        signup()
        verify(mail.lastCodeTo(email))

        signup(email = "other@example.com").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("NICKNAME_TAKEN") }
        }
        mockMvc.get("/api/auth/nickname-available?nickname=$nickname").andExpect {
            jsonPath("$.available") { value(false) }
        }
    }

    @Test
    fun `닉네임 규칙과 비밀번호 규칙과 이메일 형식을 검사한다`() {
        signup(nickname = "a").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_NICKNAME") } }
        signup(nickname = "열세글자를넘는닉네임입니다닉").andExpect { status { isBadRequest() } }
        signup(nickname = "특수!문자").andExpect { status { isBadRequest() } }
        signup(password = "short1!").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_PASSWORD") } }
        signup(password = "NoSpecialChar1").andExpect { status { isBadRequest() } }
        signup(email = "not-an-email").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_EMAIL") } }
        assertEquals(0, mail.sent.size)
    }

    @Test
    fun `닉네임 사용 가능 여부를 확인할 수 있다`() {
        mockMvc.get("/api/auth/nickname-available?nickname=새닉네임").andExpect {
            jsonPath("$.available") { value(true) }
        }
        mockMvc.get("/api/auth/nickname-available?nickname=!").andExpect {
            jsonPath("$.available") { value(false) }
        }
    }

    @Test
    fun `메일 발송이 실패하면 502를 돌려주고 가입 요청도 남기지 않는다`() {
        mail.failing = true

        signup().andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("MAIL_SEND_FAILED") }
        }
        assertEquals(0, pendings.count())
    }

    @Test
    fun `진행 중인 가입이 없으면 인증과 재발송은 404이다`() {
        verify("123456").andExpect { status { isNotFound() } }
        resend().andExpect { status { isNotFound() } }
    }
}
