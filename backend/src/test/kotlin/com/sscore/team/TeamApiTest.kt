package com.sscore.team

import com.sscore.entity.ChampionEntity
import com.sscore.entity.MatchEntity
import com.sscore.entity.PlayerEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
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

class TeamApiTest : ApiTest() {

    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var matches: MatchRepository
    @Autowired private lateinit var champions: ChampionRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var stats: PlayerSeasonStatRepository

    private val base = "https://v3.football.api-sports.io"
    private val arsenal = 42
    private val chelsea = 49

    @BeforeEach
    fun seed() {
        stats.deleteAll(); players.deleteAll(); matches.deleteAll(); champions.deleteAll(); teams.deleteAll()
        teams.save(TeamEntity(arsenal, "Arsenal", "England", "arsenal.png"))
        teams.save(TeamEntity(chelsea, "Chelsea", "England", "chelsea.png"))
    }

    private fun ok(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun match(
        fixtureId: Long, home: Int, away: Int, score: String, date: String,
        time: String = "20:00 (종료)", leagueId: Int = 39, season: Int = 2025
    ) = matches.save(
        MatchEntity(
            fixtureId = fixtureId, homeTeam = if (home == arsenal) "Arsenal" else "Chelsea",
            awayTeam = if (away == arsenal) "Arsenal" else "Chelsea", homeTeamId = home, awayTeamId = away,
            matchDate = date, matchTime = time, score = score, leagueId = leagueId, season = season
        )
    )

    private fun player(id: Long, name: String, nationality: String? = null) =
        players.save(PlayerEntity(playerId = id, name = name, nationality = nationality, photoUrl = "p$id.png", position = null, team = teams.findById(arsenal).get()))

    private fun stat(playerId: Long, apps: Int, goals: Int, assists: Int, rating: Double?, team: String = "Arsenal") =
        stats.save(
            PlayerSeasonStatEntity(
                playerId = playerId, season = 2025, teamName = team, leagueName = "Premier League",
                appearances = apps, goals = goals, assists = assists, minutesPlayed = apps * 80,
                rating = rating, yellowCards = 0, redCards = 0
            )
        )

    private val standings = """
        {"errors":[],"response":[{"league":{"id":39,"season":2025,"standings":[[
          {"rank":1,"team":{"id":50,"name":"Manchester City"},"points":80,"goalsDiff":40,"all":{"played":38,"win":25,"draw":5,"lose":8},"goals":{"for":70,"against":30}},
          {"rank":2,"team":{"id":42,"name":"Arsenal"},"points":78,"goalsDiff":35,"all":{"played":38,"win":24,"draw":6,"lose":8},"goals":{"for":75,"against":40},
           "home":{"played":19,"win":15,"draw":2,"lose":2,"goals":{"for":45,"against":15}},
           "away":{"played":19,"win":9,"draw":4,"lose":6,"goals":{"for":30,"against":25}}},
          {"rank":3,"team":{"id":49,"name":"Chelsea"},"points":70,"goalsDiff":20,"all":{"played":38,"win":20,"draw":10,"lose":8},"goals":{"for":60,"against":40}},
          {"rank":4,"team":{"id":47,"name":"Tottenham"},"points":65,"goalsDiff":10,"all":{"played":38,"win":19,"draw":8,"lose":11},"goals":{"for":55,"against":45}}
        ]]}}]}
    """.trimIndent()

    // ── 기본 정보 ───────────────────────────────────────────────

    @Test
    fun `팀 정보에는 가장 최근에 뛴 지원 리그가 붙는다`() {
        match(1, arsenal, chelsea, "1-0", "2024-09-01", leagueId = 39, season = 2024)
        match(2, arsenal, chelsea, "1-0", "2025-09-01", leagueId = 39, season = 2025)

        mockMvc.get("/api/soccer/team/$arsenal").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Arsenal") }
            jsonPath("$.logoUrl") { value("arsenal.png") }
            jsonPath("$.leagueId") { value(39) }
            jsonPath("$.leagueName") { value("프리미어리그") }
        }
    }

