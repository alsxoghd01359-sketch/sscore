package com.sscore.service

import com.sscore.entity.User
import com.sscore.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository) {

    fun registerUser(email: String, password: String, nickname: String?): String {
        if (!email.contains("@")) {
            return "올바른 이메일 형식이 아닙니다."
        }

        val passwordRegex = "^(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}\$".toRegex()
        if (!passwordRegex.matches(password)) {
            return "비밀번호는 8~20자이며 특수문자를 포함해야 합니다."
        }

        if (userRepository.findByEmail(email) != null) {
            return "이미 가입된 이메일입니다."
        }

        val newUser = User(email = email, password = password)
        userRepository.save(newUser)

        return "회원가입이 완료되었습니다."
    }

    fun login(email: String, password: String): Boolean {
        val user = userRepository.findByEmail(email)

        return user != null && user.password == password
    }
}