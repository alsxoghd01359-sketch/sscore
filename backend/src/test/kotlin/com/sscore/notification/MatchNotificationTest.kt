package com.sscore.notification

import com.jayway.jsonpath.JsonPath
import com.sscore.auth.AuthSessionRepository
import com.sscore.entity.MatchEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.TeamEntity
import com.sscore.follow.FollowRepository
import com.sscore.match.LiveMatchSync
import com.sscore.member.Member
import com.sscore.member.MemberRepository
import com.sscore.member.MemberStatus
import com.sscore.push.PushDeviceRepository
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.match.MatchDetailRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 가짜 외부 API · 가짜 푸시 · 조작 가능한 시계로, 라이브 갱신이 감지한 경기 이벤트가 설정에 맞게 알림으로 나가는지 확인한다.
// 테스트 시계의 시작은 2026-01-01 09:00 (한국 시간), 경기 킥오프는 09:30.
class MatchNotificationTest : ApiTest() {

    @Autowired private lateinit var sync: LiveMatchSync
    @Autowired private lateinit var notifier: MatchNotifier
    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var members: MemberRepository
    @Autowired private lateinit var sessions: AuthSessionRepository
    @Autowired private lateinit var devices: PushDeviceRepository
    @Autowired private lateinit var follows: FollowRepository
    @Autowired private lateinit var settings: NotificationSettingRepository
    @Autowired private lateinit var sentLog: NotificationLogRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var playerStats: PlayerStatRepository
    @Autowired private lateinit var events: MatchEventRepository
    @Autowired private lateinit var matchDetails: MatchDetailRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val base = "https://v3.football.api-sports.io"
    private val liveUrl = "$base/fixtures?live=39-140-78-135-61"
    private val password = "Passw0rd!"
    private val fixture = 700L

    private var counter = 0
    private lateinit var arsenal: TeamEntity
    private lateinit var chelsea: TeamEntity

    @BeforeEach
    fun seed() {
        sentLog.deleteAll(); devices.deleteAll(); follows.deleteAll(); settings.deleteAll(); sessions.deleteAll(); members.deleteAll()
        matchDetails.deleteAll(); playerStats.deleteAll(); events.deleteAll(); matches.deleteAll(); players.deleteAll(); teams.deleteAll()
        arsenal = teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        chelsea = teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        players.save(PlayerEntity(playerId = 10, name = "B. Saka", nationality = "England", photoUrl = "s.png", position = "Attacker", team = arsenal))
        players.save(PlayerEntity(playerId = 20, name = "C. Palmer", nationality = "England", photoUrl = "p.png", position = "Midfielder", team = chelsea))
        match("09:30", "0-0")
    }

    private fun match(time: String, score: String): MatchEntity {
        matches.findByFixtureId(fixture)?.let { matches.delete(it) }
        return saveMatch(time, score)
    }

    private fun saveMatch(time: String, score: String) = matches.save(
        MatchEntity(
            fixtureId = fixture, homeTeam = "Arsenal", awayTeam = "Chelsea", homeTeamId = 42, awayTeamId = 49,
            matchDate = "2026-01-01", matchTime = time, score = score, leagueId = 39, season = 2025
        )
    )

    // ── 회원 · 팔로우 도우미 ──────────────────────────────────────

    private class Fan(val id: Int, val email: String, val token: String)

    // 로그인한 기기 하나를 가진 회원을 만든다
    private fun fan(): Fan {
        val email = "fan${++counter}@example.com"
        val id = members.save(Member(email = email, password = passwordEncoder.encode(password)!!, nickname = "팬$counter", createdAt = LocalDateTime.now(clock))).userId!!
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val access: String = JsonPath.read(body, "$.accessToken")
        val fan = Fan(id, email, "device-$counter")
        mockMvc.put("/api/members/me/push-token") {
            header("Authorization", "Bearer $access"); contentType = MediaType.APPLICATION_JSON; content = """{"token":"${fan.token}"}"""
        }.andExpect { status { isNoContent() } }
        tokens[id] = access
        return fan
    }

    private val tokens = mutableMapOf<Int, String>()

    private fun follow(f: Fan, path: String) = mockMvc.put("/api/members/me/follows/$path") { header("Authorization", "Bearer ${tokens[f.id]}") }
        .andExpect { status { isNoContent() } }

