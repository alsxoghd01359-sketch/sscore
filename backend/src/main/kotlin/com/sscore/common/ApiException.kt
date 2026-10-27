package com.sscore.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 사용자에게 보여 줄 수 있는 오류. code는 앱이 분기하는 값, message는 화면에 보이는 문구.
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)

data class ApiError(val code: String, val message: String)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(e.status).body(ApiError(e.code, e.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."))
}
