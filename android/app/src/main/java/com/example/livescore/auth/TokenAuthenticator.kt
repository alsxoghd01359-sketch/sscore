package com.example.livescore.auth

import com.example.livescore.common.ApiResult
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

// 로그인 상태면 모든 요청에 액세스 토큰을 붙인다. 게스트는 헤더 없이 보낸다.
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = SessionManager.accessToken
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

// 액세스 토큰이 만료되어 401을 받으면 갱신 토큰으로 새 토큰을 받아 같은 요청을 한 번 다시 보낸다.
// 갱신 토큰까지 거절되면 로그인을 풀고(게스트) 안내를 띄운다.
class TokenAuthenticator(private val repository: AuthRepository) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            // 다른 요청이 먼저 갱신을 끝냈다면 새 토큰으로 바로 재시도
            val current = SessionManager.accessToken
            if (current != null && current != sentToken) return withToken(response.request, current)

            val refresh = SessionManager.refreshToken ?: return null
            return when (val result = runBlocking { repository.refresh(refresh) }) {
                is ApiResult.Success -> {
                    SessionManager.updateTokens(result.data.accessToken, result.data.refreshToken)
                    withToken(response.request, result.data.accessToken)
                }
                is ApiResult.Failure -> {
                    // 네트워크 오류는 로그인을 유지하고 이번 요청만 실패시킨다
                    if (result.code != "NETWORK") SessionManager.expire()
                    null
                }
            }
        }
    }

    private fun withToken(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
