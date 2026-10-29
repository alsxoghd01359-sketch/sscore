package com.example.livescore.auth

import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class SignupBody(val email: String, val password: String, val nickname: String)
data class ResendBody(val email: String)
data class VerifyBody(val email: String, val code: String)
data class LoginBody(val email: String, val password: String)
data class RefreshBody(val refreshToken: String)
data class PasswordResetConfirmBody(val email: String, val code: String, val newPassword: String)

data class SignupStartedData(val email: String, val expiresInSeconds: Long, val resendAfterSeconds: Long)
data class SignupCompletedData(val memberId: Int, val email: String, val nickname: String)
data class NicknameAvailabilityData(val available: Boolean, val message: String? = null)

data class MemberData(val memberId: Int, val email: String, val nickname: String, val role: String, val supportedTeamId: Int? = null)

data class LoginResponseData(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long,
    val member: MemberData
)

data class TokenPairData(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long
)

// 인증 헤더가 필요 없는 주소 + 로그아웃(토큰을 직접 넘긴다). 토큰 자동 첨부·갱신이 없는 클라이언트로 호출한다.
interface AuthApi {
    @POST("/api/auth/signup")
    suspend fun signup(@Body body: SignupBody): Response<SignupStartedData>

    @POST("/api/auth/signup/resend")
    suspend fun resend(@Body body: ResendBody): Response<SignupStartedData>

    @POST("/api/auth/signup/verify")
    suspend fun verify(@Body body: VerifyBody): Response<SignupCompletedData>

    @GET("/api/auth/nickname-available")
    suspend fun nicknameAvailable(@Query("nickname") nickname: String): Response<NicknameAvailabilityData>

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginBody): Response<LoginResponseData>

    @POST("/api/auth/restore")
    suspend fun restore(@Body body: LoginBody): Response<LoginResponseData>

    @POST("/api/auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): Response<TokenPairData>

    @POST("/api/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @POST("/api/auth/password-reset")
    suspend fun requestPasswordReset(@Body body: ResendBody): Response<SignupStartedData>

    @POST("/api/auth/password-reset/resend")
    suspend fun resendPasswordReset(@Body body: ResendBody): Response<SignupStartedData>

    @POST("/api/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body body: PasswordResetConfirmBody): Response<Unit>
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

    suspend fun login(email: String, password: String): ApiResult<LoginResponseData> =
        apiCall { api.login(LoginBody(email, password)) }

    suspend fun restore(email: String, password: String): ApiResult<LoginResponseData> =
        apiCall { api.restore(LoginBody(email, password)) }

    suspend fun refresh(refreshToken: String): ApiResult<TokenPairData> =
        apiCall { api.refresh(RefreshBody(refreshToken)) }

    suspend fun requestPasswordReset(email: String): ApiResult<SignupStartedData> =
        apiCall { api.requestPasswordReset(ResendBody(email)) }

    suspend fun resendPasswordReset(email: String): ApiResult<SignupStartedData> =
        apiCall { api.resendPasswordReset(ResendBody(email)) }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): ApiResult<Unit> =
        apiCall { api.confirmPasswordReset(PasswordResetConfirmBody(email, code, newPassword)) }

    // 서버에 알리지 못해도(네트워크 오류 등) 이 기기의 로그인은 끝낸다 — 호출하는 쪽에서 처리
    suspend fun logout(accessToken: String): ApiResult<Unit> =
        apiCall { api.logout("Bearer $accessToken") }
}
