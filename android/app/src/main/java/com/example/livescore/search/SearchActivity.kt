package com.example.livescore.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.MainActivity
import com.example.livescore.SearchResultData
import com.example.livescore.databinding.ActivitySearchBinding
import com.example.livescore.league.LeagueDetailActivity
import com.example.livescore.player.PlayerDetailActivity
import com.example.livescore.team.TeamDetailActivity

// 통합 검색: 선수 · 구단 · 리그를 한 번에 찾는다
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private val viewModel: SearchViewModel by viewModels()
    private val pickTeam get() = intent.getBooleanExtra(EXTRA_PICK_TEAM, false)   // 응원 팀 고르기: 팀만 보여 주고 고르면 돌려준다

    companion object {
        const val EXTRA_PICK_TEAM = "pickTeam"
        const val RESULT_TEAM_ID = "teamId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상태바 → 검색바 상단 패딩 / 시스템 네비 → 바텀 네비 하단 패딩
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.searchBar.updatePadding(top = statusBar)
            binding.searchBar.updateLayoutParams { height = statusBar + (56 * resources.displayMetrics.density).toInt() }
            binding.bottomNav.updatePadding(bottom = navBar)
            binding.bottomNav.updateLayoutParams { height = navBar + (72 * resources.displayMetrics.density).toInt() }
            WindowInsetsCompat.CONSUMED
        }

        adapter = SearchResultAdapter { item -> navigateTo(item) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                binding.btnClear.visibility = if (q.trim().isEmpty()) View.GONE else View.VISIBLE
                viewModel.onQueryChanged(q)
            }
        })
        binding.btnClear.setOnClickListener { binding.etSearch.setText("") }
        binding.errorView.btnRetry.setOnClickListener { viewModel.retry() }
        binding.btnBack.setOnClickListener { finish() }

        // 하단 네비게이션 (검색 탭은 현재 화면이므로 아무것도 하지 않는다)
        binding.navMatch.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        viewModel.state.observe(this) { render(it) }

        // 키보드 자동 열기
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etSearch.postDelayed({ imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT) }, 100)
    }

    private fun render(state: SearchState) {
        binding.pbLoading.visibility = if (state is SearchState.Loading) View.VISIBLE else View.GONE
        binding.errorView.root.visibility = if (state is SearchState.Error) View.VISIBLE else View.GONE
        binding.rvResults.visibility = if (state is SearchState.Results) View.VISIBLE else View.GONE

        val message = when (state) {
            SearchState.Idle -> "선수, 구단, 리그 이름을 2글자 이상 입력해 주세요"
            SearchState.Empty -> "검색 결과가 없습니다"
            else -> null
        }
        binding.tvEmpty.text = message.orEmpty()
        binding.tvEmpty.visibility = if (message != null) View.VISIBLE else View.GONE

        when (state) {
            is SearchState.Results -> adapter.submitList(if (pickTeam) state.items.filter { it.type == "TEAM" } else state.items)
            is SearchState.Error -> binding.errorView.tvErrorMessage.text = state.message
            else -> adapter.submitList(emptyList())
        }
    }

    private fun navigateTo(item: SearchResultData) {
        if (pickTeam) {
            if (item.type == "TEAM") {
                setResult(RESULT_OK, Intent().putExtra(RESULT_TEAM_ID, item.id.toInt()))
                finish()
            }
            return
        }
        when (item.type) {
            "PLAYER" -> startActivity(
                Intent(this, PlayerDetailActivity::class.java).apply {
                    putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, item.id)
                }
            )
            "TEAM" -> startActivity(
                Intent(this, TeamDetailActivity::class.java).apply {
                    putExtra(TeamDetailActivity.EXTRA_TEAM_ID, item.id.toInt())
                    putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, item.name)
                    putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, item.leagueId ?: 0)
                }
            )
            "LEAGUE" -> startActivity(
                Intent(this, LeagueDetailActivity::class.java).apply {
                    putExtra(LeagueDetailActivity.EXTRA_LEAGUE_ID, item.id.toInt())
                    putExtra(LeagueDetailActivity.EXTRA_LEAGUE_NAME, item.name)
                }
            )
        }
    }
}
