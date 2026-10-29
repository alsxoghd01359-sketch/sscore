package com.example.livescore.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.common.SimpleLoader
import com.example.livescore.league.LeagueRepository

// 경기 상세 화면의 탭들이 함께 쓰는 데이터. 탭을 처음 열 때 한 번 받고, 진행 중인 경기는 refreshLive()로 조용히 갱신한다.
class MatchDetailViewModel(
    val fixtureId: Long,
    val homeTeamId: Int,
    val awayTeamId: Int,
    val leagueId: Int,
    val season: Int,
    private val repository: MatchRepository = MatchRepository(RetrofitClient.matchApi),
    private val leagues: LeagueRepository = LeagueRepository(RetrofitClient.leagueApi)
) : ViewModel() {

    val info = SimpleLoader(viewModelScope) { repository.info(fixtureId) }
    val lineups = SimpleLoader(viewModelScope) { repository.lineups(fixtureId) }
    val h2h = SimpleLoader(viewModelScope) { repository.h2h(homeTeamId, awayTeamId) }
    val standings = SimpleLoader(viewModelScope) { leagues.standings(leagueId, season) }

    // 이미 받아 둔 정보/라인업만 다시 받는다 (열어 보지 않은 탭은 건드리지 않는다)
    fun refreshLive() {
        info.refreshQuietly()
        lineups.refreshQuietly()
    }

    class Factory(
        private val fixtureId: Long, private val homeTeamId: Int, private val awayTeamId: Int,
        private val leagueId: Int, private val season: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchDetailViewModel(fixtureId, homeTeamId, awayTeamId, leagueId, season) as T
    }
}

// 경기 상세 화면(Activity)이 만든 ViewModel을 탭 화면에서 가져온다
fun androidx.fragment.app.Fragment.matchDetailViewModel(): MatchDetailViewModel =
    ViewModelProvider(requireActivity())[MatchDetailViewModel::class.java]
