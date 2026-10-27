package com.example.livescore

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MatchDetailPagerAdapter(
    fa: FragmentActivity,
    private val fixtureId: Long,
    private val leagueId: Int,
    private val season: Int,
    private val homeTeamId: Int,
    private val awayTeamId: Int,
    private val stadium: String?,
    private val round: String?,
    private val tabTitles: List<String>,
    private val homeTeamName: String, // 🌟 색상을 입히기 위해 팀 이름 추가
    private val awayTeamName: String
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        val currentTabName = tabTitles[position]

        return when (currentTabName) {
            "미리보기" -> PreviewFragment.newInstance(stadium, round)
            "라인업" -> LineupFragment.newInstance(fixtureId)
            "순위" -> StandingsFragment.newInstance(leagueId, season, homeTeamId, awayTeamId)
            "상대전적" -> H2HFragment.newInstance(homeTeamId, awayTeamId)
            "정보" -> InfoFragment.newInstance(fixtureId, homeTeamId, awayTeamId, stadium, round)

            // 🌟 드디어 비어있던 통계 탭을 진짜 화면(StatisticsFragment)으로 연결합니다!
            "전체 통계" -> StatisticsFragment.newInstance(fixtureId, homeTeamName, awayTeamName)

            else -> Fragment()
        }
    }
}