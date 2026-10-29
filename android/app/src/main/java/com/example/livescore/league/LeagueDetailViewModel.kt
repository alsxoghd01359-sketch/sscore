package com.example.livescore.league

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.livescore.LeagueInfoData
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 한 시즌 단위로 불러오는 탭 데이터(순위·경기·선수 통계·팀 통계). 같은 시즌은 한 번 받으면 다시 받지 않는다.
class SeasonLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (season: Int) -> ApiResult<T>
) {
    private val _state = MutableLiveData<UiState<T>>(UiState.Loading)
    val state: LiveData<UiState<T>> = _state

    private val cache = mutableMapOf<Int, T>()
    private var requested: Int? = null
    private var job: Job? = null

    fun load(season: Int, force: Boolean = false) {
        requested = season
        val cached = cache[season]
        if (!force && cached != null) {
            _state.value = UiState.Success(cached)
            return
        }
        _state.value = UiState.Loading
        job?.cancel()
        job = scope.launch {
            val result = fetch(season)
            if (requested != season) return@launch   // 그 사이 다른 시즌을 골랐다면 버린다
            when (result) {
                is ApiResult.Success -> {
                    cache[season] = result.data
                    _state.value = UiState.Success(result.data)
                }
                is ApiResult.Failure -> _state.value = UiState.Error(result.message)
            }
        }
    }

    fun retry() {
        requested?.let { load(it, force = true) }
    }

    fun setLoading() { _state.value = UiState.Loading }
    fun setError(message: String) { _state.value = UiState.Error(message) }
}

// 리그 상세 화면 전체(헤더 + 4개 탭)가 공유하는 상태. 시즌 선택도 탭 사이에서 같이 바뀐다.
class LeagueDetailViewModel(
    val leagueId: Int,
    private val repository: LeagueRepository = LeagueRepository(RetrofitClient.leagueApi)
) : ViewModel() {

    private val _info = MutableLiveData<LeagueInfoData?>(null)
    val info: LiveData<LeagueInfoData?> = _info

    private val _seasons = MutableLiveData<List<Int>>(emptyList())
    val seasons: LiveData<List<Int>> = _seasons

    private val _selectedSeason = MutableLiveData<Int?>(null)
    val selectedSeason: LiveData<Int?> = _selectedSeason

    val standings = SeasonLoader(viewModelScope) { repository.standings(leagueId, it) }
    val matches = SeasonLoader(viewModelScope) { repository.matches(leagueId, it) }
    val playerStats = SeasonLoader(viewModelScope) { repository.playerStats(leagueId, it) }
    val teamStats = SeasonLoader(viewModelScope) { repository.teamStats(leagueId, it) }

    private val loaders get() = listOf(standings, matches, playerStats, teamStats)

    init {
        loadInfo()
        loadSeasons()
    }

    fun selectSeason(season: Int) {
        if (_selectedSeason.value != season) _selectedSeason.value = season
    }

    // 시즌 목록을 아직 못 받았으면 그것부터 다시 받고, 받았으면 해당 탭만 다시 불러온다
    fun retry(loader: SeasonLoader<*>) {
        if (_selectedSeason.value == null) loadSeasons() else loader.retry()
    }

    private fun loadInfo() {
        viewModelScope.launch {
            // 헤더 정보는 없어도 화면을 쓸 수 있으므로 실패해도 조용히 둔다(탭 데이터가 오류를 안내한다)
            (repository.info(leagueId) as? ApiResult.Success)?.let { _info.value = it.data }
        }
    }

    private fun loadSeasons() {
        loaders.forEach { it.setLoading() }
        viewModelScope.launch {
            when (val r = repository.seasons(leagueId)) {
                is ApiResult.Success -> {
                    _seasons.value = r.data.seasons
                    _selectedSeason.value = r.data.default
                }
                is ApiResult.Failure -> loaders.forEach { it.setError(r.message) }
            }
        }
    }

    class Factory(private val leagueId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LeagueDetailViewModel(leagueId) as T
    }
}
