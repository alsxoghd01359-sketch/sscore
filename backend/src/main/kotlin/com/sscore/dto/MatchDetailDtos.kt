package com.sscore.dto

import com.fasterxml.jackson.annotation.JsonProperty

// 1. 라인업 정보 (선발, 후보, 감독)
data class LineupDto(
    val teamName: String,
    val formation: String?,
    val coachName: String?,
    val coachPhotoUrl: String?, // 🌟 감독 프로필 사진 추가
    val startXI: List<PlayerDto>,
    val substitutes: List<PlayerDto>
)

data class PlayerDto(
    val id: Long?,
    val name: String,
    val number: Int?,
    val pos: String?,
    val grid: String?
)

// 2. 순위표 정보
data class StandingDto(
    val rank: Int,
    val teamName: String,
    val teamId: Int,
    val points: Int,
    val played: Int,
    val win: Int = 0,
    val draw: Int = 0,
    val lose: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val goalsDiff: Int,
    val form: String? = null
)

// 3. 상대 전적 요약
data class H2HSummaryDto(
    val homeWins: Int,
    val draws: Int,
    val awayWins: Int,
    val lastMatches: List<MatchDto>
)

// 4. 실시간 이벤트 DTO

// 5. 구단 경기 목록 페이지 DTO
data class TeamMatchesPageDto(
    val matches:     List<MatchDto>,
    val hasMore:     Boolean,
    val currentPage: Int,
    val totalPages:  Int
)

// 6. 구단 개요 DTO
data class TeamOverviewDto(
    val recentMatches: List<RecentMatchDto>,
    val miniStandings:  List<StandingDto>,
    val venue:          VenueDto?,
    val leagueTrophies: List<TrophyDto>
)

data class RecentMatchDto(
    val fixtureId:  Long?,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val homeTeam:   String,
    val awayTeam:   String,
    val score:      String,
    val result:     String,   // "W" | "D" | "L"  (해당 팀 기준)
    val matchDate:  String?
)

data class VenueDto(
    val name:     String?,
    val address:  String?,
    val city:     String?,
    val capacity: Int?,
    val surface:  String?,
    val imageUrl: String?
)

data class TrophyDto(
    val league:        String,
    val country:       String?,
    val winCount:      Int,
    val lastWinSeason: String
)

// 6. 선수 세부 스텟 DTO
data class PlayerStatDto(
    val teamId: Int,
    val playerId: Long,
    val playerName: String,
    val rating: Double?,
    val position: String?,
    val minutesPlayed: Int?,
    val goals: Int,
    val assists: Int,
    val passesTotal: Int,
    val passesAccuracy: Int,
    val passesKey: Int,
    val shotsTotal: Int,
    val shotsOnTarget: Int,
    val shotsOffTarget: Int,
    val ballTouches: Int,
    val turnovers: Int,
    val tacklesTotal: Int,
    val tacklesBlocks: Int,
    val tacklesInterceptions: Int,
    val duelsTotal: Int,
    val duelsWon: Int,
    val foulsDrawn: Int,
    val foulsCommitted: Int,
    val goalkeeperSaves: Int,
    val goalkeeperConceded: Int,
    val goalkeeperSavesInsideBox: Int,
    val goalkeeperPunches: Int
)

// 7. 구단 통계 DTO
data class TeamStatsDto(
    val homeRecord:     RecordDto?,
    val awayRecord:     RecordDto?,
    val ratingRanking:  List<PlayerRankDto>,
    val goalRanking:    List<PlayerRankDto>,
    val assistRanking:  List<PlayerRankDto>
)

data class RecordDto(
    val played:       Int,
    val win:          Int,
    val draw:         Int,
    val lose:         Int,
    val goalsFor:     Int,
    val goalsAgainst: Int,
    val goalsDiff:    Int,
    val points:       Int
)

data class PlayerRankDto(
    val rank:        Int,
    val playerId:    Long,
    val playerName:  String,
    val photoUrl:    String?,
    val value:       Double,
    val appearances: Int
)
// 8. 선수 경기 스탯 DTO
data class PlayerMatchStatDto(
    val playerId:             Long,
    val playerName:           String,
    val photoUrl:             String,
    val teamName:             String,
    val position:             String?,
    val nationality:          String?,
    val age:                  Int?,
    val minutesPlayed:        Int?,
    val rating:               Double?,
    val goals:                Int?,
    val assists:              Int?,
    val passesTotal:          Int?,
    val passesAccuracy:       Int?,
    val keyPasses:            Int?,
    val shotsTotal:           Int?,
    val shotsOn:              Int?,
    val tacklesTotal:         Int?,
    val tacklesBlocks:        Int?,
    val tacklesInterceptions: Int?,
    val duelsTotal:           Int?,
    val duelsWon:             Int?,
    val dribblesAttempts:     Int?,
    val dribblesSuccess:      Int?,
    val foulsDrawn:           Int?,
    val foulsCommitted:       Int?,
    // GK 전용
    val saves:                Int? = null,
    val goalsConceded:        Int? = null,
    val opponentXg:           Double? = null
)
// 9. 스쿼드 DTO
data class SquadPlayerDto(
    val playerId:    Long,
    val name:        String,
    val number:      Int?,
    val position:    String,
    val age:         Int?,
    val nationality: String?,
    val photoUrl:    String
)

