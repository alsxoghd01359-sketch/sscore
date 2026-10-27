package com.example.livescore

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PlayerDetailPagerAdapter(
    activity: FragmentActivity,
    private val playerId: Long,
    private val tabs: List<String>
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment = when (tabs[position]) {
        "프로필" -> PlayerProfileFragment.newInstance(playerId)
        "경기"   -> PlayerMatchesFragment.newInstance(playerId)
        "통계"   -> PlayerStatsFragment.newInstance(playerId)
        "경력"   -> PlayerStubFragment.newInstance("경력")
        else    -> PlayerStubFragment.newInstance(tabs[position])
    }
}