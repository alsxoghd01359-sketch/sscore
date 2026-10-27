package com.sscore.service

// import org.springframework.scheduling.annotation.EnableScheduling
// import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
// @EnableScheduling
class LiveMatchScheduler(
    private val soccerService: SoccerService
) {

    // 🔴 비활성화: API 이용량 절약을 위해 5초 주기 실시간 동기화 중지
    // 재활성화하려면 아래 주석을 해제하고 클래스 상단 @EnableScheduling 도 함께 해제할 것
    // @Scheduled(fixedRate = 5000)
    fun triggerLiveSync() {
        println("[실시간 데이터 갱신 엔진] 5초 주기 스캐닝 작동 중...")
        soccerService.syncLiveMatches()
    }
}