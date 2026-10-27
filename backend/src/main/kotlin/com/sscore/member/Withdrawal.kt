package com.sscore.member

import com.sscore.auth.PasswordResetRepository
import com.sscore.auth.SessionService
import com.sscore.auth.SignupVerificationRepository
import com.sscore.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 탈퇴 유예 규칙: 탈퇴를 요청하면 30일 동안 복구할 수 있고, 그 뒤에는 회원 데이터를 영구 삭제한다.
object WithdrawalPolicy {
    val GRACE: Duration = Duration.ofDays(30)
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun deadline(member: Member): LocalDateTime? = member.withdrawnAt?.plus(GRACE)

    fun isWithdrawn(member: Member) = member.status == MemberStatus.WITHDRAWN

    // 유예가 끝났다면(아직 삭제 스케줄러가 돌지 않았더라도) 없는 회원처럼 다룬다.
    fun isExpired(member: Member, now: LocalDateTime): Boolean {
        val deadline = deadline(member) ?: return false
        return isWithdrawn(member) && now.isAfter(deadline)
    }

    fun deadlineText(member: Member): String = deadline(member)?.format(DATE).orEmpty()
}

data class WithdrawRequest(val password: String? = null)

@Service
class WithdrawalService(
    private val members: MemberRepository,
    private val sessions: SessionService,
    private val signups: SignupVerificationRepository,
    private val resets: PasswordResetRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: java.time.Clock
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 비밀번호를 확인한 뒤 즉시 탈퇴 유예 상태로 바꾸고 모든 기기의 로그인을 푼다.
    @Transactional
    fun withdraw(memberId: Int, request: WithdrawRequest) {
        val member = members.findById(memberId).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.")
        }
        if (!passwordEncoder.matches(request.password.orEmpty(), member.password)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", "비밀번호가 올바르지 않습니다.")
        }
        member.status = MemberStatus.WITHDRAWN
        member.withdrawnAt = LocalDateTime.now(clock)
        sessions.revokeAll(memberId)
    }

    // 유예가 끝난 회원과 딸린 데이터를 영구 삭제한다. 삭제한 수를 돌려준다.
    @Transactional
    fun purgeExpired(): Int {
        val cutoff = LocalDateTime.now(clock).minus(WithdrawalPolicy.GRACE)
        val expired = members.findByStatusAndWithdrawnAtBefore(MemberStatus.WITHDRAWN, cutoff)
        expired.forEach { purge(it) }
        if (expired.isNotEmpty()) log.info("탈퇴 유예가 끝난 회원 {}명을 삭제했습니다.", expired.size)
        return expired.size
    }

    // 가입·재설정 중 같은 이메일의 유예가 끝난 회원이 걸리면 그 자리에서 정리한다.
    @Transactional
    fun purgeIfExpired(email: String) {
        val member = members.findByEmail(email) ?: return
        if (WithdrawalPolicy.isExpired(member, LocalDateTime.now(clock))) purge(member)
    }

    private fun purge(member: Member) {
        // 로그인 세션은 DB의 ON DELETE CASCADE로 함께 지워진다. 팔로우·알림 설정 같은 이후 테이블도 같은 방식으로 만든다.
        signups.deleteByEmail(member.email)
        resets.deleteByEmail(member.email)
        members.delete(member)
        members.flush()
    }
}

// 매일 새벽 4시에 유예가 끝난 회원을 삭제한다.
@Component
class WithdrawalPurgeScheduler(private val withdrawals: WithdrawalService) {
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun purge() {
        withdrawals.purgeExpired()
    }
}

@RestController
@RequestMapping("/api/members/me")
class WithdrawalController(private val withdrawals: WithdrawalService) {

    @PostMapping("/withdraw")
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: WithdrawRequest): ResponseEntity<Void> {
        withdrawals.withdraw(jwt.subject.toInt(), request)
        return ResponseEntity.noContent().build()
    }
}
