package com.example.livescore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.livescore.databinding.ActivityLeagueDetailBinding
import com.google.android.material.tabs.TabLayoutMediator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeagueDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeagueDetailBinding

    companion object {
        const val EXTRA_LEAGUE_ID   = "leagueId"
        const val EXTRA_LEAGUE_NAME = "leagueName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeagueDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val leagueId   = intent.getIntExtra(EXTRA_LEAGUE_ID, 39)
        val leagueName = intent.getStringExtra(EXTRA_LEAGUE_NAME) ?: ""

        binding.btnBack.setOnClickListener { finish() }
        binding.tvLeagueName.text = leagueName

        setupTabs(leagueId)
        loadLeagueInfo(leagueId)
    }

    private fun setupTabs(leagueId: Int) {
        val tabs = listOf("순위", "경기", "플레이어 통계", "팀 통계")
        binding.viewPager.adapter = LeagueDetailPagerAdapter(this, leagueId, tabs)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabs[pos]
        }.attach()
    }

    private fun loadLeagueInfo(leagueId: Int) {
        RetrofitClient.apiService.getLeagueInfo(leagueId)
            .enqueue(object : Callback<LeagueInfoData> {
                override fun onResponse(
                    call: Call<LeagueInfoData>, response: Response<LeagueInfoData>
                ) {
                    val d = response.body() ?: return
                    binding.tvLeagueName.text    = d.name
                    binding.tvLeagueCountry.text = d.country ?: ""
                    Glide.with(this@LeagueDetailActivity)
                        .load(d.logoUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(binding.ivLeagueLogo)
                }
                override fun onFailure(call: Call<LeagueInfoData>, t: Throwable) {}
            })
    }
}