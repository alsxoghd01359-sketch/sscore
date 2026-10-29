package com.example.livescore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.example.livescore.league.LeagueDetailActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.databinding.ActivitySearchBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private val handler  = Handler(Looper.getMainLooper())
    private var searchJob: Runnable? = null
    private var currentCall: Call<List<SearchResultData>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상태바 → 검색바 상단 패딩 / 시스템 네비 → 바텀 네비 하단 패딩
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBar    = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.searchBar.updatePadding(top = statusBar)
            binding.searchBar.updateLayoutParams { height = statusBar + (56 * resources.displayMetrics.density).toInt() }
            binding.bottomNav.updatePadding(bottom = navBar)
            binding.bottomNav.updateLayoutParams { height = navBar + (72 * resources.displayMetrics.density).toInt() }
            WindowInsetsCompat.CONSUMED
        }

        setupRecycler()
        setupSearchBar()
        binding.btnBack.setOnClickListener { finish() }

        // 하단 네비게이션
        binding.navMatch.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        // navSearch는 현재 화면이므로 클릭해도 아무것도 안함

        // 키보드 자동 열기
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etSearch.postDelayed({
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun setupRecycler() {
        adapter = SearchResultAdapter { item -> navigateTo(item) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                binding.btnClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE

                searchJob?.let { handler.removeCallbacks(it) }
                if (q.length < 2) {
                    adapter.submitList(emptyList())
                    showEmpty(true)
                    return
                }
                // 300ms 디바운스
                searchJob = Runnable { performSearch(q) }
                handler.postDelayed(searchJob!!, 300)
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            adapter.submitList(emptyList())
            showEmpty(true)
        }
    }

    private fun performSearch(q: String) {
        currentCall?.cancel()
        currentCall = RetrofitClient.apiService.search(q).also { call ->
            call.enqueue(object : Callback<List<SearchResultData>> {
                override fun onResponse(
                    call: Call<List<SearchResultData>>,
                    response: Response<List<SearchResultData>>
                ) {
                    val list = response.body() ?: emptyList()
                    adapter.submitList(list)
                    showEmpty(list.isEmpty())
                }
                override fun onFailure(call: Call<List<SearchResultData>>, t: Throwable) {
                    if (call.isCanceled) return
                    adapter.submitList(emptyList())
                    showEmpty(true)
                    // 검색 실패
                }
            })
        }
    }

    private fun navigateTo(item: SearchResultData) {
        when (item.type) {
            "PLAYER" -> startActivity(
                Intent(this, PlayerDetailActivity::class.java).apply {
                    putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, item.id)
                }
            )
            "TEAM" -> startActivity(
                Intent(this, TeamDetailActivity::class.java).apply {
                    putExtra(TeamDetailActivity.EXTRA_TEAM_ID,   item.id.toInt())
                    putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, item.name)
                    putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, item.leagueId ?: 39)
                }
            )
            "LEAGUE" -> startActivity(
                Intent(this, LeagueDetailActivity::class.java).apply {
                    putExtra(LeagueDetailActivity.EXTRA_LEAGUE_ID,   item.id.toInt())
                    putExtra(LeagueDetailActivity.EXTRA_LEAGUE_NAME, item.name)
                }
            )
        }
    }

    private fun showEmpty(show: Boolean) {
        binding.tvEmpty.visibility  = if (show) View.VISIBLE else View.GONE
        binding.rvResults.visibility = if (show) View.GONE   else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCall?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}