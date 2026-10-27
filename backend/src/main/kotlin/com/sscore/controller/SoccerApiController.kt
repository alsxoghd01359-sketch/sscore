package com.sscore.controller

import com.sscore.entity.PlayerSeasonStatEntity
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

    @GetMapping("/player/{playerId}/career")
    fun getPlayerCareer(@PathVariable playerId: Long): List<PlayerSeasonStatEntity> {
        return soccerService.getPlayerCareerHistory(playerId)
    }

    @GetMapping("/standings")
    fun getStandings(@RequestParam leagueId: Int, @RequestParam season: Int) =
        soccerService.getStandings(leagueId, season)

    @GetMapping("/h2h")
    fun getH2H(@RequestParam homeId: Int, @RequestParam awayId: Int) =
        soccerService.getH2H(homeId, awayId)

    @GetMapping("/matches/{fixtureId}/info")
    fun getMatchInfo(@PathVariable fixtureId: Long): MatchInfoResponseDto {
        return soccerService.getMatchInfo(fixtureId)
    }

    /** 통합 검색 (선수·팀·리그) */
    @GetMapping("/search")
    fun search(@RequestParam q: String): List<SearchResultDto> =
        soccerService.search(q)

    /** 리그 기본 정보 */
    @GetMapping("/league/{leagueId}")
    fun getLeagueInfo(@PathVariable leagueId: Int): LeagueInfoDto? =
        soccerService.getLeagueInfo(leagueId)

    /** 리그 플레이어 통계 (득점/도움/공격P/평점) */
    @GetMapping("/league/{leagueId}/player-stats")
    fun getLeaguePlayerStats(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): LeaguePlayerStatsDto = soccerService.getLeaguePlayerStats(leagueId, season)

    /** 리그 팀 통계 (평균평점/득점/실점/클린시트) */
    @GetMapping("/league/{leagueId}/team-stats")
    fun getLeagueTeamStats(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): LeagueTeamStatsDto = soccerService.getLeagueTeamStats(leagueId, season)

    /** 리그 경기 일정/결과 (날짜별 그룹) */
    @GetMapping("/league/{leagueId}/matches")
    fun getLeagueMatches(
        @PathVariable leagueId: Int,
        @RequestParam(defaultValue = "2025") season: Int
    ): List<LeagueMatchDayDto> = soccerService.getLeagueMatches(leagueId, season)

    /** 선수 경기 기록 */
    @GetMapping("/player/{playerId}/matches")
    fun getPlayerMatches(@PathVariable playerId: Long): List<PlayerMatchRecordDto> =
        soccerService.getPlayerMatches(playerId)

    /** 선수 시즌별 통계 목록 */
    @GetMapping("/player/{playerId}/season-stats")
    fun getPlayerSeasonStatsList(@PathVariable playerId: Long): List<PlayerSeasonStatsDetailDto> =
        soccerService.getPlayerSeasonStatsList(playerId)

    /** 선수 세부 페이지 (프로필+트로피+이번시즌) */
    @GetMapping("/player/{playerId}/detail")
    fun getPlayerDetail(@PathVariable playerId: Long): PlayerDetailDto? =
        soccerService.getPlayerDetail(playerId)

    /** 선수 프로필 + 커리어 스탯 */
    @GetMapping("/player/{playerId}/profile")
    fun getPlayerProfile(@PathVariable playerId: Long): PlayerProfileDto? =
        soccerService.getPlayerProfile(playerId)

    /** 구단 트로피 전체 목록 (DB champions 테이블) */
    @GetMapping("/team/{teamId}/trophies")
    fun getTeamTrophies(@PathVariable teamId: Int): List<TrophyDto> =
        soccerService.getTeamTrophies(teamId)

    /** 구단 현재 스쿼드 */
    @GetMapping("/team/{teamId}/squad")
    fun getTeamSquad(@PathVariable teamId: Int): SquadDto =
        soccerService.getTeamSquad(teamId)

    /** 선수 경기 스탯 */
    @GetMapping("/player/{playerId}/match-stats")
    fun getPlayerMatchStats(
        @PathVariable playerId: Long,
        @RequestParam fixtureId: Long
    ): PlayerMatchStatDto? = soccerService.getPlayerMatchStats(playerId, fixtureId)

    /** 구단 통계: 홈/원정 성적 + 평점/득점/도움 랭킹 */
    @GetMapping("/team/{teamId}/stats")
    fun getTeamStats(
        @PathVariable teamId:  Int,
        @RequestParam leagueId: Int,
        @RequestParam season:   Int
    ): TeamStatsDto = soccerService.getTeamStats(teamId, leagueId, season)

    /** 구단 경기 목록 (페이지네이션, 최신순) */
    @GetMapping("/team/{teamId}/matches")
    fun getTeamMatches(
        @PathVariable teamId: Int,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "15") size: Int
    ): TeamMatchesPageDto = soccerService.getTeamMatches(teamId, page, size)

    /** 구단 개요: 최근 5경기 + 미니순위 + 경기장 + 리그트로피 */
    @GetMapping("/team/{teamId}/overview")
    fun getTeamOverview(
        @PathVariable teamId: Int,
        @RequestParam leagueId: Int,
        @RequestParam season: Int
    ): TeamOverviewDto = soccerService.getTeamOverview(teamId, leagueId, season)
}
