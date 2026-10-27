package com.sscore.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

enum class MailPurpose(val subject: String, val intro: String) {
    SIGNUP("[SScore] 이메일 인증코드", "가입 인증코드"),
    PASSWORD_RESET("[SScore] 비밀번호 재설정 인증코드", "비밀번호 재설정 인증코드")
}

// 메일 발송 경계. 운영은 SMTP, 개발은 로그, 테스트는 가짜로 바뀐다.
interface MailPort {
    fun sendVerificationCode(email: String, code: String, validMinutes: Int, purpose: MailPurpose)
}

class SmtpMailPort(
    private val sender: JavaMailSender,
    private val fromAddress: String
) : MailPort {
    override fun sendVerificationCode(email: String, code: String, validMinutes: Int, purpose: MailPurpose) {
        val message = SimpleMailMessage().apply {
            if (fromAddress.isNotBlank()) setFrom(fromAddress)
            setTo(email)
            subject = purpose.subject
            text = "${purpose.intro}: $code\n${validMinutes}분 안에 앱에서 입력해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요."
        }
        sender.send(message)
    }
}

// SMTP를 설정하지 않은 개발 환경에서는 메일 대신 서버 로그에 코드를 남긴다.
class LoggingMailPort : MailPort {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendVerificationCode(email: String, code: String, validMinutes: Int, purpose: MailPurpose) {
        log.info("[개발용 메일] to={} 종류={} 인증코드={} (유효 {}분)", email, purpose, code, validMinutes)
    }
}

@Configuration
class MailConfig {

    @Bean
    @ConditionalOnProperty(name = ["sscore.mail.enabled"], havingValue = "true")
    fun smtpMailPort(sender: JavaMailSender, @Value("\${sscore.mail.from:}") from: String): MailPort =
        SmtpMailPort(sender, from)

    @Bean
    @ConditionalOnProperty(name = ["sscore.mail.enabled"], havingValue = "false", matchIfMissing = true)
    fun loggingMailPort(): MailPort = LoggingMailPort()
}
