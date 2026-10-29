package com.example.livescore // 본인 패키지명 확인

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.POST
interface ApiService {
    // 1. 메인 화면: 날짜 + 리그 필터로 경기 목록 조회 (서버 사이드 필터링)
    @GET("/api/soccer/matches")
    fun getMatches(
        @Query("date")     date:     String? = null,
        @Query("leagueId") leagueId: Int?    = null
    ): Call<List<MatchData>>

    // 2. 상세 화면 (라인업): fixtureId로 조회
    @GET("/api/soccer/lineups/{fixtureId}")
    fun getLineups(@Path("fixtureId") fixtureId: Long): Call<List<LineupData>>

    // 3. 상세 화면 (순위표): 리그 ID와 시즌으로 조회
    @GET("/api/soccer/standings")
    fun getStandings(
        @Query("leagueId") leagueId: Int,
        @Query("season") season: Int
    ): Call<List<StandingData>>

    // 4. 상세 화면 (상대 전적): 두 팀의 ID로 조회 🌟 (이 부분이 없어서 에러가 났던 것!)
    @GET("/api/soccer/h2h")
    fun getH2H(
        @Query("homeId") homeId: Int,
        @Query("awayId") awayId: Int
    ): Call<H2HSummaryData>

    @GET("/api/soccer/matches/{fixtureId}/info")
    fun getMatchInfo(@Path("fixtureId") fixtureId: Long): Call<MatchInfoData>

    @GET("/api/soccer/search")
    fun search(@Query("q") q: String): Call<List<SearchResultData>>

    @GET("/api/soccer/player/{playerId}/matches")
    fun getPlayerMatches(@Path("playerId") playerId: Long): Call<List<PlayerMatchRecordData>>

    @GET("/api/soccer/player/{playerId}/season-stats")
    fun getPlayerSeasonStatsList(@Path("playerId") playerId: Long): Call<List<PlayerSeasonStatsDetailData>>

    @GET("/api/soccer/player/{playerId}/detail")
    fun getPlayerDetail(@Path("playerId") playerId: Long): Call<PlayerDetailData>

    @GET("/api/soccer/player/{playerId}/profile")
    fun getPlayerProfile(@Path("playerId") playerId: Long): Call<PlayerProfileData>

    @GET("/api/soccer/team/{teamId}/trophies")
    fun getTeamTrophies(@Path("teamId") teamId: Int): Call<List<TrophyData>>

    @GET("/api/soccer/team/{teamId}/squad")
    fun getTeamSquad(@Path("teamId") teamId: Int): Call<SquadData>

    @GET("/api/soccer/player/{playerId}/match-stats")
    fun getPlayerMatchStats(
        @Path("playerId")   playerId:  Long,
        @Query("fixtureId") fixtureId: Long
    ): Call<PlayerMatchStatData>

    @GET("/api/soccer/team/{teamId}/stats")
    fun getTeamStats(
        @Path("teamId")    teamId:   Int,
        @Query("leagueId") leagueId: Int,
        @Query("season")   season:   Int
    ): Call<TeamStatsData>

    @GET("/api/soccer/team/{teamId}/matches")
    fun getTeamMatches(
        @Path("teamId") teamId: Int,
        @Query("page")  page:   Int,
        @Query("size")  size:   Int = 15
    ): Call<TeamMatchesPageData>

    @GET("/api/soccer/team/{teamId}/overview")
    fun getTeamOverview(
        @Path("teamId")    teamId:   Int,
        @Query("leagueId") leagueId: Int,
        @Query("season")   season:   Int
    ): Call<TeamOverviewData>
}