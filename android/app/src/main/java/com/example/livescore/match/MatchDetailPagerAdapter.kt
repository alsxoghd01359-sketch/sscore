package com.example.livescore.match

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

// 탭 이름에 맞는 화면을 만든다. 데이터는 탭들이 MatchDetailViewModel에서 함께 가져간다.
class MatchDetailPagerAdapter(
    fa: FragmentActivity,
    private val fixtureId: Long,
    private val stadium: String?,
    private val round: String?,
    private val tabTitles: List<String>,
    private val homeTeamName: String,
    private val awayTeamName: String
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment = when (tabTitles[position]) {
        "미리보기" -> PreviewFragment.newInstance(stadium, round)
        "라인업" -> LineupFragment.newInstance(fixtureId)
        "순위" -> StandingsFragment.newInstance()
        "상대전적" -> H2HFragment.newInstance()
        "정보" -> InfoFragment.newInstance(stadium, round)
        "전체 통계" -> StatisticsFragment.newInstance(fixtureId, homeTeamName, awayTeamName)
        else -> Fragment()
    }
}
