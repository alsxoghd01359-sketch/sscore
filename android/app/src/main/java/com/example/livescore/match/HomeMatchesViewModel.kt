package com.example.livescore.match

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.H2HSummaryData
import com.example.livescore.LineupData
import com.example.livescore.MatchData
import com.example.livescore.MatchInfoData
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.UiState
import com.example.livescore.common.apiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

interface MatchApi {
    @GET("/api/soccer/matches")
    suspend fun matches(@Query("date") date: String, @Query("leagueId") leagueId: Int?): Response<List<MatchData>>

    @GET("/api/soccer/matches/{fixtureId}")
    suspend fun match(@Path("fixtureId") fixtureId: Long): Response<MatchData>

    @GET("/api/soccer/matches/{fixtureId}/info")
    suspend fun info(@Path("fixtureId") fixtureId: Long): Response<MatchInfoData>

    @GET("/api/soccer/lineups/{fixtureId}")
    suspend fun lineups(@Path("fixtureId") fixtureId: Long): Response<List<LineupData>>

    @GET("/api/soccer/h2h")
    suspend fun h2h(@Query("homeId") homeId: Int, @Query("awayId") awayId: Int): Response<H2HSummaryData>
}

class MatchRepository(private val api: MatchApi) {
    suspend fun matches(date: LocalDate, leagueId: Int?): ApiResult<List<MatchData>> =
        apiCall { api.matches(date.toString(), leagueId) }

    suspend fun info(fixtureId: Long): ApiResult<MatchInfoData> = apiCall { api.info(fixtureId) }
    suspend fun lineups(fixtureId: Long): ApiResult<List<LineupData>> = apiCall { api.lineups(fixtureId) }
    suspend fun h2h(homeId: Int, awayId: Int): ApiResult<H2HSummaryData> = apiCall { api.h2h(homeId, awayId) }
}

// 홈 화면의 경기 목록. 날짜/리그를 고르면 새로 받고, 화면이 보이는 동안에는 조용히 다시 받아 점수를 갱신한다.
class HomeMatchesViewModel(
    private val repository: MatchRepository = MatchRepository(RetrofitClient.matchApi)
) : ViewModel() {

    private val _state = MutableLiveData<UiState<List<MatchData>>>(UiState.Loading)
    val state: LiveData<UiState<List<MatchData>>> = _state

    private var date: LocalDate = LocalDate.now()
    private var leagueId: Int? = null
    private var job: Job? = null

    // 날짜나 리그를 바꿨을 때: 이전 요청은 버리고 로딩 표시와 함께 새로 받는다
    fun show(date: LocalDate, leagueId: Int?) {
        this.date = date
        this.leagueId = leagueId
        job?.cancel()
        _state.value = UiState.Loading
        job = viewModelScope.launch { publish(repository.matches(date, leagueId), quiet = false) }
    }

    fun retry() = show(date, leagueId)

    // 라이브 갱신: 로딩 표시 없이 받고, 실패하면 지금 보이는 목록을 그대로 둔다
    fun refreshQuietly() {
        if (!isLivePossible() || _state.value !is UiState.Success) return
        val requestedDate = date
        val requestedLeague = leagueId
        job?.cancel()
        job = viewModelScope.launch {
            val result = repository.matches(requestedDate, requestedLeague)
            // 그 사이 날짜/리그를 바꿨다면 버린다
            if (requestedDate == date && requestedLeague == leagueId) publish(result, quiet = true)
        }
    }

    // 진행 중인 경기가 있을 수 있는 날짜(어제·오늘)만 자동 갱신한다
    private fun isLivePossible(): Boolean {
        val today = LocalDate.now()
        return date == today || date == today.minusDays(1)
    }

    private fun publish(result: ApiResult<List<MatchData>>, quiet: Boolean) {
        when (result) {
            is ApiResult.Success -> {
                // 바뀐 것이 없으면 다시 그리지 않는다
                val current = (_state.value as? UiState.Success)?.data
                if (current != result.data) _state.value = UiState.Success(result.data)
            }
            is ApiResult.Failure -> if (!quiet) _state.value = UiState.Error(result.message)
        }
    }
}
