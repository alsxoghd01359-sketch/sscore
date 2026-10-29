package com.example.livescore.team

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.example.livescore.databinding.ActivityTeamDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

// 팀 상세: 개요 · 경기 · 순위 · 통계 · 스쿼드 탭
class TeamDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEAM_ID = "teamId"
        const val EXTRA_TEAM_NAME = "teamName"
        const val EXTRA_LEAGUE_ID = "leagueId"
    }

    private lateinit var binding: ActivityTeamDetailBinding
    private val viewModel: TeamDetailViewModel by viewModels { TeamDetailViewModel.Factory(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTeamName.text = intent.getStringExtra(EXTRA_TEAM_NAME) ?: "구단"
        binding.tvLeagueName.text = intent.getStringExtra("leagueName").orEmpty()
        Glide.with(this)
            .load("https://media.api-sports.io/football/teams/${viewModel.teamId}.png")
            .into(binding.ivTeamLogo)

        val tabTitles = listOf("개요", "경기", "순위", "통계", "스쿼드")
        binding.viewPager.adapter = TeamDetailPagerAdapter(this, tabTitles)
        binding.viewPager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.btnBack.setOnClickListener { finish() }

        // 인텐트에 리그 이름이 없었다면 서버가 알려 준 소속 리그 이름을 보여 준다
        viewModel.info.observe(this) { info ->
            if (info != null && binding.tvLeagueName.text.isNullOrEmpty()) {
                binding.tvLeagueName.text = info.leagueName.orEmpty()
            }
        }
    }
}

class TeamDetailPagerAdapter(
    fa: FragmentActivity,
    private val tabTitles: List<String>
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment = when (tabTitles[position]) {
        "개요" -> TeamOverviewFragment()
        "경기" -> TeamMatchesFragment()
        "순위" -> TeamStandingsFragment()
        "통계" -> TeamStatsFragment()
        "스쿼드" -> TeamSquadFragment()
        else -> error("알 수 없는 탭: ${tabTitles[position]}")
    }
}

// 팀 상세 화면(액티비티)이 만든 ViewModel을 탭 프래그먼트들이 함께 쓴다
fun Fragment.teamViewModel(): TeamDetailViewModel =
    ViewModelProvider(requireActivity(), TeamDetailViewModel.Factory(requireActivity().intent))[TeamDetailViewModel::class.java]
