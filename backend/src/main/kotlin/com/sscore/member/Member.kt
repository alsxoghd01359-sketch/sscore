package com.sscore.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

enum class MemberRole { USER, ADMIN }

// WITHDRAWN: 탈퇴를 요청하고 30일 유예 중인 회원. 로그인할 수 없고 유예 안에는 복구할 수 있다.
enum class MemberStatus { ACTIVE, WITHDRAWN }

// 이메일 인증을 마친 회원
@Entity
@Table(name = "users")
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userId: Int? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false, length = 12)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: MemberRole = MemberRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.ACTIVE,

    val createdAt: LocalDateTime,

    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null
)

@Repository
interface MemberRepository : JpaRepository<Member, Int> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun findByStatusAndWithdrawnAtBefore(status: MemberStatus, cutoff: LocalDateTime): List<Member>
}
