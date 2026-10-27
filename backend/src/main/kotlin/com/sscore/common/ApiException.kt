package com.sscore.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

// 사용자에게 보여 줄 수 있는 오류. code는 앱이 분기하는 값, message는 화면에 보이는 문구.
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)

data class ApiError(val code: String, val message: String)

// 모든 컨트롤러의 오류를 {code, message} 모양과 알맞은 상태 코드로 돌려준다.
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ApiError> = respond(e.status, e.code, e.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException) =
        respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다.")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException) =
        respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "필수 값이 없습니다: ${e.parameterName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
        respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "값의 형식이 올바르지 않습니다: ${e.name}")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethod(e: HttpRequestMethodNotSupportedException) =
        respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException) =
        respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 주소를 찾을 수 없습니다.")

    // 예상하지 못한 오류: 원인은 서버 로그에 남기고, 사용자에게는 내부 정보를 보여 주지 않는다.
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("처리되지 않은 오류", e)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
    }

    private fun respond(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ApiError(code, message))
}
