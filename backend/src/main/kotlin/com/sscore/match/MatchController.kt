package com.sscore.match

import com.sscore.dto.H2HSummaryDto
import com.sscore.dto.LineupDto
import com.sscore.dto.MatchDto
import com.sscore.dto.MatchInfoResponseDto
import com.sscore.dto.toMatchDto
import com.sscore.repository.MatchRepository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 경기 목록 (저장된 경기에서 조회)
@Service
class MatchListService(private val matches: MatchRepository) {
    fun all(): List<MatchDto> = matches.findAll().map { it.toMatchDto() }

    // 날짜(yyyy-MM-dd) + 리그 필터. 리그 → 시간 순
    fun byDate(date: String, leagueId: Int?): List<MatchDto> {
        val found = if (leagueId != null) matches.findByMatchDateAndLeagueId(date, leagueId) else matches.findByMatchDate(date)
        return found.sortedWith(compareBy({ it.leagueId }, { it.matchTime })).map { it.toMatchDto() }
    }
}

@RestController
@RequestMapping("/api/soccer")
class MatchController(
    private val list: MatchListService,
    private val detail: MatchDetailService
) {
    /** 경기 목록: date를 보내면 그 날짜(+리그) 경기, 없으면 전체 */
    @GetMapping("/matches")
    fun matches(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) leagueId: Int?
    ): List<MatchDto> = if (date != null) list.byDate(date, leagueId) else list.all()

    /** 경기 정보: 타임라인 · 통계 · 평점 상위 선수 */
    @GetMapping("/matches/{fixtureId}/info")
    fun info(@PathVariable fixtureId: Long): MatchInfoResponseDto = detail.getInfo(fixtureId)

    /** 라인업 */
    @GetMapping("/lineups/{fixtureId}")
    fun lineups(@PathVariable fixtureId: Long): List<LineupDto> = detail.getLineups(fixtureId)

    /** 두 팀의 상대전적 */
    @GetMapping("/h2h")
    fun h2h(@RequestParam homeId: Int, @RequestParam awayId: Int): H2HSummaryDto = detail.getH2H(homeId, awayId)
}
