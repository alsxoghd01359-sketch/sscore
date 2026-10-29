package com.sscore.league

import com.sscore.dto.LeagueInfoDto
import com.sscore.dto.LeagueMatchDayDto
import com.sscore.dto.LeaguePlayerStatsDto
import com.sscore.dto.LeagueTeamStatsDto
import com.sscore.dto.StandingDto
import com.sscore.football.SeasonResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 리그 상세 화면용 조회. season을 보내지 않으면 데이터가 있는 가장 최근 시즌을 쓴다.
@RestController
@RequestMapping("/api/soccer")
class LeagueController(
    private val leagues: LeagueService,
    private val seasons: SeasonResolver
) {
    /** 지원하는 리그 목록 */
    @GetMapping("/leagues")
    fun listLeagues(): List<LeagueInfoDto> = leagues.listLeagues()

    /** 리그 기본 정보 */
    @GetMapping("/league/{leagueId}")
    fun getLeagueInfo(@PathVariable leagueId: Int): LeagueInfoDto? = leagues.getLeagueInfo(leagueId)

    /** 데이터가 있는 시즌 목록과 기본 시즌 */
    @GetMapping("/league/{leagueId}/seasons")
    fun getSeasons(@PathVariable leagueId: Int): LeagueSeasonsDto = leagues.getSeasons(leagueId)

    /** 순위표 */
    @GetMapping("/standings")
    fun getStandings(
        @RequestParam leagueId: Int,
        @RequestParam(required = false) season: Int?
    ): List<StandingDto> = leagues.getStandings(leagueId, seasons.resolve(season))

    /** 리그 경기 일정/결과 (날짜별 그룹) */
    @GetMapping("/league/{leagueId}/matches")
    fun getLeagueMatches(
        @PathVariable leagueId: Int,
        @RequestParam(required = false) season: Int?
    ): List<LeagueMatchDayDto> = leagues.getLeagueMatches(leagueId, seasons.resolve(season))

    /** 리그 플레이어 통계 (득점/도움/공격P/평점) */
    @GetMapping("/league/{leagueId}/player-stats")
    fun getLeaguePlayerStats(
        @PathVariable leagueId: Int,
        @RequestParam(required = false) season: Int?
    ): LeaguePlayerStatsDto = leagues.getLeaguePlayerStats(leagueId, seasons.resolve(season))

    /** 리그 팀 통계 (평균평점/득점/실점/클린시트) */
    @GetMapping("/league/{leagueId}/team-stats")
    fun getLeagueTeamStats(
        @PathVariable leagueId: Int,
        @RequestParam(required = false) season: Int?
    ): LeagueTeamStatsDto = leagues.getLeagueTeamStats(leagueId, seasons.resolve(season))
}
