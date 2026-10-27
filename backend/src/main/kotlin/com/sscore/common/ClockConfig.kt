package com.sscore.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

// 시간에 의존하는 로직(인증코드 만료, 탈퇴 유예 등)이 테스트에서 시계를 바꿀 수 있도록 Clock을 주입해 쓴다.
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
