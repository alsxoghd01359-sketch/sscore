package com.sscore.dto

data class MatchInfoResponseDto(
    val fixtureId: Long,
    val momPlayer: MomPlayerDto?,
    val referee: String?,
    val matchFullDate: String?,
    val events: List<MatchEventDto> = emptyList(),
    val statistics: List<MatchStatItemDto> = emptyList(),
    val homeTopPlayers: List<MomPlayerDto> = emptyList(), // 🌟 홈팀 TOP 5
    val awayTopPlayers: List<MomPlayerDto> = emptyList()  // 🌟 원정팀 TOP 5
)

data class MomPlayerDto(
    val playerId: Long,
    val name: String,
    val teamName: String,
    val photoUrl: String?,
    val rating: Double
)

data class MatchEventDto(
    val time: Int,
    val extraTime: Int?,
    val teamId: Int,
    val teamName: String?,
    val type: String,
    val detail: String?,
    val playerName: String?,
    val assistPlayerName: String?
)

data class MatchStatItemDto(
    val category: String,
    val name: String,
    val homeValue: String,
    val awayValue: String
)