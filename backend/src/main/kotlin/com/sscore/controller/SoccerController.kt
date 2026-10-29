package com.sscore.controller

import com.sscore.dto.*
import com.sscore.service.SoccerService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/soccer")
class SoccerController(
    private val soccerService: SoccerService
) {

    // 1. 전체 경기 데이터 DB에서 가져오기
    @GetMapping("/matches")
    fun getMatches(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) leagueId: Int?
    ): List<MatchDto> {
        return if (date != null) {
            soccerService.getMatchesByDate(date, leagueId)
        } else {
            soccerService.getAllMatchesFromDb()
        }
    }

    // 3. 상세 페이지용 API
    // 🌟 안드로이드 ApiService 규격과 일치하도록 List<LineupDto> 반환 타입 강제 지정
    @GetMapping("/lineups/{fixtureId}")
    fun getLineups(@PathVariable fixtureId: Long): List<LineupDto> {
        return soccerService.getLineups(fixtureId)
    }

    @GetMapping("/h2h")
    fun getH2H(@RequestParam homeId: Int, @RequestParam awayId: Int) =
        soccerService.getH2H(homeId, awayId)

    @GetMapping("/matches/{fixtureId}/info")
    fun getMatchInfo(@PathVariable fixtureId: Long): MatchInfoResponseDto {
        return soccerService.getMatchInfo(fixtureId)
    }
}
