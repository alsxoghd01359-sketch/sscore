package com.sscore.dto

import com.sscore.entity.MatchEntity

// 저장된 경기를 화면용 MatchDto로 바꾼다 (경기·팀·리그 화면이 함께 쓴다)
fun MatchEntity.toMatchDto() = MatchDto(
    fixtureId = fixtureId,
    homeTeam = homeTeam,
    awayTeam = awayTeam,
    homeTeamId = homeTeamId,
    awayTeamId = awayTeamId,
    matchDate = matchDate,
    matchTime = matchTime,
    score = score,
    season = season,
    leagueId = leagueId,
    matchRound = matchRound,
    stadium = stadium,
    referee = referee
)
