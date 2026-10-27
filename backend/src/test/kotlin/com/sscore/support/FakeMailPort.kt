package com.sscore.support

import com.sscore.auth.MailPort
import com.sscore.auth.MailPurpose
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 실제로 메일을 보내지 않고, 나간 인증코드를 테스트가 확인할 수 있게 모아 둔다.
class FakeMailPort : MailPort {
    data class Sent(val email: String, val code: String, val purpose: MailPurpose)

    val sent = mutableListOf<Sent>()

    // true면 발송이 실패한 것처럼 예외를 던진다 (SMTP 인증 실패 등)
    var failing = false

    override fun sendVerificationCode(email: String, code: String, validMinutes: Int, purpose: MailPurpose) {
        if (failing) throw IllegalStateException("SMTP 오류")
        sent += Sent(email, code, purpose)
    }

    fun lastCodeTo(email: String): String = sent.last { it.email == email }.code

    fun clear() {
        sent.clear()
        failing = false
    }
}

@TestConfiguration
class FakeMailConfig {
    @Bean
    @Primary
    fun fakeMailPort(): FakeMailPort = FakeMailPort()
}
