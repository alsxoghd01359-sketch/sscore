package com.sscore.support

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// 공통 오류 처리가 예상하지 못한 예외를 내부 정보 없이 500으로 바꾸는지 확인하기 위한 테스트 전용 주소
@RestController
class TestFailureController {
    @GetMapping("/test/boom")
    fun boom(): String = throw IllegalStateException("secret internal detail")
}
