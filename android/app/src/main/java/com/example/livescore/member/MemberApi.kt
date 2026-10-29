package com.example.livescore.member

import com.example.livescore.auth.MemberData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.PATCH
import retrofit2.http.POST

data class ChangePasswordBody(val currentPassword: String, val newPassword: String)
data class ChangeNicknameBody(val nickname: String)
data class WithdrawBody(val password: String)

// 로그인한 회원 전용 주소. 토큰 자동 첨부·갱신이 되는 클라이언트로 호출한다.
interface MemberApi {
    @POST("/api/members/me/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): Response<Unit>

    @POST("/api/members/me/withdraw")
    suspend fun withdraw(@Body body: WithdrawBody): Response<Unit>

    @PATCH("/api/members/me/nickname")
    suspend fun changeNickname(@Body body: ChangeNicknameBody): Response<MemberData>

    @GET("/api/auth/me")
    suspend fun me(): Response<MemberData>

    @PUT("/api/members/me/supported-team/{teamId}")
    suspend fun setSupportedTeam(@Path("teamId") teamId: Int): Response<MemberData>

    @DELETE("/api/members/me/supported-team")
    suspend fun clearSupportedTeam(): Response<MemberData>
}

class MemberRepository(private val api: MemberApi) {
    suspend fun changePassword(current: String, next: String): ApiResult<Unit> =
        apiCall { api.changePassword(ChangePasswordBody(current, next)) }

    suspend fun withdraw(password: String): ApiResult<Unit> =
        apiCall { api.withdraw(WithdrawBody(password)) }

    suspend fun changeNickname(nickname: String): ApiResult<MemberData> =
        apiCall { api.changeNickname(ChangeNicknameBody(nickname)) }

    suspend fun me(): ApiResult<MemberData> = apiCall { api.me() }
    suspend fun setSupportedTeam(teamId: Int): ApiResult<MemberData> = apiCall { api.setSupportedTeam(teamId) }
    suspend fun clearSupportedTeam(): ApiResult<MemberData> = apiCall { api.clearSupportedTeam() }
}
