package com.example.livescore

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import com.example.livescore.auth.SessionManager
import com.example.livescore.auth.showAccountMenu
import com.example.livescore.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var matchAdapter: MatchAdapter
    private lateinit var dateAdapter: DateAdapter

    private var selectedDate: LocalDate = LocalDate.now()
    private var currentLeagueId: Int? = null
    private var currentPosition: Int = Int.MAX_VALUE / 2

    // 진행 중인 API 콜을 취소하기 위한 참조 (날짜 빠르게 바꿀 때 이전 콜 취소)
    private var pendingCall: Call<List<MatchData>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupLeagueTabs()
        setupCalendar()
        updateCalendarButtonText()
        setupAccount()

        loadMatchesForDate()   // 오늘 날짜로 첫 로드
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
            binding.tabAll, binding.tabEpl, binding.tabLaLiga,
            binding.tabSerieA, binding.tabBundesliga, binding.tabLigue1
        )
        fun select(tab: TextView, leagueId: Int?) {
            updateTabUI(tab, tabs)
            currentLeagueId = leagueId
            loadMatchesForDate()
        }
        binding.tabAll.setOnClickListener       { select(binding.tabAll,        null) }
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

    /**
     * 선택된 날짜 + 리그를 서버에 전달해 해당 날짜 경기만 받아옴.
     * 이전 진행 중인 콜은 취소해서 불필요한 응답 처리를 막음.
     */
    private fun loadMatchesForDate() {
        pendingCall?.cancel()   // 이전 요청 취소

        val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)  // "yyyy-MM-dd"

        val call = RetrofitClient.apiService.getMatches(
            date     = dateStr,
            leagueId = currentLeagueId
        )
        pendingCall = call

        call.enqueue(object : Callback<List<MatchData>> {
            override fun onResponse(call: Call<List<MatchData>>, response: Response<List<MatchData>>) {
                if (call.isCanceled) return   // 취소된 콜 응답 무시
                if (response.isSuccessful) {
                    val matches = response.body() ?: emptyList()
                    matchAdapter.updateData(matches)
                }
            }
            override fun onFailure(call: Call<List<MatchData>>, t: Throwable) {
                if (call.isCanceled) return
                Toast.makeText(this@MainActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingCall?.cancel()
    }
}