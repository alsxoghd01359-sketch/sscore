package com.sscore.football

import com.sscore.common.ApiException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.sscore.football.ExternalSupport")

// 화면의 부가 정보처럼 없어도 되는 외부 API 조회. 실패하면 이유를 로그에 남기고 기본값으로 대신한다.
inline fun <T> optionalExternal(what: String, fallback: T, block: () -> T): T =
    try {
        block()
    } catch (e: ApiException) {
        logOptionalFailure(what, e)
        fallback
    }

fun logOptionalFailure(what: String, e: ApiException) {
    log.warn("{} 조회 실패, 생략합니다: {}", what, e.message)
}
