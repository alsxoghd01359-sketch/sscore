package com.sscore.search

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

class SearchApiTest : ApiTest() {

    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var players: PlayerRepository
    @Autowired private lateinit var stats: PlayerSeasonStatRepository
    @Autowired private lateinit var matches: MatchRepository

    @BeforeEach
    fun seed() {
        stats.deleteAll(); players.deleteAll(); matches.deleteAll(); teams.deleteAll()
        val arsenal = teams.save(TeamEntity(42, "Arsenal", "England", "arsenal.png"))
        val city = teams.save(TeamEntity(50, "Manchester City", "England", "city.png"))
        val barca = teams.save(TeamEntity(529, "Barcelona", "Spain", "barca.png"))
        listOf(42 to 39, 50 to 39, 529 to 140).forEachIndexed { i, (teamId, leagueId) ->
            matches.save(
                MatchEntity(
                    fixtureId = 1L + i, homeTeam = "T$teamId", awayTeam = "X", homeTeamId = teamId, awayTeamId = 9999,
                    matchDate = "2025-09-0${i + 1}", matchTime = "20:00 (종료)", score = "1-0", leagueId = leagueId, season = 2025
                )
            )
        }
        player(1, "E. Haaland", city, "Premier League")
        player(2, "V. Gyökeres", arsenal, "Premier League")
        player(3, "Lamine Yamal", barca, "La Liga")
    }

    private fun player(id: Long, name: String, team: TeamEntity, league: String) {
        players.save(PlayerEntity(playerId = id, name = name, nationality = null, photoUrl = "p$id.png", position = null, team = team))
        stats.save(
            PlayerSeasonStatEntity(
                playerId = id, season = 2025, teamName = team.name, leagueName = league, appearances = 20, goals = 5,
                assists = 2, minutesPlayed = 1500, rating = 7.0, yellowCards = 0, redCards = 0
            )
        )
    }

    @Test
    fun `영어 팀 이름으로 찾으면 구단 결과에 한글 이름과 리그가 붙는다`() {
        mockMvc.get("/api/soccer/search?q=arsen").andExpect {
            status { isOk() }
            jsonPath("$[?(@.type=='TEAM')].name") { value("Arsenal") }
            jsonPath("$[?(@.type=='TEAM')].nameKo") { value("아스널") }
            jsonPath("$[?(@.type=='TEAM')].subName") { value("프리미어리그") }
            jsonPath("$[?(@.type=='TEAM')].leagueId") { value(39) }
            jsonPath("$[?(@.type=='TEAM')].id") { value(42) }
        }
    }

    @Test
    fun `한글 팀 이름과 줄임말로도 찾는다`() {
        mockMvc.get("/api/soccer/search?q=아스널").andExpect {
            status { isOk() }
            jsonPath("$[?(@.type=='TEAM')].name") { value("Arsenal") }
        }
        mockMvc.get("/api/soccer/search?q=아스날").andExpect {   // 흔한 다른 표기
            jsonPath("$[?(@.type=='TEAM')].name") { value("Arsenal") }
        }
        mockMvc.get("/api/soccer/search?q=맨시티").andExpect {
            jsonPath("$[?(@.type=='TEAM')].name") { value("Manchester City") }
            jsonPath("$[?(@.type=='TEAM')].nameKo") { value("맨체스터 시티") }   // 화면에는 정식 한글 이름
        }
        mockMvc.get("/api/soccer/search?q=바르").andExpect {
            jsonPath("$[?(@.type=='TEAM')].name") { value("Barcelona") }
            jsonPath("$[?(@.type=='TEAM')].subName") { value("라리가") }
        }
    }

    @Test
    fun `리그는 영어와 한글 이름으로 찾는다`() {
        mockMvc.get("/api/soccer/search?q=premier").andExpect {
            jsonPath("$[?(@.type=='LEAGUE')].id") { value(39) }
            jsonPath("$[?(@.type=='LEAGUE')].name") { value("Premier League") }
            jsonPath("$[?(@.type=='LEAGUE')].nameKo") { value("프리미어리그") }
        }
        mockMvc.get("/api/soccer/search?q=라리가").andExpect {
            jsonPath("$[?(@.type=='LEAGUE')].id") { value(140) }
        }
    }

    @Test
    fun `선수는 대소문자와 악센트를 구분하지 않고 찾고 소속팀도 구단 결과로 보여 준다`() {
        mockMvc.get("/api/soccer/search?q=GYOKERES").andExpect {   // 악센트(ö)가 있는 이름을 악센트 없이
            status { isOk() }
            jsonPath("$[0].type") { value("PLAYER") }
            jsonPath("$[0].name") { value("V. Gyökeres") }
            jsonPath("$[0].subName") { value("Arsenal") }
            jsonPath("$[?(@.type=='TEAM')].name") { value("Arsenal") }
        }
        mockMvc.get("/api/soccer/search?q=haaland").andExpect {
            jsonPath("$[0].name") { value("E. Haaland") }
        }
    }

    @Test
    fun `결과는 선수 구단 리그 순서이고 같은 종류 안에서는 리그 순서로 정렬된다`() {
        mockMvc.get("/api/soccer/search?q=a").andExpect { jsonPath("$.length()") { value(0) } }   // 1글자는 검색하지 않는다

        val body = mockMvc.get("/api/soccer/search?q=an").andReturn().response.contentAsString
        // "an" 은 Manchester City, Lamine Yamal(Barcelona 소속)처럼 여러 종류에 걸쳐 걸린다
        val types = com.jayway.jsonpath.JsonPath.read<List<String>>(body, "$[*].type")
        assert(types == types.sortedBy { listOf("PLAYER", "TEAM", "LEAGUE").indexOf(it) }) { "종류 순서가 어긋남: $types" }
        val teamLeagues = com.jayway.jsonpath.JsonPath.read<List<Int?>>(body, "$[?(@.type=='TEAM')].leagueId")
        assert(teamLeagues == teamLeagues.sortedBy { it ?: 99 }) { "구단이 리그 순서가 아님: $teamLeagues" }
    }

    @Test
    fun `선수 결과는 10명까지이고 검색어로 시작하는 이름이 먼저 나온다`() {
        val team = teams.findById(42).get()
        (10..24).forEach { i -> player(i.toLong(), "Smith N$i", team, "Premier League") }
        player(100, "Zed Smithson", team, "Premier League")
        player(101, "Smith A", team, "Premier League")

        val body = mockMvc.get("/api/soccer/search?q=smith").andReturn().response.contentAsString
        val names = com.jayway.jsonpath.JsonPath.read<List<String>>(body, "$[?(@.type=='PLAYER')].name")
        assert(names.size == 10) { "선수는 10명까지: ${names.size}" }
        assert(names.none { it == "Zed Smithson" }) { "검색어로 시작하는 이름이 먼저여야 함: $names" }
        assert(names.first() == "Smith A") { "이름순: $names" }
    }

    @Test
    fun `결과가 없으면 빈 목록이다`() {
        mockMvc.get("/api/soccer/search?q=존재하지않는이름").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mockMvc.get("/api/soccer/search?q=zzzzzz").andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `검색어가 없으면 400이다`() {
        mockMvc.get("/api/soccer/search").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }
}
