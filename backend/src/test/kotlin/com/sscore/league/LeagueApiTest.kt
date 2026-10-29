package com.sscore.league

import com.sscore.entity.MatchEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

class LeagueApiTest : ApiTest() {

    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var stats: PlayerSeasonStatRepository

    @BeforeEach
    fun clean() {
        stats.deleteAll()
        players.deleteAll()
        matches.deleteAll()
        teams.deleteAll()
    }

    private fun match(
        fixtureId: Long, home: String, away: String, score: String, date: String,
        time: String = "20:00 (종료)", leagueId: Int = 39, season: Int = 2025, homeId: Int? = null, awayId: Int? = null
    ) = matches.save(
        MatchEntity(
            fixtureId = fixtureId, homeTeam = home, awayTeam = away, homeTeamId = homeId, awayTeamId = awayId,
            matchDate = date, matchTime = time, score = score, leagueId = leagueId, season = season
        )
    )

    private fun player(id: Long, name: String, team: TeamEntity?) =
        players.save(PlayerEntity(playerId = id, name = name, nationality = null, photoUrl = "p$id.png", position = null, team = team))

    private fun stat(
        playerId: Long, team: String, apps: Int, goals: Int, assists: Int, rating: Double?,
        league: String = "Premier League", season: Int = 2025
    ) = stats.save(
        PlayerSeasonStatEntity(
            playerId = playerId, season = season, teamName = team, leagueName = league,
            appearances = apps, goals = goals, assists = assists, minutesPlayed = apps * 80,
            rating = rating, yellowCards = 0, redCards = 0
        )
    )

    // ── 지원 리그 ───────────────────────────────────────────────

