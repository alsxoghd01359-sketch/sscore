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
// - 메일·푸시: 해당 기능을 만드는 티켓(가입 인증, 푸시 기반)에서 같은 방식으로 가짜를 추가한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig::class)
@Testcontainers
abstract class ApiTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    private lateinit var restTemplate: RestTemplate

    protected lateinit var footballApi: MockRestServiceServer

    @org.junit.jupiter.api.BeforeEach
    fun resetFakes() {
        footballApi = MockRestServiceServer.bindTo(restTemplate).build()
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
