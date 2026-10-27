package com.example.livescore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.livescore.databinding.ActivityTeamDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

class TeamDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEAM_ID   = "teamId"
        const val EXTRA_TEAM_NAME = "teamName"
        const val EXTRA_LEAGUE_ID = "leagueId"
    }

    private lateinit var binding: ActivityTeamDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val teamId    = intent.getIntExtra("teamId", 0)
        val teamName  = intent.getStringExtra("teamName") ?: "구단"
        val leagueId  = intent.getIntExtra("leagueId", 0)
        val leagueName = intent.getStringExtra("leagueName") ?: ""
        val season    = intent.getIntExtra("season", 2025)

        // 헤더 세팅
        binding.tvTeamName.text = teamName
        binding.tvLeagueName.text = leagueName
        Glide.with(this)
            .load("https://media.api-sports.io/football/teams/$teamId.png")
            .into(binding.ivTeamLogo)

        // 탭 & 페이저 연결
        val tabTitles = listOf("개요", "경기", "순위", "통계", "스쿼드")
        val adapter = TeamDetailPagerAdapter(
            fa         = this,
            teamId     = teamId,
            teamName   = teamName,
            leagueId   = leagueId,
            leagueName = leagueName,
            season     = season,
            tabTitles  = tabTitles
        )
        binding.viewPager.adapter = adapter
        // 6개 탭이 있으므로 offscreen 범위를 넓혀 탭 전환을 부드럽게 유지
        binding.viewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.btnBack.setOnClickListener { finish() }
    }
}