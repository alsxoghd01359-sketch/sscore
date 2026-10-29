package com.sscore.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

// REST API 경계 테스트의 공통 틀.
// 실제와 같은 MySQL 8(Testcontainers)에 Flyway 마이그레이션을 적용한 상태로 HTTP 요청을 보낸다.
// - 외부 축구 API: 앱이 쓰는 RestTemplate에 MockRestServiceServer를 붙여 응답을 흉내 낸다 (footballApi)
// - 시간: MutableClock으로 앞당긴다 (clock)
// - 메일: FakeMailPort가 나간 인증코드를 모아 둔다 (mail)
// - 푸시: 푸시 기반 티켓(19)에서 같은 방식으로 가짜를 추가한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig::class, FakeMailConfig::class)
@Testcontainers
abstract class ApiTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var mail: FakeMailPort

    @Autowired
    private lateinit var restTemplate: RestTemplate

    protected lateinit var footballApi: MockRestServiceServer

    @org.junit.jupiter.api.BeforeEach
    fun resetFakes() {
        footballApi = MockRestServiceServer.bindTo(restTemplate).build()
        mail.clear()
        clock.set(MutableClock.START)   // 스프링 컨텍스트가 테스트 클래스끼리 공유되므로 시계도 매번 처음으로 되돌린다
    }

    companion object {
        @JvmStatic
        @ServiceConnection
        val mysql: MySQLContainer = MySQLContainer("mysql:8.0")
            .withDatabaseName("sscore_db")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")
            .also { it.start() }
    }
}
