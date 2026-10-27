package com.sscore.auth

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
