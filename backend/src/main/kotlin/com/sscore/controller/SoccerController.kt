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

    /** 선수 경기 스탯 */
    @GetMapping("/player/{playerId}/match-stats")
    fun getPlayerMatchStats(
        @PathVariable playerId: Long,
        @RequestParam fixtureId: Long
    ): PlayerMatchStatDto? = soccerService.getPlayerMatchStats(playerId, fixtureId)
}
