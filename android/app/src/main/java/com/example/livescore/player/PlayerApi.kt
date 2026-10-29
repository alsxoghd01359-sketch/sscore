package com.example.livescore.player

import com.example.livescore.PlayerDetailData
import com.example.livescore.PlayerMatchRecordData
import com.example.livescore.PlayerMatchStatData
import com.example.livescore.PlayerSeasonStatsDetailData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PlayerApi {
    @GET("/api/soccer/player/{playerId}/detail")
    suspend fun detail(@Path("playerId") playerId: Long): Response<PlayerDetailData>

    @GET("/api/soccer/player/{playerId}/matches")
    suspend fun matches(@Path("playerId") playerId: Long): Response<List<PlayerMatchRecordData>>

    @GET("/api/soccer/player/{playerId}/season-stats")
    suspend fun seasonStats(@Path("playerId") playerId: Long): Response<List<PlayerSeasonStatsDetailData>>

    @GET("/api/soccer/player/{playerId}/match-stats")
    suspend fun matchStats(
        @Path("playerId") playerId: Long,
        @Query("fixtureId") fixtureId: Long
    ): Response<PlayerMatchStatData>
}

class PlayerRepository(private val api: PlayerApi) {
    suspend fun detail(playerId: Long): ApiResult<PlayerDetailData> = apiCall { api.detail(playerId) }
    suspend fun matches(playerId: Long): ApiResult<List<PlayerMatchRecordData>> = apiCall { api.matches(playerId) }
    suspend fun seasonStats(playerId: Long): ApiResult<List<PlayerSeasonStatsDetailData>> = apiCall { api.seasonStats(playerId) }
    suspend fun matchStats(playerId: Long, fixtureId: Long): ApiResult<PlayerMatchStatData> =
        apiCall { api.matchStats(playerId, fixtureId) }
}