    @Test
    fun `없는 팀은 외부 호출 없이 404이다`() {
        // 외부 API 기대값이 없으므로 호출이 나가면 502가 된다
        listOf("", "/trophies", "/squad", "/stats", "/matches", "/overview").forEach { path ->
            mockMvc.get("/api/soccer/team/9999$path").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("TEAM_NOT_FOUND") }
            }
        }
    }

    @Test
    fun `지원하지 않는 리그를 지정하면 404이다`() {
        mockMvc.get("/api/soccer/team/$arsenal/stats?leagueId=999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("LEAGUE_NOT_FOUND") }
        }
        mockMvc.get("/api/soccer/team/$arsenal/overview?leagueId=999").andExpect { status { isNotFound() } }
    }

    // ── 트로피 ──────────────────────────────────────────────────

    @Test
    fun `트로피는 리그별 우승 횟수와 마지막 우승 시즌이다`() {
        champions.save(ChampionEntity(leagueId = 39, season = 2003, teamId = arsenal, teamName = "Arsenal"))
        champions.save(ChampionEntity(leagueId = 39, season = 2023, teamId = arsenal, teamName = "Arsenal"))
        champions.save(ChampionEntity(leagueId = 39, season = 2010, teamId = chelsea, teamName = "Chelsea"))

        mockMvc.get("/api/soccer/team/$arsenal/trophies").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].league") { value("프리미어리그") }
            jsonPath("$[0].winCount") { value(2) }
            jsonPath("$[0].lastWinSeason") { value("2023/24") }
        }
    }

    // ── 스쿼드 ──────────────────────────────────────────────────

    @Test
    fun `스쿼드는 포지션별로 번호순이고 국적은 DB로 채운다`() {
        player(10, "Saka", "England")
        footballApi.expect(requestTo("$base/players/squads?team=$arsenal")).andRespond(
            ok(
                """{"errors":[],"response":[{"team":{"id":42},"players":[
                  {"id":10,"name":"Saka","number":7,"position":"Attacker","age":23},
                  {"id":11,"name":"Raya","number":22,"position":"Goalkeeper","age":28},
                  {"id":12,"name":"Saliba","number":2,"position":"Defender","age":24},
                  {"id":13,"name":"Odegaard","number":8,"position":"Midfielder","age":25}]}]}"""
            )
        )

        mockMvc.get("/api/soccer/team/$arsenal/squad").andExpect {
            status { isOk() }
            jsonPath("$.goalkeepers[0].name") { value("Raya") }
            jsonPath("$.defenders[0].name") { value("Saliba") }
            jsonPath("$.midfielders[0].name") { value("Odegaard") }
            jsonPath("$.attackers[0].name") { value("Saka") }
            jsonPath("$.attackers[0].nationality") { value("England") }
        }
    }

    @Test
    fun `스쿼드를 불러오지 못하면 502이다`() {
        footballApi.expect(requestTo("$base/players/squads?team=$arsenal")).andRespond(withServerError())
        mockMvc.get("/api/soccer/team/$arsenal/squad").andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("FOOTBALL_API_ERROR") }
        }
    }

    // ── 통계 ────────────────────────────────────────────────────

    @Test
    fun `통계는 홈 원정 성적과 선수 랭킹을 돌려준다`() {
        player(1, "스트라이커"); player(2, "공격수B"); player(3, "신예")
        stat(1, apps = 30, goals = 20, assists = 3, rating = 7.5)
        stat(2, apps = 28, goals = 20, assists = 12, rating = 7.9)   // 득점 공동 1위
        stat(3, apps = 10, goals = 5, assists = 1, rating = 8.5)     // 15경기 미만: 평점 랭킹 제외
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(ok(standings))

        mockMvc.get("/api/soccer/team/$arsenal/stats?leagueId=39&season=2025").andExpect {
            status { isOk() }
            jsonPath("$.homeRecord.played") { value(19) }
            jsonPath("$.homeRecord.win") { value(15) }
            jsonPath("$.homeRecord.points") { value(47) }
            jsonPath("$.homeRecord.goalsDiff") { value(30) }
            jsonPath("$.awayRecord.win") { value(9) }
            jsonPath("$.goalRanking[0].rank") { value(1) }
            jsonPath("$.goalRanking[1].rank") { value(1) }
            jsonPath("$.goalRanking[2].rank") { value(3) }
            jsonPath("$.assistRanking[0].playerName") { value("공격수B") }
            jsonPath("$.ratingRanking.length()") { value(2) }
            jsonPath("$.ratingRanking[0].playerName") { value("공격수B") }
        }
    }

    @Test
    fun `외부 API가 실패해도 통계는 홈 원정 성적만 비우고 돌려준다`() {
        player(1, "스트라이커")
        stat(1, apps = 30, goals = 20, assists = 3, rating = 7.5)
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(withServerError())

        mockMvc.get("/api/soccer/team/$arsenal/stats?leagueId=39&season=2025").andExpect {
            status { isOk() }
            jsonPath("$.homeRecord") { doesNotExist() }
            jsonPath("$.goalRanking[0].playerName") { value("스트라이커") }
        }
    }

    @Test
    fun `리그와 시즌을 보내지 않으면 팀의 리그와 최근 시즌을 쓴다`() {
        match(1, arsenal, chelsea, "1-0", "2025-09-01")
        player(1, "스트라이커")
        stat(1, apps = 30, goals = 20, assists = 3, rating = 7.5)
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(ok(standings))

        mockMvc.get("/api/soccer/team/$arsenal/stats").andExpect {
            status { isOk() }
            jsonPath("$.homeRecord.played") { value(19) }
        }
    }

    // ── 경기 ────────────────────────────────────────────────────

    @Test
    fun `팀 경기는 최신순으로 15개씩 나뉜다`() {
        (1..20).forEach { i -> match(i.toLong(), arsenal, chelsea, "1-0", "2025-09-%02d".format(i)) }

        mockMvc.get("/api/soccer/team/$arsenal/matches?page=0&size=15").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(15) }
            jsonPath("$.matches[0].matchDate") { value("2025-09-20") }
            jsonPath("$.hasMore") { value(true) }
            jsonPath("$.currentPage") { value(0) }
            jsonPath("$.totalPages") { value(2) }
        }
        mockMvc.get("/api/soccer/team/$arsenal/matches?page=1&size=15").andExpect {
            jsonPath("$.matches.length()") { value(5) }
            jsonPath("$.matches[4].matchDate") { value("2025-09-01") }
            jsonPath("$.hasMore") { value(false) }
        }
    }

    // ── 개요 ────────────────────────────────────────────────────

    @Test
    fun `개요는 최근 5경기 결과와 ±1 순위표와 경기장과 트로피를 돌려준다`() {
        match(1, arsenal, chelsea, "2-0", "2025-09-01")             // 승
        match(2, chelsea, arsenal, "1-1", "2025-09-08")             // 무
        match(3, chelsea, arsenal, "3-0", "2025-09-15")             // 패
        match(4, arsenal, chelsea, "1-0", "2025-09-22", time = "20:00")   // 아직 안 끝남: 제외
        champions.save(ChampionEntity(leagueId = 39, season = 2023, teamId = arsenal, teamName = "Arsenal"))
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(ok(standings))
        footballApi.expect(requestTo("$base/teams?id=$arsenal")).andRespond(
            ok("""{"errors":[],"response":[{"team":{"id":42},"venue":{"name":"Emirates Stadium","city":"London","capacity":60704}}]}""")
        )

        mockMvc.get("/api/soccer/team/$arsenal/overview?leagueId=39&season=2025").andExpect {
            status { isOk() }
            jsonPath("$.recentMatches.length()") { value(3) }
            jsonPath("$.recentMatches[0].result") { value("L") }
            jsonPath("$.recentMatches[1].result") { value("D") }
            jsonPath("$.recentMatches[2].result") { value("W") }
            jsonPath("$.miniStandings.length()") { value(3) }          // 1~3위: 맨시티, 아스널, 첼시
            jsonPath("$.miniStandings[1].teamName") { value("Arsenal") }
            jsonPath("$.venue.name") { value("Emirates Stadium") }
            jsonPath("$.leagueTrophies[0].winCount") { value(1) }
        }
    }

    @Test
    fun `외부 API가 실패해도 개요는 순위표와 경기장만 비우고 나머지를 보여 준다`() {
        match(1, arsenal, chelsea, "2-0", "2025-09-01")
        footballApi.expect(requestTo("$base/standings?league=39&season=2025")).andRespond(withServerError())
        footballApi.expect(requestTo("$base/teams?id=$arsenal")).andRespond(withServerError())

        mockMvc.get("/api/soccer/team/$arsenal/overview?leagueId=39&season=2025").andExpect {
            status { isOk() }
            jsonPath("$.recentMatches.length()") { value(1) }
            jsonPath("$.miniStandings.length()") { value(0) }
            jsonPath("$.venue") { doesNotExist() }
        }
    }
}
