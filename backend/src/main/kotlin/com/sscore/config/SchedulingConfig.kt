package com.sscore.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// 탈퇴 유예가 끝난 회원 삭제 같은 정기 작업을 켠다.
@Configuration
@EnableScheduling
class SchedulingConfig
