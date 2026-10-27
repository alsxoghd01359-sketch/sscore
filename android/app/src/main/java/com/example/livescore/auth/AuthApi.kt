package com.example.livescore.auth

import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class SignupBody(val email: String, val password: String, val nickname: String)
data class ResendBody(val email: String)
data class VerifyBody(val email: String, val code: String)

data class SignupStartedData(val email: String, val expiresInSeconds: Long, val resendAfterSeconds: Long)
data class SignupCompletedData(val memberId: Int, val email: String, val nickname: String)
data class NicknameAvailabilityData(val available: Boolean, val message: String? = null)

interface AuthApi {
    @POST("/api/auth/signup")
    suspend fun signup(@Body body: SignupBody): Response<SignupStartedData>

    @POST("/api/auth/signup/resend")
    suspend fun resend(@Body body: ResendBody): Response<SignupStartedData>

    @POST("/api/auth/signup/verify")
    suspend fun verify(@Body body: VerifyBody): Response<SignupCompletedData>

    @GET("/api/auth/nickname-available")
    suspend fun nicknameAvailable(@Query("nickname") nickname: String): Response<NicknameAvailabilityData>
}

class AuthRepository(private val api: AuthApi) {
    suspend fun signup(email: String, password: String, nickname: String): ApiResult<SignupStartedData> =
        apiCall { api.signup(SignupBody(email, password, nickname)) }

    suspend fun resend(email: String): ApiResult<SignupStartedData> =
        apiCall { api.resend(ResendBody(email)) }

    suspend fun verify(email: String, code: String): ApiResult<SignupCompletedData> =
        apiCall { api.verify(VerifyBody(email, code)) }

    suspend fun nicknameAvailable(nickname: String): ApiResult<NicknameAvailabilityData> =
        apiCall { api.nicknameAvailable(nickname) }
}
