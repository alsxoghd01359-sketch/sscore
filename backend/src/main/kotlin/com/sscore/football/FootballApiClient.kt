package com.sscore.football

import com.sscore.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

// 외부 축구 API(API-Football)를 부르는 유일한 곳.
// 오류 응답이나 예상과 다른 모양의 응답은 여기서 ApiException(502)으로 바꿔서, 호출한 쪽이 잘못된 데이터를 저장하지 않게 한다.
@Component
class FootballApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${API_FOOTBALL_KEY}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val baseUrl = "https://v3.football.api-sports.io"

    // 가장 최근 응답 헤더로 알게 된 오늘 남은 호출 수 (아직 모르면 null)
    @Volatile
    final var lastRemaining: Int? = null
        private set

    // 응답의 response 배열 항목들과, 페이지 단위 응답의 전체 페이지 수
    data class Page(val items: List<Map<*, *>>, val totalPages: Int)

    fun list(path: String, vararg params: Pair<String, Any?>): List<Map<*, *>> = page(path, *params).items

    fun page(path: String, vararg params: Pair<String, Any?>): Page {
        val uri = UriComponentsBuilder.fromUriString(baseUrl).path(path)
            .also { builder -> params.forEach { (k, v) -> if (v != null) builder.queryParam(k, v) } }
            .build().toUri()
        val request = HttpEntity<Void>(HttpHeaders().apply { set("x-apisports-key", apiKey) })

        val response = try {
            restTemplate.exchange(uri, HttpMethod.GET, request, Map::class.java)
        } catch (e: RestClientException) {
            log.error("외부 축구 API 호출 실패: {}", uri, e)
            throw unavailable("외부 축구 데이터 서버에 연결하지 못했습니다.")
        }
        logQuota(response.headers)

        val body = response.body ?: throw unavailable("외부 축구 데이터 서버가 빈 응답을 보냈습니다.")
        val errors = body["errors"]
        if (hasErrors(errors)) {
            log.error("외부 축구 API가 오류를 돌려줌: {} errors={}", uri, errors)
            throw unavailable("외부 축구 데이터 서버 오류: ${summarize(errors)}")
        }
        val raw = body["response"] as? List<*>
            ?: throw unavailable("외부 축구 데이터 서버의 응답 형식이 올바르지 않습니다.")
        if (raw.any { it !is Map<*, *> }) {
            log.error("외부 축구 API 응답 항목이 객체가 아님: {}", uri)
            throw unavailable("외부 축구 데이터 서버의 응답 형식이 올바르지 않습니다.")
        }
        val totalPages = ((body["paging"] as? Map<*, *>)?.get("total") as? Number)?.toInt() ?: 1
        return Page(raw.filterIsInstance<Map<*, *>>(), totalPages)
    }

    private fun hasErrors(errors: Any?): Boolean = when (errors) {
        null -> false
        is Map<*, *> -> errors.isNotEmpty()
        is List<*> -> errors.isNotEmpty()
        else -> errors.toString().isNotBlank()
    }

    private fun summarize(errors: Any?): String = when (errors) {
        is Map<*, *> -> errors.values.joinToString("; ") { it.toString() }
        is List<*> -> errors.joinToString("; ") { it.toString() }
        else -> errors.toString()
    }.take(200)

    // 하루 호출 한도 확인용으로 남은 호출 수를 남긴다
    private fun logQuota(headers: HttpHeaders) {
        val remaining = headers.getFirst("x-ratelimit-requests-remaining")?.toIntOrNull() ?: return
        lastRemaining = remaining
        if (remaining < 100) log.warn("외부 축구 API 오늘 남은 호출 수: {}", remaining) else log.debug("외부 축구 API 남은 호출 수: {}", remaining)
    }

    private fun unavailable(message: String) = ApiException(HttpStatus.BAD_GATEWAY, "FOOTBALL_API_ERROR", message)
}
