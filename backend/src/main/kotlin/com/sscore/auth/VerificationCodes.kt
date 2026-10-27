package com.sscore.auth

import com.sscore.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime

// 이메일 인증코드의 공통 규칙. 가입과 비밀번호 재설정이 같은 규칙을 쓴다.
object VerificationRules {
    val CODE_VALID: Duration = Duration.ofMinutes(10)
    val RESEND_INTERVAL: Duration = Duration.ofSeconds(30)
    const val MAX_RESENDS = 3
    const val MAX_FAILED_ATTEMPTS = 5

    private val random = SecureRandom()

    fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    fun hash(email: String, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$email:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun requireResendInterval(lastSentAt: LocalDateTime, now: LocalDateTime) {
        val nextAllowed = lastSentAt.plus(RESEND_INTERVAL)
        if (now.isBefore(nextAllowed)) {
            val wait = Duration.between(now, nextAllowed).seconds + 1
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON", "${wait}초 후에 다시 요청해 주세요.")
        }
    }

    fun requireResendsLeft(resendCount: Int) {
        if (resendCount >= MAX_RESENDS) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_LIMIT", "재발송 횟수를 초과했습니다. 처음부터 다시 시도해 주세요.")
        }
    }

    fun requireAttemptsLeft(failedAttempts: Int) {
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "입력 횟수를 초과했습니다. 코드를 다시 받아 주세요.")
        }
    }

    fun requireNotExpired(expiresAt: LocalDateTime, now: LocalDateTime) {
        if (now.isAfter(expiresAt)) {
            throw ApiException(HttpStatus.GONE, "CODE_EXPIRED", "인증코드가 만료되었습니다. 코드를 다시 받아 주세요.")
        }
    }

    fun mismatch(failedAttemptsAfter: Int) = ApiException(
        HttpStatus.BAD_REQUEST, "CODE_MISMATCH",
        "인증코드가 올바르지 않습니다. (남은 시도 ${MAX_FAILED_ATTEMPTS - failedAttemptsAfter}회)"
    )
}

// 메일 발송이 실패하면 502로 알린다. 호출한 쪽 트랜잭션은 예외로 롤백되어 요청 기록도 남지 않는다.
@Component
class CodeMailer(private val mail: MailPort) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun send(email: String, code: String, purpose: MailPurpose) {
        try {
            mail.sendVerificationCode(email, code, VerificationRules.CODE_VALID.toMinutes().toInt(), purpose)
        } catch (e: Exception) {
            log.error("인증 메일 발송 실패: to={} purpose={}", email, purpose, e)
            throw ApiException(HttpStatus.BAD_GATEWAY, "MAIL_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }
}
