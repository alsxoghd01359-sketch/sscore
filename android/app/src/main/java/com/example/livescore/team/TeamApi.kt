package com.example.livescore.team

import com.example.livescore.SquadData
import com.example.livescore.TeamMatchesPageData
import com.example.livescore.TeamOverviewData
import com.example.livescore.TeamStatsData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TeamInfoData(
    val teamId: Int,
    val name: String,
    val country: String?,
    val logoUrl: String?,
    val leagueId: Int?,
    val leagueName: String?
)

// leagueId/season을 보내지 않으면(null) 서버가 팀의 리그와 데이터가 있는 최근 시즌을 쓴다
interface TeamApi {
    @GET("/api/soccer/team/{teamId}")
    suspend fun info(@Path("teamId") teamId: Int): Response<TeamInfoData>

    @GET("/api/soccer/team/{teamId}/overview")
    suspend fun overview(
        @Path("teamId") teamId: Int,
        @Query("leagueId") leagueId: Int?,
        @Query("season") season: Int?
    ): Response<TeamOverviewData>

    @GET("/api/soccer/team/{teamId}/stats")
    suspend fun stats(
        @Path("teamId") teamId: Int,
        @Query("leagueId") leagueId: Int?,
        @Query("season") season: Int?
    ): Response<TeamStatsData>

    @GET("/api/soccer/team/{teamId}/squad")
    suspend fun squad(@Path("teamId") teamId: Int): Response<SquadData>

    @GET("/api/soccer/team/{teamId}/matches")
    suspend fun matches(
        @Path("teamId") teamId: Int,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<TeamMatchesPageData>
}

class TeamRepository(private val api: TeamApi) {
    suspend fun info(teamId: Int): ApiResult<TeamInfoData> = apiCall { api.info(teamId) }
    suspend fun overview(teamId: Int, leagueId: Int?, season: Int?): ApiResult<TeamOverviewData> =
        apiCall { api.overview(teamId, leagueId, season) }
    suspend fun stats(teamId: Int, leagueId: Int?, season: Int?): ApiResult<TeamStatsData> =
        apiCall { api.stats(teamId, leagueId, season) }
    suspend fun squad(teamId: Int): ApiResult<SquadData> = apiCall { api.squad(teamId) }
    suspend fun matches(teamId: Int, page: Int, size: Int): ApiResult<TeamMatchesPageData> =
        apiCall { api.matches(teamId, page, size) }
}
