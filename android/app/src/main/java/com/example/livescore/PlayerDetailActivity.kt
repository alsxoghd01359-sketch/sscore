package com.example.livescore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.databinding.ActivityPlayerDetailBinding
import com.google.android.material.tabs.TabLayoutMediator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlayerDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYER_ID = "playerId"
    }

    private lateinit var binding: ActivityPlayerDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playerId = intent.getLongExtra(EXTRA_PLAYER_ID, -1L)

        // 뒤로가기
        binding.btnBack.setOnClickListener { finish() }

        // 탭 & 페이저 (경력 탭 제외)
        val tabs = listOf("프로필", "경기", "통계")
        val adapter = PlayerDetailPagerAdapter(this, playerId, tabs)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabs[pos]
        }.attach()

        // 헤더 데이터 로드
        if (playerId != -1L) loadHeader(playerId)
    }

    private fun loadHeader(playerId: Long) {
        RetrofitClient.apiService.getPlayerDetail(playerId)
            .enqueue(object : Callback<PlayerDetailData> {
                override fun onResponse(
                    call: Call<PlayerDetailData>, response: Response<PlayerDetailData>
                ) {
                    val d = response.body() ?: return
                    binding.tvPlayerName.text = d.name
                    binding.tvPlayerTeam.text = d.currentTeamName ?: ""
                    Glide.with(this@PlayerDetailActivity)
                        .load(d.photoUrl)
                        .transform(FaceCropTransformation(), CircleCrop())
                        .placeholder(R.drawable.bg_player_circle)
                        .into(binding.ivPlayerPhoto)
                }
                override fun onFailure(call: Call<PlayerDetailData>, t: Throwable) {}
            })
    }
}