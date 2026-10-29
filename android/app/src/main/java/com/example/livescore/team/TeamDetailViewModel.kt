package com.example.livescore.team

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.livescore.MatchData
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.SeasonLoader
import com.example.livescore.common.SimpleLoader
import com.example.livescore.league.LeagueRepository
import kotlinx.coroutines.launch

// 팀 경기 목록의 현재 상태 (첫 페이지 로딩/오류, 더 불러오는 중, 끝 여부)
data class TeamMatchesState(
    val items: List<MatchData> = emptyList(),
    val hasMore: Boolean = true,
    val loading: Boolean = true,
    val error: String? = null
)

// 팀 상세 화면 전체(헤더 + 5개 탭)가 공유하는 상태. 시즌 선택은 순위·통계·개요 탭이 함께 쓴다.
class TeamDetailViewModel(
    val teamId: Int,
    private val leagueHint: Int,
    private val seasonHint: Int?,
    private val teamRepository: TeamRepository = TeamRepository(RetrofitClient.teamApi),
    private val leagueRepository: LeagueRepository = LeagueRepository(RetrofitClient.leagueApi)
) : ViewModel() {

    private val _info = MutableLiveData<TeamInfoData?>(null)
    val info: LiveData<TeamInfoData?> = _info

    private val _leagueId = MutableLiveData<Int?>(null)
    val leagueId: LiveData<Int?> = _leagueId

    private val _seasons = MutableLiveData<List<Int>>(emptyList())
    val seasons: LiveData<List<Int>> = _seasons

    private val _selectedSeason = MutableLiveData<Int?>(null)
    val selectedSeason: LiveData<Int?> = _selectedSeason

    val overview = SeasonLoader(viewModelScope) { teamRepository.overview(teamId, _leagueId.value, it) }
    val stats = SeasonLoader(viewModelScope) { teamRepository.stats(teamId, _leagueId.value, it) }
    val standings = SeasonLoader(viewModelScope) { season ->
        val league = _leagueId.value
        if (league == null) ApiResult.Failure("LEAGUE_NOT_FOUND", "이 팀의 소속 리그를 찾을 수 없습니다.")
        else leagueRepository.standings(league, season)
    }
    val squad = SimpleLoader(viewModelScope) { teamRepository.squad(teamId) }

    private val seasonLoaders get() = listOf(overview, stats, standings)

    private val _matches = MutableLiveData(TeamMatchesState())
    val matches: LiveData<TeamMatchesState> = _matches
    private var nextMatchPage = 0

    init {
        bootstrap()
        loadMoreMatches()
    }

    fun selectSeason(season: Int) {
        if (_selectedSeason.value != season) _selectedSeason.value = season
    }

    // 시즌 목록을 아직 못 받았으면 그것부터 다시 받고, 받았으면 해당 탭만 다시 불러온다
    fun retry(loader: SeasonLoader<*>) {
        if (_selectedSeason.value == null) bootstrap() else loader.retry()
    }

    // 팀 정보 → 소속 리그 → 그 리그의 시즌 목록 순서로 준비한다
    private fun bootstrap() {
        seasonLoaders.forEach { it.setLoading() }
        viewModelScope.launch {
            val infoResult = teamRepository.info(teamId)
            (infoResult as? ApiResult.Success)?.let { _info.value = it.data }

            val league = leagueHint.takeIf { it > 0 } ?: (infoResult as? ApiResult.Success)?.data?.leagueId
            _leagueId.value = league
            if (league == null) {
                val message = (infoResult as? ApiResult.Failure)?.message ?: "이 팀의 소속 리그를 찾을 수 없습니다."
                seasonLoaders.forEach { it.setError(message) }
                return@launch
            }
            when (val r = leagueRepository.seasons(league)) {
                is ApiResult.Success -> {
                    _seasons.value = r.data.seasons
                    _selectedSeason.value = seasonHint?.takeIf { it in r.data.seasons } ?: r.data.default
                }
                is ApiResult.Failure -> seasonLoaders.forEach { it.setError(r.message) }
            }
        }
    }

    // 다음 페이지를 받는다. 실패하면 error를 남기고, 다시 호출하면 이어서 시도한다.
    fun loadMoreMatches() {
        val current = _matches.value ?: return
        if ((current.loading && current.items.isNotEmpty()) || !current.hasMore) return
        _matches.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = teamRepository.matches(teamId, nextMatchPage, PAGE_SIZE)) {
                is ApiResult.Success -> {
                    nextMatchPage = r.data.currentPage + 1
                    _matches.value = TeamMatchesState(current.items + r.data.matches, r.data.hasMore, loading = false)
                }
                is ApiResult.Failure -> _matches.value = current.copy(loading = false, error = r.message)
            }
        }
    }

    class Factory(private val intent: Intent) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TeamDetailViewModel(
            teamId = intent.getIntExtra(TeamDetailActivity.EXTRA_TEAM_ID, 0),
            leagueHint = intent.getIntExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, 0),
            seasonHint = if (intent.hasExtra("season")) intent.getIntExtra("season", 0).takeIf { it > 0 } else null
        ) as T
    }

    private companion object {
        const val PAGE_SIZE = 15
    }
}