data class SquadDto(
    val goalkeepers: List<SquadPlayerDto>,
    val defenders:   List<SquadPlayerDto>,
    val midfielders: List<SquadPlayerDto>,
    val attackers:   List<SquadPlayerDto>
)
// 10. 선수 프로필 DTO
data class CareerStatDto(
    val season:       Int,
    val teamName:     String,
    val leagueName:   String,
    val appearances:  Int,
    val goals:        Int,
    val assists:      Int,
    val rating:       Double?,
    val minutesPlayed: Int
)

data class PlayerProfileDto(
    val playerId:        Long,
    val name:            String,
    val nationality:     String?,
    val position:        String?,
    val age:             Int?,
    val photoUrl:        String,
    val currentTeamName: String?,
    val careerStats:     List<CareerStatDto>
)
// 11. 선수 세부 페이지 DTO
data class PlayerTrophyDto(
    val trophyName: String,
    val count:      Int,
    val seasons:    List<String>
)

data class PlayerDetailDto(
    val playerId:        Long,
    val name:            String,
    val photoUrl:        String,
    val currentTeamName: String?,
    val height:          String?,
    val birthDate:       String?,
    val nationality:     String?,
    val age:             Int?,
    val position:        String?,
    val squadNumber:     Int?,
    val preferredFoot:   String?,
    val currentSeasonStats: CareerStatDto?,
    val trophies:        List<PlayerTrophyDto>
)
// 12. 리그 상세 DTO
data class LeagueInfoDto(
    val leagueId:   Int,
    val name:       String,
    val country:    String?,
    val logoUrl:    String?,
    val leagueType: String?
)

data class LeagueMatchDayDto(
    val date:    String,
    val matches: List<MatchDto>
)
// 13. 리그 통계 DTO
data class TeamRankDto(
    val teamId:       Int?,
    val teamName:     String,
    val logoUrl:      String?,
    val value:        Double,
    val displayValue: String
)

data class LeaguePlayerStatsDto(
    val goalRanking:        List<PlayerRankDto>,
    val assistRanking:      List<PlayerRankDto>,
    val attackPointRanking: List<PlayerRankDto>,
    val ratingRanking:      List<PlayerRankDto>
)

data class LeagueTeamStatsDto(
    val avgRatingRanking:      List<TeamRankDto>,
    val goalsPerGameRanking:   List<TeamRankDto>,
    val concededPerGameRanking: List<TeamRankDto>,
    val cleanSheetRanking:     List<TeamRankDto>
)
// 14. 선수 세부 - 경기 기록 DTO
data class PlayerMatchRecordDto(
    val fixtureId:     Long,
    val matchDate:     String,
    val opponentName:  String,
    val opponentLogo:  String?,
    val score:         String,
    val isHome:        Boolean,
    val status:        String,
    val minutesPlayed: Int?,
    val goals:         Int,
    val assists:       Int,
    val yellowCards:   Int,
    val redCards:      Int,
    val rating:        Double?,
    // MatchDetailActivity 네비게이션용
    val homeTeam:   String,
    val awayTeam:   String,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val matchTime:  String,
    val matchRound: String?,
    val leagueId:   Int,
    val season:     Int,
    val stadium:    String?
)

// 15. 선수 세부 - 시즌별 통계 DTO
data class PlayerSeasonStatsDetailDto(
    val season:       Int,
    val teamName:     String,
    val leagueName:   String,
    val position:     String?,
    // 시즌 요약 (player_season_stats)
    val appearances:  Int,
    val goals:        Int,
    val assists:      Int,
    val avgRating:    Double?,
    val yellowCards:  Int,
    val redCards:     Int,
    // 경기별 집계 (player_stats - 조회된 경기만)
    val passesTotal:          Int,
    val passesAccuracyAvg:    Double,
    val passesKey:            Int,
    val shotsTotal:           Int,
    val shotsOnTarget:        Int,
    val xgTotal:              Double,
    val tacklesTotal:         Int,
    val tacklesBlocks:        Int,
    val tacklesInterceptions: Int,
    val duelsTotal:           Int,
    val duelsWon:             Int,
    val foulsDrawn:           Int,
    val foulsCommitted:       Int,
    val goalkeeperSaves:      Int,
    val goalkeeperConceded:   Int,
    val goalkeeperSavesInsideBox: Int,
    val matchesWithData:      Int
)
// 16. 검색 결과 DTO
data class SearchResultDto(
    val type:     String,
    val id:       Long,
    val name:     String,
    val subName:  String?,
    val logoUrl:  String?,
    val leagueId: Int?,
    val sortOrder: Int = 99
)