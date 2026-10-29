package com.sscore.notification

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.PlayerEntity
import com.sscore.entity.TeamEntity
import com.sscore.follow.FollowRepository
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.TeamRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import kotlin.test.assertEquals

class NotificationSettingsApiTest : ApiTest() {

    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var follows: FollowRepository
    @Autowired private lateinit var settings: NotificationSettingRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val password = "Passw0rd!"
    private val base = "/api/members/me/notification-settings"

    @BeforeEach
    fun seed() {
        settings.deleteAll(); follows.deleteAll(); sessions.deleteAll(); members.deleteAll(); players.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        players.save(PlayerEntity(playerId = 10, name = "B. Saka", nationality = "England", photoUrl = "saka.png", position = "Attacker", team = null))
        members.save(Member(email = "fan@example.com", password = passwordEncoder.encode(password)!!, nickname = "축구팬", createdAt = LocalDateTime.now(clock)))
    }

    private fun login(): String {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"fan@example.com","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun follow(t: String, path: String) = mockMvc.put("/api/members/me/follows/$path") { header("Authorization", "Bearer $t") }
    private fun get(t: String): ResultActionsDsl = mockMvc.get(base) { header("Authorization", "Bearer $t") }
    private fun patch(t: String, path: String, body: String): ResultActionsDsl = mockMvc.patch("$base/$path") {
        header("Authorization", "Bearer $t")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `처음에는 종류가 모두 켜져 있고 팔로우한 대상도 켜져 있다`() {
        val t = login()
        follow(t, "teams/42"); follow(t, "players/10")

        get(t).andExpect {
            status { isOk() }
            jsonPath("$.kinds.matchStart") { value(true) }
            jsonPath("$.kinds.goalsEvents") { value(true) }
            jsonPath("$.kinds.matchEnd") { value(true) }
            jsonPath("$.kinds.lineup") { value(true) }
            jsonPath("$.teams[0].name") { value("Arsenal") }
            jsonPath("$.teams[0].notify") { value(true) }
            jsonPath("$.teams[0].effective.goalsEvents") { value(true) }
            jsonPath("$.players[0].notify") { value(true) }
        }
    }

    @Test
    fun `알림 종류를 각각 켜고 끌 수 있고 보내지 않은 종류는 그대로다`() {
        val t = login()
        patch(t, "kinds", """{"matchStart":false,"lineup":false}""").andExpect {
            status { isOk() }
            jsonPath("$.kinds.matchStart") { value(false) }
            jsonPath("$.kinds.lineup") { value(false) }
            jsonPath("$.kinds.goalsEvents") { value(true) }
            jsonPath("$.kinds.matchEnd") { value(true) }
        }
        patch(t, "kinds", """{"matchStart":true}""").andExpect {
            jsonPath("$.kinds.matchStart") { value(true) }
            jsonPath("$.kinds.lineup") { value(false) }
        }
    }

    @Test
    fun `대상별로 알림을 켜고 끌 수 있다`() {
        val t = login()
        follow(t, "teams/42"); follow(t, "teams/49"); follow(t, "players/10")

        patch(t, "teams/42", """{"enabled":false}""").andExpect {
            status { isOk() }
            jsonPath("$.teams[0].notify") { value(false) }
            jsonPath("$.teams[1].notify") { value(true) }
            jsonPath("$.players[0].notify") { value(true) }
        }
        patch(t, "players/10", """{"enabled":false}""").andExpect { jsonPath("$.players[0].notify") { value(false) } }
    }

    @Test
    fun `알림은 종류와 대상이 모두 켜져 있어야 나간다는 규칙이 조회 결과에 나온다`() {
        val t = login()
        follow(t, "teams/42"); follow(t, "teams/49")
        patch(t, "kinds", """{"lineup":false}""")
        patch(t, "teams/49", """{"enabled":false}""")

        get(t).andExpect {
            // 켜진 대상: 꺼진 종류(라인업)만 막힌다
            jsonPath("$.teams[0].effective.matchStart") { value(true) }
            jsonPath("$.teams[0].effective.goalsEvents") { value(true) }
            jsonPath("$.teams[0].effective.matchEnd") { value(true) }
            jsonPath("$.teams[0].effective.lineup") { value(false) }
            // 꺼진 대상: 종류가 켜져 있어도 모두 막힌다
            jsonPath("$.teams[1].effective.matchStart") { value(false) }
            jsonPath("$.teams[1].effective.goalsEvents") { value(false) }
            jsonPath("$.teams[1].effective.matchEnd") { value(false) }
            jsonPath("$.teams[1].effective.lineup") { value(false) }
        }
    }

    @Test
    fun `규칙 함수는 종류와 대상이 모두 켜져 있을 때만 참이다`() {
        val on = NotificationSetting(1)
        val off = NotificationSetting(1, goalsEvents = false)
        assertEquals(true, NotificationRules.shouldSend(on, NotificationKind.GOALS_EVENTS, true))
        assertEquals(false, NotificationRules.shouldSend(on, NotificationKind.GOALS_EVENTS, false))
        assertEquals(false, NotificationRules.shouldSend(off, NotificationKind.GOALS_EVENTS, true))
        assertEquals(false, NotificationRules.shouldSend(off, NotificationKind.GOALS_EVENTS, false))
        assertEquals(true, NotificationRules.shouldSend(off, NotificationKind.MATCH_END, true))
        assertEquals(true, NotificationRules.shouldSend(null, NotificationKind.LINEUP, true))   // 설정이 없으면 켜짐
    }

    @Test
    fun `설정은 서버에 저장되어 다른 기기에서 로그인해도 같다`() {
        val phone = login()
        follow(phone, "teams/42")
        patch(phone, "kinds", """{"matchEnd":false}""")
        patch(phone, "teams/42", """{"enabled":false}""")

        get(login()).andExpect {
            jsonPath("$.kinds.matchEnd") { value(false) }
            jsonPath("$.kinds.matchStart") { value(true) }
            jsonPath("$.teams[0].notify") { value(false) }
        }
    }

    @Test
    fun `팔로우하지 않은 대상은 404이고 값이 없으면 400이며 로그인하지 않으면 401이다`() {
        val t = login()
        patch(t, "teams/42", """{"enabled":false}""").andExpect { status { isNotFound() }; jsonPath("$.code") { value("NOT_FOLLOWING") } }
        follow(t, "teams/42")
        patch(t, "teams/42", """{}""").andExpect { status { isBadRequest() } }
        mockMvc.get(base).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `회원이 지워지면 알림 설정도 함께 지워진다`() {
        val t = login()
        patch(t, "kinds", """{"matchStart":false}""")
        assertEquals(1, settings.count())
        sessions.deleteAll(); members.deleteAll()
        assertEquals(0, settings.count())
    }
}
