package com.sscore.member

import com.sscore.common.ApiException
import org.springframework.http.HttpStatus

// 가입·비밀번호 재설정·프로필 변경이 함께 쓰는 입력 규칙
object MemberRules {
    private val EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
    private val PASSWORD_REGEX = "^(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}$".toRegex()
    private val NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,12}$".toRegex()

    const val NICKNAME_RULE_MESSAGE = "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다."
    const val PASSWORD_RULE_MESSAGE = "비밀번호는 8~20자이며 특수문자를 포함해야 합니다."

    fun normalizeEmail(raw: String?): String {
        val email = raw.orEmpty().trim().lowercase()
        if (email.length > 254 || !EMAIL_REGEX.matches(email)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "올바른 이메일 형식이 아닙니다.")
        }
        return email
    }

    fun requirePassword(password: String) {
        if (!PASSWORD_REGEX.matches(password)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", PASSWORD_RULE_MESSAGE)
        }
    }

    fun isValidNickname(nickname: String): Boolean = NICKNAME_REGEX.matches(nickname)

    fun requireNickname(nickname: String) {
        if (!isValidNickname(nickname)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", NICKNAME_RULE_MESSAGE)
        }
    }
}
