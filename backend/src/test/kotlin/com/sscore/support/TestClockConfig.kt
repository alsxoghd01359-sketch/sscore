package com.sscore.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 운영용 Clock 대신 테스트가 조작할 수 있는 시계를 쓴다.
@TestConfiguration
class TestClockConfig {
    @Bean
    @Primary
    fun mutableClock(): MutableClock = MutableClock()
}
