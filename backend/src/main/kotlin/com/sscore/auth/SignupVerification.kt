package com.sscore.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

// 인증코드 확인을 기다리는 가입 요청. 인증이 끝나기 전에는 회원이 아니다.
@Entity
@Table(name = "signup_verifications")
class SignupVerification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false, length = 12)
    var nickname: String,

    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,

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
interface SignupVerificationRepository : JpaRepository<SignupVerification, Long> {
    fun findByEmail(email: String): SignupVerification?
}
