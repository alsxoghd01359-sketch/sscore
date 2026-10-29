package com.sscore.auth

import com.sscore.common.ApiException
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberStatus
import com.sscore.member.WithdrawalPolicy
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val email: String? = null, val password: String? = null)

data class RefreshRequest(val refreshToken: String? = null)

data class MemberSummary(val memberId: Int, val email: String, val nickname: String, val role: String, val supportedTeamId: Int? = null)

data class LoginResponse(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long,
    val member: MemberSummary
)

fun Member.toSummary() = MemberSummary(userId!!, email, nickname, role.name, supportedTeamId)

@Service
class LoginService(
    private val members: MemberRepository,
    private val sessions: SessionService,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) {
    companion object {
        // 없는 이메일에도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부를 알 수 없게 한다.
        private const val DUMMY_HASH = "\$2a\$10\$7EqJtq98hPqEX7fNZaFWoOhi5BWgxTSeYgyDj5rJq6VzK5E2xV1uC"
    }

    fun login(request: LoginRequest): LoginResponse {
        val email = request.email.orEmpty().trim().lowercase()
        val member = members.findByEmail(email)
        val matches = passwordEncoder.matches(request.password.orEmpty(), member?.password ?: DUMMY_HASH)
        // 이메일/비밀번호 중 어느 쪽이 틀렸는지 알려 주지 않는다.
        if (member == null || !matches || WithdrawalPolicy.isExpired(member, LocalDateTime.now(clock))) {
            throw invalidCredentials()
        }
        if (member.status == MemberStatus.WITHDRAWN) {
            // 비밀번호까지 맞은 경우에만 탈퇴 처리 중이라는 사실을 알려 준다
            throw ApiException(
                HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN",
                "탈퇴 처리 중인 계정입니다. ${WithdrawalPolicy.deadlineText(member)}까지 탈퇴를 취소할 수 있습니다."
            )
        }
        return issue(member)
    }

    // 유예 기간 안에 비밀번호를 다시 확인하고 탈퇴를 취소한다. 취소하면 바로 로그인된다.
    @Transactional
    fun restore(request: LoginRequest): LoginResponse {
        val email = request.email.orEmpty().trim().lowercase()
        val member = members.findByEmail(email)
        val matches = passwordEncoder.matches(request.password.orEmpty(), member?.password ?: DUMMY_HASH)
        if (member == null || !matches || WithdrawalPolicy.isExpired(member, LocalDateTime.now(clock))) {
            throw invalidCredentials()
        }
        if (member.status != MemberStatus.WITHDRAWN) {
            throw ApiException(HttpStatus.BAD_REQUEST, "NOT_WITHDRAWN", "탈퇴 처리 중인 계정이 아닙니다.")
        }
        member.status = MemberStatus.ACTIVE
        member.withdrawnAt = null
        return issue(member)
    }

    private fun invalidCredentials() =
        ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")

    private fun issue(member: com.sscore.member.Member): LoginResponse {
        val tokens = sessions.create(member)
        return LoginResponse(
            tokens.accessToken, tokens.accessExpiresInSeconds,
            tokens.refreshToken, tokens.refreshExpiresInSeconds, member.toSummary()
        )
    }

    fun refresh(request: RefreshRequest): TokenPair {
        val token = request.refreshToken.orEmpty()
        if (token.isBlank()) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "로그인이 만료되었습니다. 다시 로그인해 주세요.")
        }
        return sessions.refresh(token)
    }
}

@RestController
@RequestMapping("/api/auth")
class LoginController(
    private val login: LoginService,
    private val sessions: SessionService,
    private val members: MemberRepository
) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse = login.login(request)

    @PostMapping("/restore")
    fun restore(@RequestBody request: LoginRequest): LoginResponse = login.restore(request)

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): TokenPair = login.refresh(request)

    // 이 기기의 로그인만 끝낸다. 액세스 토큰도 즉시 무효가 된다.
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        sessions.revoke(jwt.getClaimAsString("sid"))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MemberSummary =
        members.findById(jwt.subject.toInt()).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.")
        }.toSummary()
}
