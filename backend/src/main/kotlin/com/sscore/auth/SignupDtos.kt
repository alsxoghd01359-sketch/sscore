package com.sscore.auth

data class SignupRequest(val email: String? = null, val password: String? = null, val nickname: String? = null)

data class ResendRequest(val email: String? = null)

data class VerifyRequest(val email: String? = null, val code: String? = null)

data class SignupStarted(val email: String, val expiresInSeconds: Long, val resendAfterSeconds: Long)

data class SignupCompleted(val memberId: Int, val email: String, val nickname: String)

data class NicknameAvailability(val available: Boolean, val message: String? = null)
