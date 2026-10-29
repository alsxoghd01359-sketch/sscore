package com.sscore.push

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberRole
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import kotlin.test.assertEquals

@TestPropertySource(properties = ["sscore.admin.emails=boss@example.com"])
class PushApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var devices: PushDeviceRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val password = "Passw0rd!"
    private var fanId: Int = 0

    private class Device(val access: String)

    @BeforeEach
    fun seed() {
        devices.deleteAll(); sessions.deleteAll(); members.deleteAll()
        fanId = member("fan@example.com", "축구팬", MemberRole.USER)
        member("boss@example.com", "관리자", MemberRole.ADMIN)
    }

    private fun member(email: String, nickname: String, role: MemberRole) = members.save(
        Member(email = email, password = passwordEncoder.encode(password)!!, nickname = nickname, role = role, createdAt = LocalDateTime.now(clock))
    ).userId!!

    private fun login(email: String = "fan@example.com"): Device {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Device(JsonPath.read(body, "$.accessToken"))
    }

    private fun register(d: Device, token: String): ResultActionsDsl = mockMvc.put("/api/members/me/push-token") {
        header("Authorization", "Bearer ${d.access}")
        contentType = MediaType.APPLICATION_JSON
        content = """{"token":"$token"}"""
    }

    private fun logout(d: Device) = mockMvc.post("/api/auth/logout") { header("Authorization", "Bearer ${d.access}") }
        .andExpect { status { isNoContent() } }

    private fun testPush(memberId: Int = fanId): ResultActionsDsl {
        val admin = login("boss@example.com")
        return mockMvc.post("/api/admin/push/test?memberId=$memberId") { header("Authorization", "Bearer ${admin.access}") }
    }

    @Test
    fun `로그인한 기기의 토큰을 등록하면 그 회원에게 보낸 푸시가 그 기기로 간다`() {
        register(login(), "token-phone").andExpect { status { isNoContent() } }

        testPush().andExpect { status { isOk() }; jsonPath("$.sentDevices") { value(1) } }

        assertEquals(listOf("token-phone"), push.tokensSent())
        assertEquals("SScore 테스트 알림", push.sent.single().message.title)
    }

    @Test
    fun `같은 기기가 토큰을 다시 보내면 갱신되고 중복되지 않는다`() {
        val phone = login()
        register(phone, "token-old")
        register(phone, "token-new").andExpect { status { isNoContent() } }

        assertEquals(1, devices.count())
        testPush()
        assertEquals(listOf("token-new"), push.tokensSent())
    }

    @Test
    fun `한 회원의 여러 기기에 푸시가 간다`() {
        register(login(), "token-phone")
        register(login(), "token-tablet")

        testPush().andExpect { jsonPath("$.sentDevices") { value(2) } }

        assertEquals(setOf("token-phone", "token-tablet"), push.tokensSent().toSet())
    }

    @Test
    fun `로그아웃하면 이 기기의 토큰만 해제된다`() {
        val phone = login()
        val tablet = login()
        register(phone, "token-phone"); register(tablet, "token-tablet")

        logout(phone)

        testPush().andExpect { jsonPath("$.sentDevices") { value(1) } }
        assertEquals(listOf("token-tablet"), push.tokensSent())
    }

    @Test
    fun `탈퇴하면 모든 기기의 토큰이 해제되어 푸시가 가지 않는다`() {
        val phone = login()
        register(phone, "token-phone"); register(login(), "token-tablet")

        mockMvc.post("/api/members/me/withdraw") {
            header("Authorization", "Bearer ${phone.access}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"password":"$password"}"""
        }.andExpect { status { isNoContent() } }

        assertEquals(0, devices.count())
        testPush().andExpect { jsonPath("$.sentDevices") { value(0) } }
        assertEquals(0, push.sent.size)
    }

    @Test
    fun `같은 폰에서 다른 계정으로 로그인하면 토큰이 새 계정으로 옮겨진다`() {
        register(login(), "token-shared")
        member("other@example.com", "다른팬", MemberRole.USER)
        register(login("other@example.com"), "token-shared").andExpect { status { isNoContent() } }

        assertEquals(1, devices.count())
        testPush().andExpect { jsonPath("$.sentDevices") { value(0) } }   // 이전 계정으로는 더 가지 않는다
    }

    @Test
    fun `FCM이 쓸 수 없다고 한 토큰은 지워진다`() {
        register(login(), "token-dead"); register(login(), "token-alive")
        push.invalidTokens += "token-dead"

        testPush().andExpect { jsonPath("$.sentDevices") { value(1) } }

        assertEquals(listOf("token-alive"), devices.findAll().map { it.token })
    }

    @Test
    fun `토큰이 비어 있으면 400이고 로그인하지 않으면 401이다`() {
        register(login(), " ").andExpect { status { isBadRequest() } }
        mockMvc.put("/api/members/me/push-token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"x"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `관리자가 아니면 테스트 푸시를 보낼 수 없다`() {
        val user = login()
        mockMvc.post("/api/admin/push/test?memberId=$fanId") { header("Authorization", "Bearer ${user.access}") }
            .andExpect { status { isForbidden() } }
    }
}
