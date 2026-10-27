package com.sscore.member

import com.sscore.auth.MemberSummary
import com.sscore.auth.SessionService
import com.sscore.auth.toSummary
import com.sscore.common.ApiException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ChangePasswordRequest(val currentPassword: String? = null, val newPassword: String? = null)

data class ChangeNicknameRequest(val nickname: String? = null)

// 로그인한 회원이 자기 프로필을 바꾼다.
@Service
class MemberProfileService(
    private val members: MemberRepository,
    private val sessions: SessionService,
    private val passwordEncoder: PasswordEncoder
) {
    // 현재 비밀번호를 확인한 뒤 바꾸고, 이 기기를 뺀 다른 기기의 로그인을 모두 푼다.
    @Transactional
    fun changePassword(memberId: Int, currentSessionId: String, request: ChangePasswordRequest) {
        val member = load(memberId)
        val current = request.currentPassword.orEmpty()
        val next = request.newPassword.orEmpty()
        if (!passwordEncoder.matches(current, member.password)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.")
        }
        MemberRules.requirePassword(next)
        if (current == next) {
            throw ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "현재 비밀번호와 다른 비밀번호를 입력해 주세요.")
        }
        member.password = passwordEncoder.encode(next)!!
        sessions.revokeOthers(memberId, currentSessionId)
    }

    @Transactional
    fun changeNickname(memberId: Int, request: ChangeNicknameRequest): MemberSummary {
        val member = load(memberId)
        val nickname = request.nickname.orEmpty().trim()
        MemberRules.requireNickname(nickname)
        if (nickname == member.nickname) return member.toSummary()
        if (members.existsByNickname(nickname)) throw nicknameTaken()
        member.nickname = nickname
        try {
            members.saveAndFlush(member)
        } catch (e: DataIntegrityViolationException) {
            // 확인과 저장 사이에 다른 회원이 같은 닉네임을 가져간 경우
            throw nicknameTaken()
        }
        return member.toSummary()
    }

    private fun load(memberId: Int): Member = members.findById(memberId).orElseThrow {
        ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.")
    }

    private fun nicknameTaken() = ApiException(HttpStatus.CONFLICT, "NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다.")
}

@RestController
@RequestMapping("/api/members/me")
class MemberProfileController(private val profile: MemberProfileService) {

    @PostMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        profile.changePassword(jwt.subject.toInt(), jwt.getClaimAsString("sid"), request)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/nickname")
    fun changeNickname(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ChangeNicknameRequest
    ): MemberSummary = profile.changeNickname(jwt.subject.toInt(), request)
}
