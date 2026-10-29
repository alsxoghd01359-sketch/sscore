package com.example.livescore.league

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.livescore.databinding.ActivityLeagueDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

// 리그 상세: 순위 · 경기 · 플레이어 통계 · 팀 통계 탭
class LeagueDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeagueDetailBinding

    companion object {
        const val EXTRA_LEAGUE_ID = "leagueId"
        const val EXTRA_LEAGUE_NAME = "leagueName"
    }

    private val viewModel: LeagueDetailViewModel by viewModels {
        LeagueDetailViewModel.Factory(intent.getIntExtra(EXTRA_LEAGUE_ID, 39))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeagueDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvLeagueName.text = intent.getStringExtra(EXTRA_LEAGUE_NAME).orEmpty()

        val tabs = listOf("순위", "경기", "플레이어 통계", "팀 통계")
        binding.viewPager.adapter = LeagueDetailPagerAdapter(this, viewModel.leagueId, tabs)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos -> tab.text = tabs[pos] }.attach()

        viewModel.info.observe(this) { info ->
            if (info == null) return@observe
            binding.tvLeagueName.text = info.name
            binding.tvLeagueCountry.text = info.country ?: ""
            Glide.with(this)
                .load(info.logoUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(binding.ivLeagueLogo)
        }
    }
}
