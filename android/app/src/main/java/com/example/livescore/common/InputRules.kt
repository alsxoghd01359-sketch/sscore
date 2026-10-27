package com.example.livescore.common

// 서버와 같은 입력 규칙 (서버가 최종 검증한다). 가입·비밀번호 재설정·프로필 변경 화면이 함께 쓴다.
object InputRules {
    private val EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
    private val NICKNAME = "^[가-힣a-zA-Z0-9]{2,12}$".toRegex()
    private val PASSWORD = "^(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$".toRegex()
    private val CODE = "^\\d{6}$".toRegex()

    const val EMAIL_MESSAGE = "올바른 이메일 형식이 아닙니다."
    const val NICKNAME_MESSAGE = "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다."
    const val PASSWORD_MESSAGE = "비밀번호는 8~20자이며 특수문자를 포함해야 합니다."
    const val CODE_MESSAGE = "6자리 숫자 인증코드를 입력해 주세요."

    fun isEmail(v: String) = EMAIL.matches(v)
    fun isNickname(v: String) = NICKNAME.matches(v)
    fun isPassword(v: String) = PASSWORD.matches(v)
    fun isCode(v: String) = CODE.matches(v)
}
