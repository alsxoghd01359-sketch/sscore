package com.sscore.auth

import com.sscore.common.ApiException
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

// 로그인 세션(기기 하나). 행이 있어야 그 세션의 토큰이 유효하다.
@Entity
@Table(name = "auth_sessions")
class AuthSession(
    @Id
    @Column(length = 36)
    val id: String,

    @Column(name = "member_id", nullable = false)
    val memberId: Int,

    @Column(name = "refresh_hash", nullable = false, length = 64)
    var refreshHash: String,

    @Column(name = "refresh_expires_at", nullable = false)
    var refreshExpiresAt: LocalDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime
)

@Repository
interface AuthSessionRepository : JpaRepository<AuthSession, String> {
    fun findByRefreshHash(refreshHash: String): AuthSession?

    @Modifying
    @Query("delete from AuthSession s where s.memberId = :memberId")
    fun deleteAllByMemberId(memberId: Int): Int
}

data class TokenPair(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long
)

@Service
class SessionService(
    private val sessions: AuthSessionRepository,
    private val members: MemberRepository,
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock
) {
    companion object {
        val ACCESS_VALID: Duration = Duration.ofMinutes(30)
        val REFRESH_VALID: Duration = Duration.ofDays(14)
        private val random = SecureRandom()
    }

    // 로그인: 새 세션과 토큰 한 쌍을 발급한다.
    @Transactional
    fun create(member: Member): TokenPair {
        val now = LocalDateTime.now(clock)
        val refreshToken = newRefreshToken()
        val session = sessions.save(
            AuthSession(
                id = UUID.randomUUID().toString(),
                memberId = member.userId!!,
                refreshHash = hash(refreshToken),
                refreshExpiresAt = now.plus(REFRESH_VALID),
                createdAt = now,
                lastUsedAt = now
            )
        )
        return pair(member, session.id, refreshToken)
    }

    // 갱신 토큰으로 새 한 쌍을 받는다. 갱신 토큰은 쓸 때마다 새로 바뀐다(이전 것은 폐기).
    @Transactional
    fun refresh(refreshToken: String): TokenPair {
        val session = sessions.findByRefreshHash(hash(refreshToken)) ?: throw invalidRefresh()
        val now = LocalDateTime.now(clock)
        if (now.isAfter(session.refreshExpiresAt)) {
            sessions.delete(session)
            throw invalidRefresh()
        }
        val member = members.findById(session.memberId).orElse(null)
        if (member == null) {
            sessions.delete(session)
            throw invalidRefresh()
        }
        val next = newRefreshToken()
        session.refreshHash = hash(next)
        session.refreshExpiresAt = now.plus(REFRESH_VALID)
        session.lastUsedAt = now
        return pair(member, session.id, next)
    }

    @Transactional
    fun revoke(sessionId: String) {
        sessions.deleteById(sessionId)
    }

    // 모든 기기에서 로그아웃 (비밀번호 변경, 탈퇴에서 사용)
    @Transactional
    fun revokeAll(memberId: Int) {
        sessions.deleteAllByMemberId(memberId)
    }

    @Transactional(readOnly = true)
    fun isActive(sessionId: String, memberId: Int): Boolean {
        val session = sessions.findById(sessionId).orElse(null) ?: return false
        return session.memberId == memberId && !LocalDateTime.now(clock).isAfter(session.refreshExpiresAt)
    }

    private fun pair(member: Member, sessionId: String, refreshToken: String): TokenPair {
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder()
            .subject(member.userId.toString())
            .claim("role", member.role.name)
            .claim("sid", sessionId)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ACCESS_VALID))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val access = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return TokenPair(access, ACCESS_VALID.seconds, refreshToken, REFRESH_VALID.seconds)
    }

    private fun invalidRefresh() =
        ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "로그인이 만료되었습니다. 다시 로그인해 주세요.")

    private fun newRefreshToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
