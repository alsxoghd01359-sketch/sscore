package com.sscore.auth

import com.sscore.member.MemberRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

@RestController
@RequestMapping("/api/auth")
class SignupController(private val signup: SignupService) {

    @PostMapping("/signup")
    fun start(@RequestBody request: SignupRequest): ResponseEntity<SignupStarted> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(signup.start(request))

    @PostMapping("/signup/resend")
    fun resend(@RequestBody request: ResendRequest): ResponseEntity<SignupStarted> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(signup.resend(request))

    @PostMapping("/signup/verify")
    fun verify(@RequestBody request: VerifyRequest): ResponseEntity<SignupCompleted> =
        ResponseEntity.status(HttpStatus.CREATED).body(signup.verify(request))

    @GetMapping("/nickname-available")
    fun nicknameAvailable(@RequestParam nickname: String?): NicknameAvailability =
        signup.nicknameAvailability(nickname)
}

// 로그인은 04번 티켓(JWT)에서 교체한다. 그때까지 기존 앱이 쓰던 응답 형태를 그대로 둔다.
@RestController
@RequestMapping("/api/user")
class LegacyLoginController(
    private val members: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {
    @PostMapping("/login")
    fun login(@RequestBody loginData: Map<String, String>): String {
        val member = members.findByEmail(loginData["email"].orEmpty().trim().lowercase())
        val ok = member != null && passwordEncoder.matches(loginData["password"].orEmpty(), member.password)
        return if (ok) "성공" else "실패"
    }
}
