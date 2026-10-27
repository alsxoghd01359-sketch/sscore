package com.example.livescore

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TeamDetailPagerAdapter(
    fa: FragmentActivity,
    private val teamId: Int,
    private val teamName: String,
    private val leagueId: Int,
    private val leagueName: String,
    private val season: Int,
    private val tabTitles: List<String>
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        return when (tabTitles[position]) {
            "개요"   -> TeamOverviewFragment.newInstance(teamId, teamName, leagueId, season)
            "경기"   -> TeamMatchesFragment.newInstance(teamId, leagueId, season)
            "순위"   -> TeamStandingsFragment.newInstance(leagueId, season, teamId)
            "통계"   -> TeamStatsFragment.newInstance(teamId, leagueId, season)
            "스쿼드" -> TeamSquadFragment.newInstance(teamId)
            else    -> Fragment()
        }
    }
}