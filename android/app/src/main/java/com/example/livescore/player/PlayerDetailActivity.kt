package com.example.livescore.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.FaceCropTransformation
import com.example.livescore.R
import com.example.livescore.RetrofitClient
import com.example.livescore.common.SimpleLoader
import com.example.livescore.common.UiState
import com.example.livescore.databinding.ActivityPlayerDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

// 선수 상세 화면 전체(헤더 + 3개 탭)가 공유하는 상태. 선수 세부 정보는 헤더와 프로필 탭이 같이 쓴다.
class PlayerDetailViewModel(
    val playerId: Long,
    repository: PlayerRepository = PlayerRepository(RetrofitClient.playerApi)
) : ViewModel() {

    val detail = SimpleLoader(viewModelScope) { repository.detail(playerId) }
    val matches = SimpleLoader(viewModelScope) { repository.matches(playerId) }
    val seasonStats = SimpleLoader(viewModelScope) { repository.seasonStats(playerId) }

    init {
        detail.ensureLoaded()
    }

    class Factory(private val intent: Intent) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerDetailViewModel(intent.getLongExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, -1L)) as T
    }
}

// 선수 상세: 프로필 · 경기 · 통계 탭
class PlayerDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYER_ID = "playerId"
    }

    private lateinit var binding: ActivityPlayerDetailBinding
    private val viewModel: PlayerDetailViewModel by viewModels { PlayerDetailViewModel.Factory(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val tabs = listOf("프로필", "경기", "통계")
        binding.viewPager.adapter = PlayerDetailPagerAdapter(this, tabs)
        binding.viewPager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos -> tab.text = tabs[pos] }.attach()

        // 헤더는 불러오지 못해도 비워 둔다(프로필 탭이 오류와 다시 시도를 안내한다)
        viewModel.detail.state.observe(this) { state ->
            if (state is UiState.Success) {
                val d = state.data
                binding.tvPlayerName.text = d.name
                binding.tvPlayerTeam.text = d.currentTeamName ?: ""
                Glide.with(this)
                    .load(d.photoUrl)
                    .transform(FaceCropTransformation(), CircleCrop())
                    .placeholder(R.drawable.bg_player_circle)
                    .into(binding.ivPlayerPhoto)
            }
        }
    }
}

class PlayerDetailPagerAdapter(
    activity: FragmentActivity,
    private val tabs: List<String>
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment = when (tabs[position]) {
        "프로필" -> PlayerProfileFragment()
        "경기" -> PlayerMatchesFragment()
        "통계" -> PlayerStatsFragment()
        else -> error("알 수 없는 탭: ${tabs[position]}")
    }
}

// 선수 상세 화면(액티비티)이 만든 ViewModel을 탭 프래그먼트들이 함께 쓴다
fun Fragment.playerViewModel(): PlayerDetailViewModel =
    ViewModelProvider(requireActivity(), PlayerDetailViewModel.Factory(requireActivity().intent))[PlayerDetailViewModel::class.java]
