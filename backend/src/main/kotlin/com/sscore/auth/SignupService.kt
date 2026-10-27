package com.sscore.auth

import com.sscore.admin.AdminAccounts
import com.sscore.common.ApiException
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberRules
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 가입: 요청(코드 발송) → 인증코드 확인 → 회원 생성. 확인이 끝나기 전에는 회원이 아니다.
@Service
class SignupService(
    private val members: MemberRepository,
    private val verifications: SignupVerificationRepository,
    private val mailer: CodeMailer,
    private val adminAccounts: AdminAccounts,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) {
    @Transactional
    fun start(request: SignupRequest): SignupStarted {
        val email = MemberRules.normalizeEmail(request.email)
        val password = request.password.orEmpty()
        val nickname = request.nickname.orEmpty().trim()
        MemberRules.requirePassword(password)
        MemberRules.requireNickname(nickname)

        if (members.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 가입된 이메일입니다.")
        }
        if (members.existsByNickname(nickname)) {
            throw ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다.")
        }

        val now = LocalDateTime.now(clock)
        val code = VerificationRules.newCode()
        val existing = verifications.findByEmail(email)
        if (existing != null) {
            VerificationRules.requireResendInterval(existing.lastSentAt, now)
            existing.nickname = nickname
            existing.passwordHash = passwordEncoder.encode(password)!!
            existing.codeHash = VerificationRules.hash(email, code)
            existing.expiresAt = now.plus(VerificationRules.CODE_VALID)
            existing.failedAttempts = 0
            existing.resendCount = 0
            existing.lastSentAt = now
        } else {
            verifications.save(
                SignupVerification(
                    email = email,
                    nickname = nickname,
                    passwordHash = passwordEncoder.encode(password)!!,
                    codeHash = VerificationRules.hash(email, code),
                    expiresAt = now.plus(VerificationRules.CODE_VALID),
                    lastSentAt = now,
                    createdAt = now
                )
            )
        }
        mailer.send(email, code, MailPurpose.SIGNUP)
        return started(email)
    }

    @Transactional
    fun resend(request: ResendRequest): SignupStarted {
        val email = MemberRules.normalizeEmail(request.email)
        val pending = verifications.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)
        VerificationRules.requireResendsLeft(pending.resendCount)
        VerificationRules.requireResendInterval(pending.lastSentAt, now)

        val code = VerificationRules.newCode()
        pending.codeHash = VerificationRules.hash(email, code)
        pending.expiresAt = now.plus(VerificationRules.CODE_VALID)
        pending.failedAttempts = 0
        pending.resendCount += 1
        pending.lastSentAt = now
        mailer.send(email, code, MailPurpose.SIGNUP)
        return started(email)
    }

    // 실패 횟수는 오류를 던져도 저장되어야 하므로 ApiException에서는 롤백하지 않는다.
    @Transactional(noRollbackFor = [ApiException::class])
    fun verify(request: VerifyRequest): SignupCompleted {
        val email = MemberRules.normalizeEmail(request.email)
        val code = request.code.orEmpty().trim()
        val pending = verifications.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)

        VerificationRules.requireAttemptsLeft(pending.failedAttempts)
        VerificationRules.requireNotExpired(pending.expiresAt, now)
        if (pending.codeHash != VerificationRules.hash(email, code)) {
            pending.failedAttempts += 1
            throw VerificationRules.mismatch(pending.failedAttempts)
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
        if (!MemberRules.isValidNickname(value)) {
            return NicknameAvailability(false, MemberRules.NICKNAME_RULE_MESSAGE)
        }
        if (members.existsByNickname(value)) {
            return NicknameAvailability(false, "이미 사용 중인 닉네임입니다.")
        }
        return NicknameAvailability(true)
    }

    private fun noPending() =
        ApiException(HttpStatus.NOT_FOUND, "NO_PENDING_SIGNUP", "진행 중인 가입 요청이 없습니다. 처음부터 다시 가입해 주세요.")

    private fun started(email: String) =
        SignupStarted(email, VerificationRules.CODE_VALID.seconds, VerificationRules.RESEND_INTERVAL.seconds)
}
