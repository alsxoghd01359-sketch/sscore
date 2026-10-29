package com.example.livescore

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.livescore.common.renderState
import com.example.livescore.match.HomeMatchesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import com.example.livescore.auth.SessionManager
import com.example.livescore.auth.requireLogin
import com.example.livescore.auth.showAccountMenu
import com.example.livescore.follow.FollowListActivity
import com.example.livescore.follow.FollowStore
import com.example.livescore.push.requestNotificationPermissionWhenLoggedIn
import com.example.livescore.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import com.example.livescore.search.SearchActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var matchAdapter: MatchAdapter
    private lateinit var dateAdapter: DateAdapter

    private var selectedDate: LocalDate = LocalDate.now()
    private var currentLeagueId: Int? = null
    private var followOnly = false              // "팔로우" 탭: 팔로우한 팀의 경기만
    private var loadedMatches: List<MatchData> = emptyList()
    private var currentPosition: Int = Int.MAX_VALUE / 2

    private val viewModel: HomeMatchesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupLeagueTabs()
        setupCalendar()
        updateCalendarButtonText()
        setupAccount()
        requestNotificationPermissionWhenLoggedIn()

        binding.errorView.btnRetry.setOnClickListener { viewModel.retry() }
        viewModel.state.observe(this) { state ->
            renderState(state, binding.pbLoading, binding.errorView) { loadedMatches = it; showMatches() }
        }
        FollowStore.follows.observe(this) { showMatches() }
        loadMatchesForDate()   // 오늘 날짜로 첫 로드
        startLiveRefresh()
    }

    // 화면이 보이는 동안 15초마다 경기 목록을 조용히 다시 받아 점수를 갱신한다 (화면을 벗어나면 멈춘다)
    private fun startLiveRefresh() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(LIVE_REFRESH_MS)
                    viewModel.refreshQuietly()
                }
            }
        }
    }

    // 팔로우 탭이면 팔로우한 팀이 뛰는 경기만 보여 준다
    private fun showMatches() {
        val shown = if (followOnly) {
            val ids = FollowStore.follows.value?.teams?.map { it.id }?.toSet().orEmpty()
            loadedMatches.filter { it.homeTeamId in ids || it.awayTeamId in ids }
        } else loadedMatches
        matchAdapter.updateData(shown)
    }

    // 헤더 계정 버튼: 게스트는 "로그인", 회원은 닉네임을 보여 준다
    private fun setupAccount() {
        binding.btnAccount.setOnClickListener { showAccountMenu() }
        SessionManager.member.observe(this) { member ->
            binding.btnAccount.text = member?.nickname ?: "로그인"
        }
        SessionManager.expired.observe(this) { expired ->
            if (expired) {
                Toast.makeText(this, "로그인이 만료되었습니다. 다시 로그인해 주세요.", Toast.LENGTH_LONG).show()
                SessionManager.consumeExpired()
            }
        }
    }

    private fun updateCalendarButtonText() {
        val shortYear = selectedDate.year % 100
        binding.btnCalendar.text = "$shortYear/${selectedDate.monthValue}/${selectedDate.dayOfMonth} ▼"
    }

    private fun setupRecyclerViews() {
        dateAdapter = DateAdapter { date, position ->
            selectedDate = date
            moveDateListToPosition(position)
            updateCalendarButtonText()
            loadMatchesForDate()
        }

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDateList.layoutManager = layoutManager
        binding.rvDateList.adapter = dateAdapter

        binding.rvDateList.post {
            val offset = (binding.rvDateList.width / 2) - 100
            layoutManager.scrollToPositionWithOffset(dateAdapter.centerPosition, offset)
        }
        currentPosition = dateAdapter.centerPosition

        matchAdapter = MatchAdapter(mutableListOf())
        binding.rvMatches.layoutManager = LinearLayoutManager(this)
        binding.rvMatches.adapter = matchAdapter
    }

    private fun setupLeagueTabs() {
        val tabs = listOf(
            binding.tabAll, binding.tabFollow, binding.tabEpl, binding.tabLaLiga,
            binding.tabSerieA, binding.tabBundesliga, binding.tabLigue1
        )
        fun select(tab: TextView, leagueId: Int?) {
            updateTabUI(tab, tabs)
            currentLeagueId = leagueId
            followOnly = tab === binding.tabFollow
            loadMatchesForDate()
            showMatches()
        }
        binding.tabAll.setOnClickListener       { select(binding.tabAll,        null) }
        binding.tabFollow.setOnClickListener    { requireLogin { select(binding.tabFollow, null) } }
        binding.tabEpl.setOnClickListener       { select(binding.tabEpl,        39)   }
        binding.tabLaLiga.setOnClickListener    { select(binding.tabLaLiga,     140)  }
        binding.tabSerieA.setOnClickListener    { select(binding.tabSerieA,     135)  }
        binding.tabBundesliga.setOnClickListener{ select(binding.tabBundesliga, 78)   }
        binding.tabLigue1.setOnClickListener    { select(binding.tabLigue1,     61)   }
    }

    private fun updateTabUI(selectedTab: TextView, allTabs: List<TextView>) {
        allTabs.forEach { it.setTextColor(Color.parseColor("#6B7280")) }
        selectedTab.setTextColor(Color.parseColor("#F2711C"))
    }

    private fun setupCalendar() {
        binding.navSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.navFollow.setOnClickListener {
            requireLogin { startActivity(Intent(this, FollowListActivity::class.java)) }
        }
        binding.btnCalendar.setOnClickListener {
            DatePickerDialog(this,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    val diffDays = ChronoUnit.DAYS.between(LocalDate.now(), selectedDate).toInt()
                    val targetPos = dateAdapter.centerPosition + diffDays
                    moveDateListToPosition(targetPos)
                    dateAdapter.setSelectedPosition(targetPos)
                    updateCalendarButtonText()
                    loadMatchesForDate()
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            ).show()
        }
    }

    private fun moveDateListToPosition(targetPosition: Int) {
        val lm = binding.rvDateList.layoutManager as LinearLayoutManager
        val jump = abs(targetPosition - currentPosition)
        if (jump >= 15) {
            val offset = if (binding.rvDateList.width > 0) (binding.rvDateList.width / 2) - 100 else 300
            lm.scrollToPositionWithOffset(targetPosition, offset)
        } else {
            val scroller = object : LinearSmoothScroller(this) {
                override fun calculateDtToFit(vs: Int, ve: Int, bs: Int, be: Int, snap: Int) =
                    (bs + (be - bs) / 2) - (vs + (ve - vs) / 2)
            }
            scroller.targetPosition = targetPosition
            lm.startSmoothScroll(scroller)
        }
        currentPosition = targetPosition
    }

    /** 선택된 날짜 + 리그의 경기를 받아온다. 이전 요청은 ViewModel이 취소한다. */
    private fun loadMatchesForDate() {
        viewModel.show(selectedDate, currentLeagueId)
    }

    private companion object {
        const val LIVE_REFRESH_MS = 15_000L
    }
}
