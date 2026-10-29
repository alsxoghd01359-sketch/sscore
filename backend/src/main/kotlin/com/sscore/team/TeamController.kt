package com.sscore.team

import com.sscore.dto.SquadDto
import com.sscore.dto.TeamMatchesPageDto
import com.sscore.dto.TeamOverviewDto
import com.sscore.dto.TeamStatsDto
import com.sscore.dto.TrophyDto
import com.sscore.football.SeasonResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 팀 상세 화면용 조회. leagueId/season을 보내지 않으면 팀의 리그와 데이터가 있는 최근 시즌을 쓴다.
@RestController
@RequestMapping("/api/soccer/team/{teamId}")
class TeamController(
    private val teams: TeamService,
    private val seasons: SeasonResolver
) {
    /** 팀 기본 정보 + 소속 리그 */
    @GetMapping
    fun info(@PathVariable teamId: Int): TeamInfoDto = teams.getInfo(teamId)

    /** 구단 트로피 (champions 테이블) */
    @GetMapping("/trophies")
    fun trophies(@PathVariable teamId: Int): List<TrophyDto> = teams.getTrophies(teamId)

    /** 구단 현재 스쿼드 */
    @GetMapping("/squad")
    fun squad(@PathVariable teamId: Int): SquadDto = teams.getSquad(teamId)

    /** 구단 통계: 홈/원정 성적 + 평점/득점/도움 랭킹 */
    @GetMapping("/stats")
    fun stats(
        @PathVariable teamId: Int,
        @RequestParam(required = false) leagueId: Int?,
        @RequestParam(required = false) season: Int?
    ): TeamStatsDto = teams.getStats(teamId, leagueId, seasons.resolve(season))

    /** 구단 경기 목록 (페이지네이션, 최신순) */
    @GetMapping("/matches")
    fun matches(
        @PathVariable teamId: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int
    ): TeamMatchesPageDto = teams.getMatches(teamId, page, size)

    /** 구단 개요: 최근 5경기 + 미니순위 + 경기장 + 리그트로피 */
    @GetMapping("/overview")
    fun overview(
        @PathVariable teamId: Int,
        @RequestParam(required = false) leagueId: Int?,
        @RequestParam(required = false) season: Int?
    ): TeamOverviewDto = teams.getOverview(teamId, leagueId, seasons.resolve(season))
}