    private fun setting(f: Fan, path: String, body: String): ResultActionsDsl = mockMvc.patch("/api/members/me/notification-settings/$path") {
        header("Authorization", "Bearer ${tokens[f.id]}"); contentType = MediaType.APPLICATION_JSON; content = body
    }.andExpect { status { isOk() } }

    // ── 외부 API 응답 도우미 ──────────────────────────────────────

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)
    private fun body(vararg items: String) = """{"errors":[],"response":[${items.joinToString(",")}]}"""

    private fun liveFixture(status: String, elapsed: Int, home: Int, away: Int) = """
        {"fixture":{"id":$fixture,"date":"2026-01-01T00:30:00+00:00","status":{"short":"$status","elapsed":$elapsed}},
         "league":{"id":39,"season":2025,"round":"R1"},"teams":{"home":{"id":42,"name":"Arsenal"},"away":{"id":49,"name":"Chelsea"}},
         "goals":{"home":$home,"away":$away}}
    """.trimIndent()

    private fun goal(minute: Int, team: Int, player: String, assist: String? = null) = """
        {"time":{"elapsed":$minute,"extra":null},"team":{"id":$team,"name":"${if (team == 42) "Arsenal" else "Chelsea"}"},
         "player":{"name":"$player"},"assist":{"name":${assist?.let { "\"$it\"" } ?: "null"}},"type":"Goal","detail":"Normal Goal"}
    """.trimIndent()

    private fun redCard(minute: Int, team: Int, player: String) = """
        {"time":{"elapsed":$minute,"extra":null},"team":{"id":$team,"name":"Arsenal"},
         "player":{"name":"$player"},"assist":{"name":null},"type":"Card","detail":"Red Card"}
    """.trimIndent()

    private fun details(status: String, vararg eventJson: String) = body(
        """{"fixture":{"id":$fixture,"date":"2026-01-01T00:30:00+00:00","status":{"short":"$status"}},
           "events":[${eventJson.joinToString(",")}],"statistics":[],"players":[]}"""
    )

    private fun expectLive(vararg fixtures: String) {
        freshFootballApi().expect(requestTo(liveUrl)).andRespond(ok(body(*fixtures)))
    }

    private fun tokensPushed() = push.tokensSent()
    private fun titles() = push.sent.map { it.message.title }

    // ── 경기 시작 ───────────────────────────────────────────────

    @Test
    fun `경기가 시작되면 팔로우한 팀의 회원에게 시작 알림이 가고 누르면 열 경기 번호가 담긴다`() {
        val fan = fan(); follow(fan, "teams/42")
        clock.advance(Duration.ofMinutes(31))
        expectLive(liveFixture("1H", 1, 0, 0))

        sync.sync()

        assertEquals(listOf(fan.token), tokensPushed())
        val message = push.sent.single().message
        assertEquals("경기 시작", message.title)
        assertEquals("Arsenal vs Chelsea", message.body)
        assertEquals(mapOf("type" to "MATCH_START", "fixtureId" to "$fixture"), message.data)
    }

    @Test
    fun `팔로우하지 않았거나 관련 없는 경기는 알림이 가지 않는다`() {
        fan()   // 아무것도 팔로우하지 않은 회원
        clock.advance(Duration.ofMinutes(31))
        expectLive(liveFixture("1H", 1, 0, 0))

        sync.sync()

        assertEquals(0, push.sent.size)
        assertEquals(0, sentLog.count())
    }

    @Test
    fun `이미 많이 진행된 경기를 처음 발견했을 때는 시작 알림을 보내지 않는다`() {
        val fan = fan(); follow(fan, "teams/42")
        clock.advance(Duration.ofMinutes(75))
        expectLive(liveFixture("2H", 70, 0, 0))

        sync.sync()

        assertEquals(0, push.sent.size)
    }

    // ── 득점 / 이벤트 ───────────────────────────────────────────

    @Test
    fun `득점이 나오면 팀을 팔로우한 회원에게 득점자와 함께 알림이 가고 같은 득점은 다시 가지 않는다`() {
        val fan = fan(); follow(fan, "teams/42")
        match("09:30 (진행중 20')", "0-0")
        clock.advance(Duration.ofMinutes(51))

        expectLive(liveFixture("1H", 20, 1, 0))
        footballApi.expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("1H", goal(20, 42, "B. Saka", "M. Odegaard"))))
        sync.sync()

        assertEquals(1, push.sent.size)
        val message = push.sent.single().message
        assertTrue(message.title.contains("골") && message.title.contains("1-0"))
        assertTrue(message.body.contains("B. Saka"))
        assertEquals("GOAL", message.data["type"])

        // 점수가 같은 채로 다시 감지돼도, 이벤트 목록이 다시 와도 새 알림은 없다
        freshFootballApi().expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("1H", goal(20, 42, "B. Saka", "M. Odegaard"))))
        notifier.scoreChanged(matches.findByFixtureId(fixture)!!)   // (같은 이벤트 재감지)
        assertEquals(1, push.sent.size)
    }

    @Test
    fun `서버를 다시 켜서 같은 득점을 다시 감지해도 한 번만 간다`() {
        val fan = fan(); follow(fan, "teams/42")
        match("09:30 (진행중 20')", "1-0")
        val m = matches.findByFixtureId(fixture)!!

        footballApi.expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("1H", goal(20, 42, "B. Saka"))))
        notifier.scoreChanged(m)
        assertEquals(1, push.sent.size)

        // 메모리 기록이 없는 새 시작처럼, 같은 이벤트를 또 받아도 DB 기록 때문에 막힌다
        clock.advance(Duration.ofMinutes(1))
        freshFootballApi().expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("1H", goal(20, 42, "B. Saka"))))
        notifier.scoreChanged(m)
        assertEquals(1, push.sent.size)
    }

    @Test
    fun `선수를 팔로우한 회원은 그 선수가 관련된 득점만 받는다`() {
        val sakaFan = fan(); follow(sakaFan, "players/10")
        val palmerFan = fan(); follow(palmerFan, "players/20")
        match("09:30 (진행중 20')", "1-0")

        footballApi.expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("1H", goal(20, 42, "B. Saka"))))
        notifier.scoreChanged(matches.findByFixtureId(fixture)!!)

        assertEquals(listOf(sakaFan.token), tokensPushed())
    }

    @Test
    fun `퇴장도 이벤트 알림으로 간다`() {
        val fan = fan(); follow(fan, "teams/42")
        match("09:30 (진행중 50')", "0-0")
        // 퇴장은 점수가 바뀌지 않아 득점 감지 흐름이 아니라 직접 확인 경로로 검증한다
        footballApi.expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("2H", redCard(50, 42, "G. Xhaka"))))

        notifier.scoreChanged(matches.findByFixtureId(fixture)!!)

        assertEquals("RED_CARD", push.sent.single().message.data["type"])
    }

    // ── 종료 / 결과 ─────────────────────────────────────────────

    @Test
    fun `경기가 끝나면 결과 알림이 간다`() {
        val fan = fan(); follow(fan, "teams/49")
        match("09:30 (진행중 88')", "2-1")
        clock.advance(Duration.ofMinutes(130))

        freshFootballApi().expect(requestTo(liveUrl)).andRespond(ok(body()))
        footballApi.expect(requestTo("$base/fixtures?ids=$fixture")).andRespond(ok(body(liveFixture("FT", 90, 2, 1))))
        // 득점이 있던 경기라 점수 변화 확인과 확정본 저장에 경기 상세를 받는다
        footballApi.expect(requestTo("$base/fixtures?id=$fixture")).andRespond(ok(details("FT", goal(10, 42, "B. Saka"))))
        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=$fixture")).andRespond(ok(body()))

        sync.sync()

        assertTrue("경기 종료" in titles())
        val end = push.sent.first { it.message.title == "경기 종료" }.message
        assertEquals("Arsenal 2-1 Chelsea", end.body)
        assertEquals("MATCH_END", end.data["type"])
    }

    // ── 설정 · 중복 · 탈퇴 유예 ──────────────────────────────────

    @Test
    fun `꺼진 알림 종류와 꺼진 대상에는 알림이 가지 않는다`() {
        val kindOff = fan(); follow(kindOff, "teams/42"); setting(kindOff, "kinds", """{"matchStart":false}""")
        val targetOff = fan(); follow(targetOff, "teams/42"); setting(targetOff, "teams/42", """{"enabled":false}""")
        val on = fan(); follow(on, "teams/42")
        clock.advance(Duration.ofMinutes(31))
        expectLive(liveFixture("1H", 1, 0, 0))

        sync.sync()

        assertEquals(listOf(on.token), tokensPushed())
    }

    @Test
    fun `종류가 꺼져 있어도 다른 종류는 받는다`() {
        val fan = fan(); follow(fan, "teams/42"); setting(fan, "kinds", """{"matchStart":false}""")
        match("09:30 (진행중 88')", "2-1")
        val m = matches.findByFixtureId(fixture)!!

        notifier.matchStarted(m)
        assertEquals(0, push.sent.size)
        notifier.matchEnded(m)
        assertEquals(listOf("경기 종료"), titles())
    }

    @Test
    fun `두 팀을 모두 팔로우해도 같은 이벤트 알림은 한 번만 간다`() {
        val fan = fan(); follow(fan, "teams/42"); follow(fan, "teams/49"); follow(fan, "players/10")
        clock.advance(Duration.ofMinutes(31))
        expectLive(liveFixture("1H", 1, 0, 0))

        sync.sync()
        assertEquals(1, push.sent.size)

        // 다시 감지돼도 더 가지 않는다
        notifier.matchStarted(matches.findByFixtureId(fixture)!!)
        assertEquals(1, push.sent.size)
    }

    @Test
    fun `한 회원의 여러 기기에 알림이 간다`() {
        val fan = fan(); follow(fan, "teams/42")
        // 같은 회원의 두 번째 기기
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON; content = """{"email":"${fan.email}","password":"$password"}"""
        }.andReturn().response.contentAsString
        mockMvc.put("/api/members/me/push-token") {
            header("Authorization", "Bearer ${JsonPath.read<String>(body, "$.accessToken")}"); contentType = MediaType.APPLICATION_JSON; content = """{"token":"tablet"}"""
        }.andExpect { status { isNoContent() } }

        notifier.matchStarted(matches.findByFixtureId(fixture)!!)

        assertEquals(setOf(fan.token, "tablet"), tokensPushed().toSet())
    }

    @Test
    fun `탈퇴 유예 중인 회원에게는 알림이 가지 않는다`() {
        val active = fan(); follow(active, "teams/42")
        val withdrawing = fan(); follow(withdrawing, "teams/42")
        // 유예 중: 상태만 바꿔 둔다 (탈퇴 요청은 기기를 풀지만, 상태 확인이 별도로 막아 주는지 본다)
        members.save(members.findById(withdrawing.id).get().also { it.status = MemberStatus.WITHDRAWN })

        notifier.matchStarted(matches.findByFixtureId(fixture)!!)

        assertEquals(listOf(active.token), tokensPushed())
    }

    // ── 라인업 ──────────────────────────────────────────────────

    private val lineupJson = body(
        """{"team":{"name":"Arsenal"},"formation":"4-3-3","coach":{"name":"A"},"startXI":[],"substitutes":[]}""",
        """{"team":{"name":"Chelsea"},"formation":"4-2-3-1","coach":{"name":"B"},"startXI":[],"substitutes":[]}"""
    )

    @Test
    fun `라인업이 발표되면 팔로우한 회원에게 한 번 알리고 그 전에는 다시 확인한다`() {
        val fan = fan(); follow(fan, "teams/42")
        clock.advance(Duration.ofMinutes(-45))   // 08:15 — 킥오프 75분 전

        footballApi.expect(requestTo("$base/fixtures/lineups?fixture=$fixture")).andRespond(ok(body()))   // 아직 발표 전
        notifier.checkLineups()
        assertEquals(0, push.sent.size)

        clock.advance(Duration.ofMinutes(5))
        freshFootballApi().expect(requestTo("$base/fixtures/lineups?fixture=$fixture")).andRespond(ok(lineupJson))
        notifier.checkLineups()
        assertEquals("라인업 발표", push.sent.single().message.title)

        // 발표 뒤에는 외부 호출도, 알림도 더 없다
        clock.advance(Duration.ofMinutes(5))
        freshFootballApi()
        notifier.checkLineups()
        assertEquals(1, push.sent.size)
        footballApi.verify()
    }

    @Test
    fun `팔로우하는 사람이 없는 경기는 라인업을 확인하지 않는다`() {
        fan()
        clock.advance(Duration.ofMinutes(-45))
        notifier.checkLineups()   // 외부 API 기대값이 없으므로 호출이 나가면 실패한다
        assertEquals(0, push.sent.size)
    }
}
