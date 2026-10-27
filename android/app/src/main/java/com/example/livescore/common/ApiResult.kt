package com.example.livescore.common

import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

// 서버가 돌려주는 오류 본문 {code, message}
data class ApiErrorBody(val code: String? = null, val message: String? = null)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: String, val message: String) : ApiResult<Nothing>()
}

private val gson = Gson()

// suspend Retrofit 호출을 결과 타입으로 바꾼다. 서버 오류 본문과 네트워크 오류를 한 곳에서 처리한다.
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            val error = runCatching { gson.fromJson(response.errorBody()?.string(), ApiErrorBody::class.java) }.getOrNull()
            ApiResult.Failure(
                error?.code ?: "HTTP_${response.code()}",
                error?.message ?: "서버 오류가 발생했습니다. (${response.code()})"
            )
        }
    } catch (e: IOException) {
        ApiResult.Failure("NETWORK", "네트워크 연결을 확인해 주세요.")
    }
}
