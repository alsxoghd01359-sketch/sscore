package com.example.livescore.league

import com.example.livescore.LeagueInfoData
import com.example.livescore.LeagueMatchDayData
import com.example.livescore.LeaguePlayerStatsData
import com.example.livescore.LeagueTeamStatsData
import com.example.livescore.StandingData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// 서버가 알려 주는 이 리그의 시즌 목록(최신순)과 기본 시즌
data class LeagueSeasonsData(val default: Int, val seasons: List<Int>)

interface LeagueApi {
    @GET("/api/soccer/league/{leagueId}")
    suspend fun info(@Path("leagueId") leagueId: Int): Response<LeagueInfoData>

    @GET("/api/soccer/league/{leagueId}/seasons")
    suspend fun seasons(@Path("leagueId") leagueId: Int): Response<LeagueSeasonsData>

    @GET("/api/soccer/standings")
    suspend fun standings(@Query("leagueId") leagueId: Int, @Query("season") season: Int): Response<List<StandingData>>

    @GET("/api/soccer/league/{leagueId}/matches")
    suspend fun matches(@Path("leagueId") leagueId: Int, @Query("season") season: Int): Response<List<LeagueMatchDayData>>

    @GET("/api/soccer/league/{leagueId}/player-stats")
    suspend fun playerStats(@Path("leagueId") leagueId: Int, @Query("season") season: Int): Response<LeaguePlayerStatsData>

    @GET("/api/soccer/league/{leagueId}/team-stats")
    suspend fun teamStats(@Path("leagueId") leagueId: Int, @Query("season") season: Int): Response<LeagueTeamStatsData>
}

class LeagueRepository(private val api: LeagueApi) {
    suspend fun info(leagueId: Int): ApiResult<LeagueInfoData> = apiCall { api.info(leagueId) }
    suspend fun seasons(leagueId: Int): ApiResult<LeagueSeasonsData> = apiCall { api.seasons(leagueId) }
    suspend fun standings(leagueId: Int, season: Int): ApiResult<List<StandingData>> = apiCall { api.standings(leagueId, season) }
    suspend fun matches(leagueId: Int, season: Int): ApiResult<List<LeagueMatchDayData>> = apiCall { api.matches(leagueId, season) }
    suspend fun playerStats(leagueId: Int, season: Int): ApiResult<LeaguePlayerStatsData> = apiCall { api.playerStats(leagueId, season) }
    suspend fun teamStats(leagueId: Int, season: Int): ApiResult<LeagueTeamStatsData> = apiCall { api.teamStats(leagueId, season) }
}
