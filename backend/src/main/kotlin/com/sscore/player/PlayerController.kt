package com.sscore.player

import com.sscore.dto.PlayerDetailDto
import com.sscore.dto.PlayerMatchRecordDto
import com.sscore.dto.PlayerMatchStatDto
import com.sscore.dto.PlayerProfileDto
import com.sscore.dto.PlayerSeasonStatsDetailDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 선수 상세 화면용 조회. 없는 선수는 404이다.
@RestController
@RequestMapping("/api/soccer/player/{playerId}")
class PlayerController(private val players: PlayerService) {

    /** 선수 세부 페이지 (프로필 + 이번 시즌 + 트로피) */
    @GetMapping("/detail")
    fun detail(@PathVariable playerId: Long): PlayerDetailDto = players.getDetail(playerId)

    /** 선수 프로필 + 시즌별 커리어 */
    @GetMapping("/profile")
    fun profile(@PathVariable playerId: Long): PlayerProfileDto = players.getProfile(playerId)

    /** 선수 경기 기록 */
    @GetMapping("/matches")
    fun matches(@PathVariable playerId: Long): List<PlayerMatchRecordDto> = players.getMatches(playerId)

    /** 선수 시즌별 통계 목록 */
    @GetMapping("/season-stats")
    fun seasonStats(@PathVariable playerId: Long): List<PlayerSeasonStatsDetailDto> = players.getSeasonStats(playerId)

    /** 한 경기에서의 선수 기록 */
    @GetMapping("/match-stats")
    fun matchStats(@PathVariable playerId: Long, @RequestParam fixtureId: Long): PlayerMatchStatDto =
        players.getMatchStats(playerId, fixtureId)
}
