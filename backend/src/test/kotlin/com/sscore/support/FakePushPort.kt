package com.sscore.support

import com.sscore.push.PushMessage
import com.sscore.push.PushPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 실제로 푸시를 보내지 않고, 나간 푸시를 테스트가 확인할 수 있게 모아 둔다.
class FakePushPort : PushPort {
    data class Sent(val tokens: List<String>, val message: PushMessage)

    val sent = mutableListOf<Sent>()

    // 쓸 수 없는 토큰으로 돌려줄 값 (앱 삭제 등으로 FCM이 거절한 경우)
    val invalidTokens = mutableSetOf<String>()

    override fun send(tokens: List<String>, message: PushMessage): List<String> {
        sent += Sent(tokens, message)
        return tokens.filter { it in invalidTokens }
    }

    fun tokensSent(): List<String> = sent.flatMap { it.tokens }

    fun clear() {
        sent.clear()
        invalidTokens.clear()
    }
}

@TestConfiguration
class FakePushConfig {
    @Bean
    @Primary
    fun fakePushPort(): FakePushPort = FakePushPort()
}
