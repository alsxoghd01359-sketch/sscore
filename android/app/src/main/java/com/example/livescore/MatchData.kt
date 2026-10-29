package com.example.livescore

import com.google.gson.annotations.SerializedName

data class MatchData(
    @SerializedName("fixtureId") val fixtureId: Long?,
    @SerializedName("homeTeam") val homeTeam: String?,
    @SerializedName("awayTeam") val awayTeam: String?,
    @SerializedName("homeTeamId") val homeTeamId: Int?,
    @SerializedName("awayTeamId") val awayTeamId: Int?,
    @SerializedName("matchDate") val matchDate: String?,
    @SerializedName("matchTime") val matchTime: String?,
    @SerializedName("score") val score: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("leagueId") val leagueId: Int?,
    @SerializedName("matchRound") val matchRound: String?,
    @SerializedName("stadium") val stadium: String?,
    @SerializedName("referee")      val referee: String?,
    @SerializedName("homeLogoUrl")   val homeLogoUrl: String? = null,
    @SerializedName("awayLogoUrl")   val awayLogoUrl: String? = null
)

data class LineupData(
    val teamName: String,
    val formation: String?,
    val coachName: String?,
    val coachPhotoUrl: String?,
    val startXI: List<PlayerData>,
    val substitutes: List<PlayerData>
)

data class PlayerData(
    val id: Long?,
    val name: String,
    val number: Int?,
    val pos: String?,
    val grid: String?
)

data class StandingData(
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
    val goalsDiff: Int = 0,
    val form: String? = null
)

data class H2HSummaryData(
    val homeWins: Int,
    val draws: Int,
    val awayWins: Int,
    val lastMatches: List<MatchData>
)

data class MatchInfoData(
    val fixtureId: Long,
    val momPlayer: MomPlayerData?,
    val referee: String?,
    val matchFullDate: String?,
    val events: List<MatchEventData> = emptyList(),
    val statistics: List<MatchStatItemData> = emptyList(),
    val homeTopPlayers: List<MomPlayerData> = emptyList(), // 🌟 홈팀 평점 TOP 5
    val awayTopPlayers: List<MomPlayerData> = emptyList()  // 🌟 원정팀 평점 TOP 5
)

data class MomPlayerData(
    val playerId: Long,
    val name: String,
    val teamName: String,
    val photoUrl: String?,
    val rating: Double
)

data class MatchEventData(
    val time: Int,
    val extraTime: Int?,
    val teamId: Int,
    val teamName: String?,
    val type: String,
    val detail: String?,
    val playerName: String?,
    val assistPlayerName: String?
)

data class MatchStatItemData(
    val category: String,
    val name: String,
    val homeValue: String,
    val awayValue: String
)

// ── 구단 경기 페이지 ──────────────────────────────────
data class TeamMatchesPageData(
    val matches:     List<MatchData>,
    val hasMore:     Boolean,
    val currentPage: Int,
    val totalPages:  Int
)

// ── 구단 개요 ──────────────────────────────────────
data class TeamOverviewData(
    val recentMatches:  List<RecentMatchData>,
    val miniStandings:  List<StandingData>,
    val venue:          VenueData?,
    val leagueTrophies: List<TrophyData>
)

data class RecentMatchData(
    val fixtureId:  Long?,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val homeTeam:   String?,
    val awayTeam:   String?,
    val score:      String?,
    val result:     String?,   // "W" | "D" | "L"
    val matchDate:  String?
)

data class VenueData(
    val name:     String?,
    val address:  String?,
    val city:     String?,
    val capacity: Int?,
    val surface:  String?,
    val imageUrl: String?
)

data class TrophyData(
    val league:        String?,
    val country:       String?,
    val winCount:      Int,
    val lastWinSeason: String?
)

// ── 구단 통계 ──────────────────────────────────────
data class TeamStatsData(
    val homeRecord:     RecordData?,
    val awayRecord:     RecordData?,
    val ratingRanking:  List<PlayerRankData>,
    val goalRanking:    List<PlayerRankData>,
    val assistRanking:  List<PlayerRankData>
)

data class RecordData(
    val played:       Int,
    val win:          Int,
    val draw:         Int,
    val lose:         Int,
    val goalsFor:     Int,
    val goalsAgainst: Int,
    val goalsDiff:    Int,
    val points:       Int
)

