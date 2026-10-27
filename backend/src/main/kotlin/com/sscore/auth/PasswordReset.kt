package com.sscore.auth

import com.sscore.common.ApiException
import com.sscore.member.MemberRepository
import com.sscore.member.MemberStatus
import com.sscore.member.MemberRules
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDateTime

@Entity
@Table(name = "password_resets")
class PasswordReset(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0,

    @Column(name = "resend_count", nullable = false)
    var resendCount: Int = 0,

    @Column(name = "last_sent_at", nullable = false)
    var lastSentAt: LocalDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime
)

@Repository
interface PasswordResetRepository : JpaRepository<PasswordReset, Long> {
    fun findByEmail(email: String): PasswordReset?
    fun deleteByEmail(email: String): Long
}

data class PasswordResetRequest(val email: String? = null)

data class PasswordResetConfirm(val email: String? = null, val code: String? = null, val newPassword: String? = null)

// 비밀번호 재설정: 코드 요청 → 코드와 새 비밀번호 확인. 성공하면 모든 기기의 로그인이 풀린다.
@Service
class PasswordResetService(
    private val members: MemberRepository,
    private val resets: PasswordResetRepository,
    private val sessions: SessionService,
    private val mailer: CodeMailer,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) {
    // 가입 여부는 응답으로 드러내지 않는다. 회원이 아니면 메일을 보내지 않고 같은 응답을 준다.
    @Transactional
    fun start(request: PasswordResetRequest): SignupStarted {
        val email = MemberRules.normalizeEmail(request.email)
        // 탈퇴 유예 중인 회원도 가입하지 않은 이메일처럼 다룬다(메일을 보내지 않는다)
        val member = members.findByEmail(email)
        if (member == null || member.status != MemberStatus.ACTIVE) return started(email)

        val now = LocalDateTime.now(clock)
        val code = VerificationRules.newCode()
        val existing = resets.findByEmail(email)
        if (existing != null) {
            VerificationRules.requireResendInterval(existing.lastSentAt, now)
            existing.codeHash = VerificationRules.hash(email, code)
            existing.expiresAt = now.plus(VerificationRules.CODE_VALID)
            existing.failedAttempts = 0
            existing.resendCount = 0
            existing.lastSentAt = now
        } else {
            resets.save(
                PasswordReset(
                    email = email, codeHash = VerificationRules.hash(email, code),
                    expiresAt = now.plus(VerificationRules.CODE_VALID), lastSentAt = now, createdAt = now
                )
            )
        }
        mailer.send(email, code, MailPurpose.PASSWORD_RESET)
        return started(email)
    }

    @Transactional
    fun resend(request: ResendRequest): SignupStarted {
        val email = MemberRules.normalizeEmail(request.email)
        val pending = resets.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)
        VerificationRules.requireResendsLeft(pending.resendCount)
        VerificationRules.requireResendInterval(pending.lastSentAt, now)

        val code = VerificationRules.newCode()
        pending.codeHash = VerificationRules.hash(email, code)
        pending.expiresAt = now.plus(VerificationRules.CODE_VALID)
        pending.failedAttempts = 0
        pending.resendCount += 1
        pending.lastSentAt = now
        mailer.send(email, code, MailPurpose.PASSWORD_RESET)
        return started(email)
    }

    @Transactional(noRollbackFor = [ApiException::class])
    fun confirm(request: PasswordResetConfirm) {
        val email = MemberRules.normalizeEmail(request.email)
        val newPassword = request.newPassword.orEmpty()
        MemberRules.requirePassword(newPassword)

        val pending = resets.findByEmail(email) ?: throw noPending()
        val now = LocalDateTime.now(clock)
        VerificationRules.requireAttemptsLeft(pending.failedAttempts)
        VerificationRules.requireNotExpired(pending.expiresAt, now)
        if (pending.codeHash != VerificationRules.hash(email, request.code.orEmpty().trim())) {
            pending.failedAttempts += 1
            throw VerificationRules.mismatch(pending.failedAttempts)
        }

        val member = members.findByEmail(email)?.takeIf { it.status == MemberStatus.ACTIVE } ?: throw noPending()
        member.password = passwordEncoder.encode(newPassword)!!
        resets.delete(pending)
        sessions.revokeAll(member.userId!!)
    }

    private fun noPending() =
        ApiException(HttpStatus.NOT_FOUND, "NO_PENDING_RESET", "진행 중인 비밀번호 재설정 요청이 없습니다. 처음부터 다시 시도해 주세요.")

    private fun started(email: String) =
        SignupStarted(email, VerificationRules.CODE_VALID.seconds, VerificationRules.RESEND_INTERVAL.seconds)
}

@RestController
@RequestMapping("/api/auth/password-reset")
class PasswordResetController(private val service: PasswordResetService) {

    @PostMapping
    fun start(@RequestBody request: PasswordResetRequest): ResponseEntity<SignupStarted> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(request))

    @PostMapping("/resend")
    fun resend(@RequestBody request: ResendRequest): ResponseEntity<SignupStarted> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resend(request))

    @PostMapping("/confirm")
    fun confirm(@RequestBody request: PasswordResetConfirm): ResponseEntity<Void> {
        service.confirm(request)
        return ResponseEntity.noContent().build()
    }
}
