package com.sscore

import com.sscore.entity.MatchEntity
import com.sscore.repository.MatchRepository
import com.sscore.support.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

class SscoreServerApplicationTests : ApiTest() {

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @BeforeEach
    fun seed() {
        matchRepository.deleteAll()
        matchRepository.save(match(fixtureId = 1001, date = "2026-03-01", leagueId = 39, home = "Arsenal", away = "Chelsea"))
        matchRepository.save(match(fixtureId = 1002, date = "2026-03-01", leagueId = 140, home = "Barcelona", away = "Valencia"))
        matchRepository.save(match(fixtureId = 1003, date = "2026-03-02", leagueId = 39, home = "Liverpool", away = "Everton"))
    }

    @Test
    fun `날짜로 경기 목록을 조회한다`() {
        mockMvc.get("/api/soccer/matches?date=2026-03-01").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].homeTeam") { value("Arsenal") }
        }
    }

    @Test
    fun `날짜와 리그로 경기 목록을 필터링한다`() {
        mockMvc.get("/api/soccer/matches?date=2026-03-01&leagueId=140").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].homeTeam") { value("Barcelona") }
        }
    }

    private fun match(fixtureId: Long, date: String, leagueId: Int, home: String, away: String) = MatchEntity(
        fixtureId = fixtureId, homeTeam = home, awayTeam = away, matchDate = date,
        matchTime = "20:00", score = "- : -", leagueId = leagueId, season = 2025
    )
}
