package com.sscore.auth

import com.sscore.admin.AdminAccounts
import com.sscore.common.ApiException
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

// 가입: 요청(코드 발송) → 인증코드 확인 → 회원 생성. 확인이 끝나기 전에는 회원이 아니다.
@Service
class SignupService(
    private val members: MemberRepository,
    private val verifications: SignupVerificationRepository,
    private val mail: MailPort,
    private val adminAccounts: AdminAccounts,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        val CODE_VALID: Duration = Duration.ofMinutes(10)
        val RESEND_INTERVAL: Duration = Duration.ofSeconds(30)
        const val MAX_RESENDS = 3
        const val MAX_FAILED_ATTEMPTS = 5

        private val EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
        private val PASSWORD_REGEX = "^(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}$".toRegex()
        private val NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,12}$".toRegex()
        private val random = SecureRandom()
    }

    @Transactional
    fun start(request: SignupRequest): SignupStarted {
        val email = normalizeEmail(request.email)
        val password = request.password.orEmpty()
        val nickname = request.nickname.orEmpty().trim()
        validatePassword(password)
        validateNickname(nickname)

        if (members.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 가입된 이메일입니다.")
        }
        if (members.existsByNickname(nickname)) {
            throw ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다.")
        }

        val now = LocalDateTime.now(clock)
        val code = newCode()
        val existing = verifications.findByEmail(email)
        if (existing != null) {
            requireResendInterval(existing, now)
            existing.nickname = nickname
            existing.passwordHash = passwordEncoder.encode(password)!!
            existing.codeHash = hash(email, code)
            existing.expiresAt = now.plus(CODE_VALID)
            existing.failedAttempts = 0
            existing.resendCount = 0
            existing.lastSentAt = now
        } else {
            verifications.save(
                SignupVerification(
                    email = email,
                    nickname = nickname,
                    passwordHash = passwordEncoder.encode(password)!!,
                    codeHash = hash(email, code),
                    expiresAt = now.plus(CODE_VALID),
                    lastSentAt = now,
                    createdAt = now
                )
            )
        }
        sendCode(email, code)
        return started(email)
    }

    @Transactional
    fun resend(request: ResendRequest): SignupStarted {
        val email = normalizeEmail(request.email)
        val pending = verifications.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)
        if (pending.resendCount >= MAX_RESENDS) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_LIMIT", "재발송 횟수를 초과했습니다. 처음부터 다시 가입해 주세요.")
        }
        requireResendInterval(pending, now)

        val code = newCode()
        pending.codeHash = hash(email, code)
        pending.expiresAt = now.plus(CODE_VALID)
        pending.failedAttempts = 0
        pending.resendCount += 1
        pending.lastSentAt = now
        sendCode(email, code)
        return started(email)
    }

    // 실패 횟수는 오류를 던져도 저장되어야 하므로 ApiException에서는 롤백하지 않는다.
    @Transactional(noRollbackFor = [ApiException::class])
    fun verify(request: VerifyRequest): SignupCompleted {
        val email = normalizeEmail(request.email)
        val code = request.code.orEmpty().trim()
        val pending = verifications.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)

        if (pending.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "입력 횟수를 초과했습니다. 코드를 다시 받아 주세요.")
        }
        if (now.isAfter(pending.expiresAt)) {
            throw ApiException(HttpStatus.GONE, "CODE_EXPIRED", "인증코드가 만료되었습니다. 코드를 다시 받아 주세요.")
        }
        if (pending.codeHash != hash(email, code)) {
            pending.failedAttempts += 1
            val left = MAX_FAILED_ATTEMPTS - pending.failedAttempts
            throw ApiException(HttpStatus.BAD_REQUEST, "CODE_MISMATCH", "인증코드가 올바르지 않습니다. (남은 시도 ${left}회)")
        }

        if (members.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 가입된 이메일입니다.")
        }
        if (members.existsByNickname(pending.nickname)) {
            throw ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다.")
        }
        val member = members.save(
            Member(
                email = email, password = pending.passwordHash, nickname = pending.nickname,
                role = adminAccounts.roleFor(email), createdAt = now
            )
        )
        verifications.delete(pending)
        return SignupCompleted(member.userId!!, member.email, member.nickname)
    }

    fun nicknameAvailability(nickname: String?): NicknameAvailability {
        val value = nickname.orEmpty().trim()
        if (!NICKNAME_REGEX.matches(value)) {
            return NicknameAvailability(false, nicknameRuleMessage())
        }
        if (members.existsByNickname(value)) {
            return NicknameAvailability(false, "이미 사용 중인 닉네임입니다.")
        }
        return NicknameAvailability(true)
    }

    private fun normalizeEmail(raw: String?): String {
        val email = raw.orEmpty().trim().lowercase()
        if (email.length > 254 || !EMAIL_REGEX.matches(email)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "올바른 이메일 형식이 아닙니다.")
        }
        return email
    }

    private fun validatePassword(password: String) {
        if (!PASSWORD_REGEX.matches(password)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 8~20자이며 특수문자를 포함해야 합니다.")
        }
    }

    private fun validateNickname(nickname: String) {
        if (!NICKNAME_REGEX.matches(nickname)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", nicknameRuleMessage())
        }
    }

    private fun nicknameRuleMessage() = "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다."

    private fun requireResendInterval(pending: SignupVerification, now: LocalDateTime) {
        val nextAllowed = pending.lastSentAt.plus(RESEND_INTERVAL)
        if (now.isBefore(nextAllowed)) {
            val wait = Duration.between(now, nextAllowed).seconds + 1
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON", "${wait}초 후에 다시 요청해 주세요.")
        }
    }

    // 메일 발송이 실패하면 가입 요청도 저장하지 않는다(예외로 롤백). 원인은 서버 로그에 남긴다.
    private fun sendCode(email: String, code: String) {
        try {
            mail.sendVerificationCode(email, code, CODE_VALID.toMinutes().toInt())
        } catch (e: Exception) {
            log.error("인증 메일 발송 실패: to={}", email, e)
            throw ApiException(HttpStatus.BAD_GATEWAY, "MAIL_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }

    private fun noPending() =
        ApiException(HttpStatus.NOT_FOUND, "NO_PENDING_SIGNUP", "진행 중인 가입 요청이 없습니다. 처음부터 다시 가입해 주세요.")

    private fun started(email: String) =
        SignupStarted(email, CODE_VALID.seconds, RESEND_INTERVAL.seconds)

    private fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    private fun hash(email: String, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$email:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
