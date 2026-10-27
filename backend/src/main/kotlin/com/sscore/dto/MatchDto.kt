package com.sscore.dto

data class MatchDto(
    val fixtureId:    Long?,
    val homeTeam:     String,
    val awayTeam:     String,
    val homeTeamId:   Int?,
    val awayTeamId:   Int?,
    val matchDate:    String,
    val matchTime:    String,
    val score:        String,
    val season:       Int,
    val leagueId:     Int,
    val matchRound:   String?,
    val stadium:      String?,
    val referee:      String?  = null,
    val homeLogoUrl:  String?  = null,   // 리그 상세 경기탭용
    val awayLogoUrl:  String?  = null
)