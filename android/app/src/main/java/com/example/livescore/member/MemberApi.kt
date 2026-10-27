package com.example.livescore.member

import com.example.livescore.auth.MemberData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

data class ChangePasswordBody(val currentPassword: String, val newPassword: String)
data class ChangeNicknameBody(val nickname: String)

// 로그인한 회원 전용 주소. 토큰 자동 첨부·갱신이 되는 클라이언트로 호출한다.
interface MemberApi {
    @POST("/api/members/me/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): Response<Unit>

    @PATCH("/api/members/me/nickname")
    suspend fun changeNickname(@Body body: ChangeNicknameBody): Response<MemberData>
}

class MemberRepository(private val api: MemberApi) {
    suspend fun changePassword(current: String, next: String): ApiResult<Unit> =
        apiCall { api.changePassword(ChangePasswordBody(current, next)) }

    suspend fun changeNickname(nickname: String): ApiResult<MemberData> =
        apiCall { api.changeNickname(ChangeNicknameBody(nickname)) }
}