data class PlayerRankData(
    val rank:        Int,
    val playerId:    Long,
    val playerName:  String,
    val photoUrl:    String?,
    val value:       Double,
    val appearances: Int
)
// ── 선수 경기 스탯 ────────────────────────────────────
data class PlayerMatchStatData(
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
// ── 스쿼드 ────────────────────────────────────────────
data class SquadPlayerData(
    val playerId:    Long,
    val name:        String,
    val number:      Int?,
    val position:    String,
    val age:         Int?,
    val nationality: String?,
    val photoUrl:    String
)

data class SquadData(
    val goalkeepers: List<SquadPlayerData>,
    val defenders:   List<SquadPlayerData>,
    val midfielders: List<SquadPlayerData>,
    val attackers:   List<SquadPlayerData>
)
// ── 선수 프로필 ──────────────────────────────────────
data class CareerStatData(
    val season:       Int,
    val teamName:     String,
    val leagueName:   String,
    val appearances:  Int,
    val goals:        Int,
    val assists:      Int,
    val rating:       Double?,
    val minutesPlayed: Int
)

// ── 선수 세부 페이지 ──────────────────────────────────
data class PlayerTrophyData(
    val trophyName: String,
    val count:      Int,
    val seasons:    List<String>
)

data class PlayerDetailData(
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
    val currentSeasonStats: CareerStatData?,
    val trophies:        List<PlayerTrophyData>
)
// ── 리그 상세 ─────────────────────────────────────────
data class LeagueInfoData(
    val leagueId:   Int,
    val name:       String,
    val country:    String?,
    val logoUrl:    String?,
    val leagueType: String?
)

data class LeagueMatchDayData(
    val date:    String,
    val matches: List<MatchData>
)
// ── 리그 통계 ─────────────────────────────────────────
data class TeamRankData(
    val teamId:       Int?,
    val teamName:     String,
    val logoUrl:      String?,
    val value:        Double,
    val displayValue: String
)

data class LeaguePlayerStatsData(
    val goalRanking:        List<PlayerRankData>,
    val assistRanking:      List<PlayerRankData>,
    val attackPointRanking: List<PlayerRankData>,
    val ratingRanking:      List<PlayerRankData>
)

data class LeagueTeamStatsData(
    val avgRatingRanking:       List<TeamRankData>,
    val goalsPerGameRanking:    List<TeamRankData>,
    val concededPerGameRanking: List<TeamRankData>,
    val cleanSheetRanking:      List<TeamRankData>
)
// ── 선수 세부 경기/통계 ────────────────────────────────
data class PlayerMatchRecordData(
    @SerializedName("fixtureId")     val fixtureId:     Long,
    @SerializedName("matchDate")     val matchDate:     String,
    @SerializedName("opponentName")  val opponentName:  String,
    @SerializedName("opponentLogo")  val opponentLogo:  String?,
    @SerializedName("score")         val score:         String,
    @SerializedName("isHome")        val isHome:        Boolean,
    @SerializedName("status")        val status:        String,
    @SerializedName("minutesPlayed") val minutesPlayed: Int?,
    @SerializedName("goals")         val goals:         Int,
    @SerializedName("assists")       val assists:       Int,
    @SerializedName("yellowCards")   val yellowCards:   Int,
    @SerializedName("redCards")      val redCards:      Int,
    @SerializedName("rating")        val rating:        Double?,
    @SerializedName("homeTeam")      val homeTeam:      String,
    @SerializedName("awayTeam")      val awayTeam:      String,
    @SerializedName("homeTeamId")    val homeTeamId:    Int?,
    @SerializedName("awayTeamId")    val awayTeamId:    Int?,
    @SerializedName("matchTime")     val matchTime:     String,
    @SerializedName("matchRound")    val matchRound:    String?,
    @SerializedName("leagueId")      val leagueId:      Int,
    @SerializedName("season")        val season:        Int,
    @SerializedName("stadium")       val stadium:       String?
)

data class PlayerSeasonStatsDetailData(
    val season:       Int,
    val teamName:     String,
    val leagueName:   String,
    val position:     String?,
    val appearances:  Int,
    val goals:        Int,
    val assists:      Int,
    val avgRating:    Double?,
    val yellowCards:  Int,
    val redCards:     Int,
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
// ── 검색 결과 ──────────────────────────────────────────
data class SearchResultData(
    val type:     String,
    val id:       Long,
    val name:     String,
    val subName:  String?,
    val logoUrl:  String?,
    val leagueId: Int?,
    val sortOrder: Int = 99,
    val nameKo:   String? = null
)