package com.sscore.player

import com.sscore.entity.ChampionEntity
import com.sscore.entity.MatchEntity
import com.sscore.entity.MatchEventEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.PlayerStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.get

class PlayerApiTest : ApiTest() {

    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var seasonStats: PlayerSeasonStatRepository
    @Autowired private lateinit var playerStats: PlayerStatRepository
    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var events: MatchEventRepository
    @Autowired private lateinit var champions: ChampionRepository

    private val base = "https://v3.football.api-sports.io"
    private val arsenal = 42
    private val chelsea = 49
    private val saka = 10L

    @BeforeEach
    fun seed() {
        playerStats.deleteAll(); events.deleteAll(); seasonStats.deleteAll(); players.deleteAll()
        matches.deleteAll(); champions.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(arsenal, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(chelsea, "Chelsea", "England", "chelsea.png"))
        players.save(PlayerEntity(saka, "Bukayo Saka", "England", "saka.png", "Attacker", 24, teams.findById(arsenal).get()))
    }

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun season(season: Int, team: String = "Arsenal", apps: Int = 30, goals: Int = 10, assists: Int = 8, rating: Double? = 7.6) =
        seasonStats.save(
            PlayerSeasonStatEntity(
                playerId = saka, season = season, teamName = team, leagueName = "Premier League",
                appearances = apps, goals = goals, assists = assists, minutesPlayed = apps * 80,
                rating = rating, yellowCards = 2, redCards = 0
            )
        )

    private fun match(fixtureId: Long, home: Int, away: Int, date: String, score: String = "2-1") = matches.save(
        MatchEntity(
            fixtureId = fixtureId, homeTeam = if (home == arsenal) "Arsenal" else "Chelsea",
            awayTeam = if (away == arsenal) "Arsenal" else "Chelsea", homeTeamId = home, awayTeamId = away,
            matchDate = date, matchTime = "20:00 (종료)", score = score, leagueId = 39, season = 2025
        )
    )

    private fun stat(fixtureId: Long, minutes: Int?, passesTotal: Int = 0, passesAccuracy: Int = 0, keyPasses: Int = 0, rating: Double? = null): PlayerStatEntity {
        val entity = PlayerStatEntity()
        entity.fixtureId = fixtureId
        entity.playerId = saka
        entity.playerName = "Bukayo Saka"
        entity.teamId = arsenal
        entity.minutesPlayed = minutes
        entity.rating = rating
        entity.passesTotal = passesTotal
        entity.passesAccuracy = passesAccuracy
        entity.passesKey = keyPasses
        return playerStats.save(entity)
    }

    private fun event(fixtureId: Long, type: String, detail: String?, player: String?, assist: String? = null) =
        events.save(
            MatchEventEntity(
                fixtureId = fixtureId, time = 30, extraTime = null, teamId = arsenal, teamName = "Arsenal",
                type = type, detail = detail, playerName = player, assistPlayerName = assist
            )
        )

    // ── 없는 선수 ───────────────────────────────────────────────

