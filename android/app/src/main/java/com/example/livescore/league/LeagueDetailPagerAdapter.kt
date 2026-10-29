package com.example.livescore.league

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LeagueDetailPagerAdapter(
    activity: FragmentActivity,
    private val leagueId: Int,
    private val tabs: List<String>
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment = when (tabs[position]) {
        "순위" -> LeagueStandingsFragment.newInstance(leagueId)
        "경기" -> LeagueMatchesFragment.newInstance(leagueId)
        "플레이어 통계" -> LeaguePlayerStatsFragment.newInstance(leagueId)
        "팀 통계" -> LeagueTeamStatsFragment.newInstance(leagueId)
        else -> error("알 수 없는 탭: ${tabs[position]}")
    }
}
