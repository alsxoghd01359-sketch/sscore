package com.sscore.controller

import com.sscore.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService
) {

    // 1. 로그인 API
    @PostMapping("/login")
    fun login(@RequestBody loginData: Map<String, String>): String {
        val email = loginData["email"] ?: ""
        val password = loginData["password"] ?: ""

        val loginSuccess = userService.login(email, password)

        // 성공 시 "성공", 실패 시 "실패" 반환
        return if (loginSuccess) "성공" else "실패"
    }

    // 2. 회원가입 API (추가됨)
    @PostMapping("/register")
    fun register(@RequestBody registerData: Map<String, String>): String {
        val email = registerData["email"] ?: ""
        val password = registerData["password"] ?: ""
        val nickname = registerData["nickname"] // 필요 시 사용

        // UserService에 구현된 유효성 검사 및 저장 로직 호출
        return userService.registerUser(email, password, nickname)
    }
}