    @Test
    fun `없는 선수는 외부 호출 없이 404이고 선수 경력 스텁 주소는 사라졌다`() {
        // 외부 API 기대값이 없으므로 호출이 나가면 502가 된다
        listOf("/detail", "/profile", "/matches", "/season-stats", "/match-stats?fixtureId=1").forEach { path ->
            mockMvc.get("/api/soccer/player/9999$path").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PLAYER_NOT_FOUND") }
            }
        }
        mockMvc.get("/api/soccer/player/$saka/career").andExpect { status { isNotFound() } }
    }

    // ── 프로필 ──────────────────────────────────────────────────

    @Test
    fun `프로필은 시즌별 커리어를 최신 시즌부터 돌려준다`() {
        season(2024, apps = 35, goals = 14, assists = 11)
        season(2025, apps = 30, goals = 10, assists = 8)

        mockMvc.get("/api/soccer/player/$saka/profile").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Bukayo Saka") }
            jsonPath("$.currentTeamName") { value("Arsenal") }
            jsonPath("$.careerStats[0].season") { value(2025) }
            jsonPath("$.careerStats[0].goals") { value(10) }
            jsonPath("$.careerStats[1].season") { value(2024) }
        }
    }

    // ── 세부 페이지 ─────────────────────────────────────────────

    @Test
    fun `세부 페이지는 외부 정보와 이번 시즌 스탯과 트로피를 합쳐 돌려준다`() {
        season(2025)
        season(2023, goals = 5)
        champions.save(ChampionEntity(leagueId = 39, season = 2023, teamId = arsenal, teamName = "Arsenal"))
        champions.save(ChampionEntity(leagueId = 39, season = 2022, teamId = chelsea, teamName = "Chelsea"))   // 뛰지 않은 팀: 제외
        footballApi.expect(requestTo("$base/players?id=$saka&season=2025")).andRespond(
            ok(
                """{"errors":[],"response":[{"player":{"id":10,"height":"178 cm","birth":{"date":"2001-09-05"},
                "nationality":"England","age":24,"foot":"Left"},"statistics":[{"games":{"number":7}}]}]}"""
            )
        )

        mockMvc.get("/api/soccer/player/$saka/detail").andExpect {
            status { isOk() }
            jsonPath("$.height") { value("178 cm") }
            jsonPath("$.birthDate") { value("2001-09-05") }
            jsonPath("$.squadNumber") { value(7) }
            jsonPath("$.preferredFoot") { value("Left") }
            jsonPath("$.currentTeamName") { value("Arsenal") }
            jsonPath("$.currentSeasonStats.season") { value(2025) }
            jsonPath("$.trophies.length()") { value(1) }
            jsonPath("$.trophies[0].count") { value(1) }
            jsonPath("$.trophies[0].seasons[0]") { value("2023/24") }
        }
    }

    @Test
    fun `외부 API가 실패해도 세부 페이지는 외부 정보만 비우고 돌려준다`() {
        season(2025)
        // 최근 시즌 두 곳(2025, 2023 등 기록된 시즌)을 모두 시도한 뒤 포기한다
        footballApi.expect(requestTo("$base/players?id=$saka&season=2025")).andRespond(withServerError())
        footballApi.expect(requestTo("$base/players?id=$saka&season=2025")).andRespond(withServerError())

        mockMvc.get("/api/soccer/player/$saka/detail").andExpect {
            status { isOk() }
            jsonPath("$.height") { doesNotExist() }
            jsonPath("$.name") { value("Bukayo Saka") }
            jsonPath("$.currentSeasonStats.goals") { value(10) }
        }
    }

    // ── 경기 기록 ───────────────────────────────────────────────

    @Test
    fun `경기 기록은 출전 상태와 득점 도움 카드를 이벤트와 출전 시간으로 계산한다`() {
        season(2025)
        match(1, arsenal, chelsea, "2025-09-01")   // 선발: 90분, 득점 1, 도움 1
        match(2, chelsea, arsenal, "2025-09-08")   // 교체 출전: 25분
        match(3, arsenal, chelsea, "2025-09-15")   // 벤치: 0분
        match(4, chelsea, arsenal, "2025-09-22")   // 기록 없음
        stat(1, minutes = 90, rating = 8.1)
        stat(2, minutes = 25, rating = 6.5)
        stat(3, minutes = 0)
        event(1, "Goal", "Normal Goal", player = "B. Saka")
        event(1, "Goal", "Normal Goal", player = "M. Odegaard", assist = "B. Saka")
        event(1, "Card", "Yellow Card", player = "B. Saka")
        event(2, "Card", "Red Card", player = "B. Saka")

        mockMvc.get("/api/soccer/player/$saka/matches").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
            jsonPath("$[0].matchDate") { value("2025-09-22") }
            jsonPath("$[0].status") { value("NO_DATA") }
            jsonPath("$[1].status") { value("BENCH") }
            jsonPath("$[2].status") { value("SUBBED_ON") }
            jsonPath("$[2].redCards") { value(1) }
            jsonPath("$[2].isHome") { value(false) }
            jsonPath("$[2].opponentName") { value("Chelsea") }
            jsonPath("$[2].opponentLogo") { value("chelsea.png") }
            jsonPath("$[3].status") { value("STARTED") }
            jsonPath("$[3].goals") { value(1) }
            jsonPath("$[3].assists") { value(1) }
            jsonPath("$[3].yellowCards") { value(1) }
            jsonPath("$[3].minutesPlayed") { value(90) }
            jsonPath("$[3].rating") { value(8.1) }
        }
    }

    @Test
    fun `시즌 기록이 없는 선수의 경기 기록은 빈 목록이다`() {
        mockMvc.get("/api/soccer/player/$saka/matches").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    // ── 시즌별 통계 ─────────────────────────────────────────────

    @Test
    fun `시즌별 통계는 그 시즌 경기별 기록을 합산한다`() {
        season(2025)
        match(1, arsenal, chelsea, "2025-09-01")
        match(2, chelsea, arsenal, "2025-09-08")
        stat(1, minutes = 90, passesTotal = 50, passesAccuracy = 40, keyPasses = 3)
        stat(2, minutes = 70, passesTotal = 30, passesAccuracy = 20, keyPasses = 1)
        stat(99, minutes = 90, passesTotal = 100, passesAccuracy = 100, keyPasses = 9)   // 이 시즌 경기가 아님: 제외

        mockMvc.get("/api/soccer/player/$saka/season-stats").andExpect {
            status { isOk() }
            jsonPath("$[0].season") { value(2025) }
            jsonPath("$[0].appearances") { value(30) }
            jsonPath("$[0].passesTotal") { value(80) }
            jsonPath("$[0].passesKey") { value(4) }
            jsonPath("$[0].passesAccuracyAvg") { value(75.0) }   // 성공 60 / 전체 80
            jsonPath("$[0].matchesWithData") { value(2) }
        }
    }

    // ── 한 경기 기록 ────────────────────────────────────────────

    private val fixtureDetails = """
        {"errors":[],"response":[{
          "players":[{"team":{"name":"Arsenal"},"players":[
            {"player":{"id":10,"name":"B. Saka"},"statistics":[{"games":{"minutes":90,"position":"F","rating":"8.1"},
              "goals":{"total":1,"assists":1},"passes":{"total":50,"key":3,"accuracy":"40"},"shots":{"total":4,"on":2},
              "tackles":{"total":2,"blocks":0,"interceptions":1},"duels":{"total":10,"won":6},
              "dribbles":{"attempts":5,"success":3},"fouls":{"drawn":2,"committed":1}}]}]}],
          "statistics":[{"team":{"name":"Arsenal"},"statistics":[{"type":"expected_goals","value":"2.10"}]},
                        {"team":{"name":"Chelsea"},"statistics":[{"type":"expected_goals","value":"0.85"}]}]
        }]}
    """.trimIndent()

    @Test
    fun `한 경기 기록은 외부 경기 상세에서 선수 한 명의 기록과 상대 xG를 읽는다`() {
        footballApi.expect(requestTo("$base/fixtures?id=500")).andRespond(ok(fixtureDetails))

        mockMvc.get("/api/soccer/player/$saka/match-stats?fixtureId=500").andExpect {
            status { isOk() }
            jsonPath("$.teamName") { value("Arsenal") }
            jsonPath("$.minutesPlayed") { value(90) }
            jsonPath("$.rating") { value(8.1) }
            jsonPath("$.goals") { value(1) }
            jsonPath("$.passesAccuracy") { value(40) }
            jsonPath("$.shotsOn") { value(2) }
            jsonPath("$.nationality") { value("England") }   // 경기 응답에 없어 DB 값으로 채움
            jsonPath("$.opponentXg") { value(0.85) }
        }
    }

    @Test
    fun `경기에 기록이 없으면 404이고 외부 API가 실패하면 502이다`() {
        footballApi.expect(requestTo("$base/fixtures?id=501")).andRespond(ok("""{"errors":[],"response":[]}"""))
        mockMvc.get("/api/soccer/player/$saka/match-stats?fixtureId=501").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PLAYER_MATCH_STATS_NOT_FOUND") }
        }

        freshFootballApi().expect(requestTo("$base/fixtures?id=502")).andRespond(withServerError())
        mockMvc.get("/api/soccer/player/$saka/match-stats?fixtureId=502").andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("FOOTBALL_API_ERROR") }
        }
    }
}