    @Test
    fun `지원하는 리그 목록은 5대 리그이다`() {
        mockMvc.get("/api/soccer/leagues").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(5) }
            jsonPath("$[0].leagueId") { value(39) }
            jsonPath("$[0].name") { value("Premier League") }
            jsonPath("$[4].leagueId") { value(61) }
        }
    }

    @Test
    fun `리그 기본 정보를 돌려주고 지원하지 않는 리그는 외부 호출 없이 404이다`() {
        mockMvc.get("/api/soccer/league/39").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Premier League") }
            jsonPath("$.country") { value("England") }
        }
        // 외부 API 기대값이 없으므로 호출이 나가면 502가 된다 — 404가 나오면 호출 전에 막힌 것이다
        listOf(
            "/api/soccer/league/999", "/api/soccer/league/999/matches", "/api/soccer/league/999/seasons",
            "/api/soccer/league/999/player-stats", "/api/soccer/league/999/team-stats", "/api/soccer/standings?leagueId=999"
        ).forEach { url ->
            mockMvc.get(url).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("LEAGUE_NOT_FOUND") }
            }
        }
    }

    // ── 시즌 ────────────────────────────────────────────────────

    @Test
    fun `리그의 시즌 목록은 데이터가 있는 시즌만 최신순이고 기본 시즌을 함께 준다`() {
        match(1, "A", "B", "1-0", "2024-09-01", season = 2024)
        match(2, "A", "B", "1-0", "2025-09-01", season = 2025)
        match(3, "C", "D", "1-0", "2023-09-01", leagueId = 140, season = 2023)

        mockMvc.get("/api/soccer/league/39/seasons").andExpect {
            jsonPath("$.default") { value(2025) }
            jsonPath("$.seasons[0]") { value(2025) }
            jsonPath("$.seasons[1]") { value(2024) }
            jsonPath("$.seasons.length()") { value(2) }
        }
        // 이 리그에는 현재 시즌 데이터가 없으면 데이터가 있는 시즌이 기본이 된다
        mockMvc.get("/api/soccer/league/140/seasons").andExpect {
            jsonPath("$.default") { value(2023) }
            jsonPath("$.seasons[0]") { value(2023) }
        }
    }

    // ── 경기 ────────────────────────────────────────────────────

    @Test
    fun `리그 경기는 날짜별로 묶여 최신 날짜부터 나오고 팀 로고가 붙는다`() {
        teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(49, "Chelsea", "England", "chelsea.png"))
        match(1, "Arsenal", "Chelsea", "2-1", "2025-09-01", time = "20:00 (종료)", homeId = 42, awayId = 49)
        match(2, "Chelsea", "Arsenal", "0-0", "2025-09-01", time = "17:00 (종료)", homeId = 49, awayId = 42)
        match(3, "Arsenal", "Chelsea", "1-1", "2025-10-05", homeId = 42, awayId = 49)
        match(4, "Arsenal", "Chelsea", "3-0", "2024-09-01", season = 2024, homeId = 42, awayId = 49)

        mockMvc.get("/api/soccer/league/39/matches?season=2025").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].date") { value("2025-10-05") }
            jsonPath("$[1].date") { value("2025-09-01") }
            jsonPath("$[1].matches.length()") { value(2) }
            jsonPath("$[1].matches[0].matchTime") { value("20:00 (종료)") }
            jsonPath("$[0].matches[0].homeLogoUrl") { value("arsenal.png") }
            jsonPath("$[0].matches[0].awayLogoUrl") { value("chelsea.png") }
        }
    }

    // ── 선수 통계 ───────────────────────────────────────────────

    @Test
    fun `선수 랭킹은 득점 도움 공격포인트 평점 순이고 평점은 15경기 이상만 센다`() {
        val team = teams.save(TeamEntity(42, "Arsenal", "England", "a.png"))
        player(1, "스트라이커", team); player(2, "플레이메이커", team); player(3, "신예", team); player(4, "다른리그", team)
        stat(1, "Arsenal", apps = 30, goals = 20, assists = 3, rating = 7.5)
        stat(1, "Chelsea", apps = 5, goals = 3, assists = 0, rating = 6.0)       // 같은 선수의 다른 기록: 출장수가 많은 쪽만 쓴다
        stat(2, "Arsenal", apps = 28, goals = 15, assists = 12, rating = 7.9)
        stat(3, "Arsenal", apps = 10, goals = 5, assists = 1, rating = 8.5)      // 15경기 미만: 평점 랭킹 제외
        stat(4, "Real Madrid", apps = 30, goals = 40, assists = 0, rating = 8.0, league = "La Liga")   // 다른 리그

        mockMvc.get("/api/soccer/league/39/player-stats?season=2025").andExpect {
            status { isOk() }
            jsonPath("$.goalRanking[0].playerName") { value("스트라이커") }
            jsonPath("$.goalRanking[0].value") { value(20.0) }
            jsonPath("$.goalRanking[0].rank") { value(1) }
            jsonPath("$.goalRanking[1].playerName") { value("플레이메이커") }
            jsonPath("$.goalRanking[2].playerName") { value("신예") }
            jsonPath("$.goalRanking.length()") { value(3) }
            jsonPath("$.assistRanking[0].playerName") { value("플레이메이커") }
            jsonPath("$.attackPointRanking[0].playerName") { value("플레이메이커") }   // 15+12=27 > 20+3=23
            jsonPath("$.attackPointRanking[0].value") { value(27.0) }
            jsonPath("$.ratingRanking[0].playerName") { value("플레이메이커") }
            jsonPath("$.ratingRanking[1].playerName") { value("스트라이커") }
            jsonPath("$.ratingRanking.length()") { value(2) }
        }
    }

    // ── 팀 통계 ─────────────────────────────────────────────────

    @Test
    fun `팀 통계는 끝난 경기만 모아 경기당 득점 실점 클린시트와 평균 평점을 낸다`() {
        teams.save(TeamEntity(1, "X", "England", "x.png"))
        teams.save(TeamEntity(2, "Y", "England", "y.png"))
        match(1, "X", "Y", "2-0", "2025-09-01", homeId = 1, awayId = 2)
        match(2, "Y", "X", "1-1", "2025-09-08", homeId = 2, awayId = 1)
        match(3, "X", "Y", "3-1", "2025-09-15", homeId = 1, awayId = 2)
        match(4, "X", "Y", "9-9", "2027-01-01", homeId = 1, awayId = 2)           // 아직 오지 않은 날짜: 제외
        match(5, "X", "Y", "- : -", "2025-12-01", time = "예정", homeId = 1, awayId = 2)   // 점수를 읽을 수 없음: 제외
        player(10, "X선수", null); player(11, "Y선수", null)
        stat(10, "X", apps = 20, goals = 5, assists = 1, rating = 7.4)
        stat(11, "Y", apps = 20, goals = 2, assists = 1, rating = 6.8)

        mockMvc.get("/api/soccer/league/39/team-stats?season=2025").andExpect {
            status { isOk() }
            // X: 3경기 6득점 2실점 클린시트 1 / Y: 3경기 2득점 6실점 클린시트 0
            jsonPath("$.goalsPerGameRanking[0].teamName") { value("X") }
            jsonPath("$.goalsPerGameRanking[0].displayValue") { value("2.00") }
            jsonPath("$.goalsPerGameRanking[0].logoUrl") { value("x.png") }
            jsonPath("$.concededPerGameRanking[0].teamName") { value("X") }
            jsonPath("$.concededPerGameRanking[0].displayValue") { value("0.67") }
            jsonPath("$.cleanSheetRanking[0].teamName") { value("X") }
            jsonPath("$.cleanSheetRanking[0].displayValue") { value("1회") }
            jsonPath("$.avgRatingRanking[0].teamName") { value("X") }
            jsonPath("$.avgRatingRanking[0].displayValue") { value("7.40") }
            jsonPath("$.avgRatingRanking[1].teamName") { value("Y") }
        }
    }

    @Test
    fun `시즌을 보내지 않으면 데이터가 있는 최근 시즌으로 통계를 낸다`() {
        match(1, "X", "Y", "1-0", "2025-09-01")
        mockMvc.get("/api/soccer/league/39/team-stats").andExpect {
            status { isOk() }
            jsonPath("$.goalsPerGameRanking[0].teamName") { value("X") }
        }
    }
}
