package com.example.livescore

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentTeamStandingsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamStandingsFragment : Fragment() {

    private var _binding: FragmentTeamStandingsBinding? = null
    private val binding get() = _binding!!

    private var leagueId    = 0
    private var focusTeamId = 0
    private var currentSeason = 0
    private var isDropdownOpen = false

    companion object {
        // DB에 있는 시즌 범위 (2010~현재)
        private const val FROM_SEASON = 2010
        private val CURRENT_SEASON   = 2025

        fun newInstance(leagueId: Int, season: Int, focusTeamId: Int) =
            TeamStandingsFragment().apply {
                arguments = Bundle().apply {
                    putInt("leagueId", leagueId)
                    putInt("season", season)
                    putInt("focusTeamId", focusTeamId)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamStandingsBinding.inflate(inflater, container, false)

        leagueId     = arguments?.getInt("leagueId")    ?: 39
        focusTeamId  = arguments?.getInt("focusTeamId") ?: 0
        currentSeason = arguments?.getInt("season")?.coerceIn(FROM_SEASON, CURRENT_SEASON)
            ?: CURRENT_SEASON

        setupRecyclerView()
        setupSeasonHeader()
        buildSeasonDropdown()
        loadStandings(currentSeason)
        return binding.root
    }

    // ── RecyclerView ───────────────────────────────────────────
    private fun setupRecyclerView() {
        binding.rvTeamStandings.layoutManager = LinearLayoutManager(requireContext())
    }

    // ── 상단 시즌 선택 바 ───────────────────────────────────────
    private fun setupSeasonHeader() {
        updateSeasonHeader(currentSeason)

        binding.layoutSeasonSelector.setOnClickListener {
            isDropdownOpen = !isDropdownOpen
            binding.scrollSeasonList.visibility =
                if (isDropdownOpen) View.VISIBLE else View.GONE
            binding.tvDropdownArrow.text = if (isDropdownOpen) "▲" else "▼"
        }
    }

    private fun updateSeasonHeader(season: Int) {
        binding.tvLeagueName.text    = leagueIdToName(leagueId)
        binding.tvCurrentSeason.text = "${season}/${season + 1}"
        Glide.with(this)
            .load("https://media.api-sports.io/football/leagues/$leagueId.png")
            .into(binding.ivLeagueLogo)
    }

    // ── 드롭다운 시즌 목록 (최신순) ─────────────────────────────
    private fun buildSeasonDropdown() {
        val container = binding.layoutSeasonList
        container.removeAllViews()

        for (season in CURRENT_SEASON downTo FROM_SEASON) {
            val isSelected = season == currentSeason
            val row = buildSeasonRow(season, isSelected)
            row.setOnClickListener {
                if (season != currentSeason) {
                    currentSeason = season
                    rebuildDropdownSelection()
                    updateSeasonHeader(season)
                    loadStandings(season)
                }
                // 드롭다운 닫기
                isDropdownOpen = false
                binding.scrollSeasonList.visibility = View.GONE
                binding.tvDropdownArrow.text = "▼"
            }
            container.addView(row)
        }
    }

    private fun buildSeasonRow(season: Int, isSelected: Boolean): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            setPadding(dp(16), 0, dp(16), 0)
            if (isSelected) setBackgroundColor(Color.parseColor("#1A2E1A"))

            // 리그 로고
            val logo = android.widget.ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                Glide.with(this@TeamStandingsFragment)
                    .load("https://media.api-sports.io/football/leagues/$leagueId.png")
                    .into(this)
            }
            addView(logo)

            // 리그명 + 시즌
            val nameCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(10) }
            }

            val tvName = android.widget.TextView(requireContext()).apply {
                text = leagueIdToName(leagueId)
                textSize = 12f
                setTextColor(if (isSelected) Color.parseColor("#20E07A") else Color.WHITE)
            }
            val tvSeason = android.widget.TextView(requireContext()).apply {
                text = "$season/${season + 1}"
                textSize = 11f
                setTextColor(if (isSelected) Color.parseColor("#20E07A") else Color.parseColor("#888888"))
            }
            nameCol.addView(tvName)
            nameCol.addView(tvSeason)
            addView(nameCol)

            // 선택 체크
            if (isSelected) {
                val check = android.widget.TextView(requireContext()).apply {
                    text = "✓"
                    textSize = 14f
                    setTextColor(Color.parseColor("#20E07A"))
                }
                addView(check)
            }
        }
    }

    // 드롭다운 목록 선택 상태 갱신
    private fun rebuildDropdownSelection() {
        buildSeasonDropdown()
    }

    // ── 순위표 로드 ─────────────────────────────────────────────
    private fun loadStandings(season: Int) {
        binding.pbStandingsLoading.visibility = View.VISIBLE
        binding.rvTeamStandings.visibility    = View.GONE

        RetrofitClient.apiService.getStandings(leagueId, season)
            .enqueue(object : Callback<List<StandingData>> {
                override fun onResponse(
                    call: Call<List<StandingData>>,
                    response: Response<List<StandingData>>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbStandingsLoading.visibility = View.GONE
                    binding.rvTeamStandings.visibility    = View.VISIBLE

                    val standings = response.body() ?: emptyList()
                    // focusTeamId 하이라이트 (홈/어웨이 파라미터에 같은 값 넣어 강조)
                    binding.rvTeamStandings.adapter = StandingsAdapter(
                        standings, focusTeamId, focusTeamId
                    ) { standing ->
                        openTeamDetail(standing.teamId, standing.teamName)
                    }

                    // 해당 팀 위치로 자동 스크롤
                    val focusPos = standings.indexOfFirst { it.teamId == focusTeamId }
                    if (focusPos >= 0) {
                        (binding.rvTeamStandings.layoutManager as LinearLayoutManager)
                            .scrollToPositionWithOffset(
                                (focusPos - 2).coerceAtLeast(0), dp(8)
                            )
                    }
                }

                override fun onFailure(call: Call<List<StandingData>>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbStandingsLoading.visibility = View.GONE
                }
            })
    }

    // ── 헬퍼 ────────────────────────────────────────────────────
    private fun openTeamDetail(teamId: Int, teamName: String) {
        val intent = Intent(requireContext(), TeamDetailActivity::class.java).apply {
            putExtra("teamId",     teamId)
            putExtra("teamName",   teamName)
            putExtra("leagueId",   leagueId)
            putExtra("leagueName", leagueIdToName(leagueId))
            putExtra("season",     currentSeason)
        }
        startActivity(intent)
    }

    private fun leagueIdToName(id: Int) = when (id) {
        39  -> "Premier League"
        140 -> "La Liga"
        78  -> "Bundesliga"
        135 -> "Serie A"
        61  -> "Ligue 1"
        else -> "League $id"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}