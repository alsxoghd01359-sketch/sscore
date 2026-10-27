package com.example.livescore

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentTeamStatsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamStatsFragment : Fragment() {

    private var _binding: FragmentTeamStatsBinding? = null
    private val binding get() = _binding!!

    private var leagueId      = 0
    private var focusTeamId   = 0
    private var currentSeason = 0
    private var isDropdownOpen = false

    // 전체 랭킹 데이터 저장 (펼치기/접기용)
    private var allRating  = listOf<PlayerRankData>()
    private var allGoals   = listOf<PlayerRankData>()
    private var allAssists = listOf<PlayerRankData>()
    private var showAllRating  = false
    private var showAllGoals   = false
    private var showAllAssists = false

    companion object {
        private const val FROM_SEASON    = 2010
        private const val CURRENT_SEASON = 2025

        fun newInstance(teamId: Int, leagueId: Int, season: Int) =
            TeamStatsFragment().apply {
                arguments = Bundle().apply {
                    putInt("teamId",   teamId)
                    putInt("leagueId", leagueId)
                    putInt("season",   season)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamStatsBinding.inflate(inflater, container, false)
        focusTeamId   = arguments?.getInt("teamId")   ?: 0
        leagueId      = arguments?.getInt("leagueId") ?: 39
        currentSeason = arguments?.getInt("season")
            ?.coerceIn(FROM_SEASON, CURRENT_SEASON) ?: CURRENT_SEASON

        setupSeasonSelector()
        buildSeasonDropdown()
        loadStats(currentSeason)
        return binding.root
    }

    // ── 시즌 선택 ─────────────────────────────────────────────
    private fun setupSeasonSelector() {
        updateSeasonHeader(currentSeason)
        binding.layoutSeasonSelector.setOnClickListener {
            isDropdownOpen = !isDropdownOpen
            binding.scrollSeasonList.visibility = if (isDropdownOpen) View.VISIBLE else View.GONE
            binding.tvDropdownArrow.text = if (isDropdownOpen) "▲" else "▼"
        }
    }

    private fun updateSeasonHeader(season: Int) {
        binding.tvLeagueName.text    = leagueIdToName(leagueId)
        binding.tvCurrentSeason.text = "$season/${season + 1}"
        Glide.with(this).load("https://media.api-sports.io/football/leagues/$leagueId.png")
            .into(binding.ivLeagueLogo)
    }

    private fun buildSeasonDropdown() {
        val container = binding.layoutSeasonList
        container.removeAllViews()
        for (season in CURRENT_SEASON downTo FROM_SEASON) {
            val isSelected = season == currentSeason
            val row = buildSeasonRow(season, isSelected)
            row.setOnClickListener {
                if (season != currentSeason) {
                    currentSeason = season
                    buildSeasonDropdown()
                    updateSeasonHeader(season)
                    loadStats(season)
                }
                isDropdownOpen = false
                binding.scrollSeasonList.visibility = View.GONE
                binding.tvDropdownArrow.text = "▼"
            }
            container.addView(row)
        }
    }

    private fun buildSeasonRow(season: Int, selected: Boolean): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
            setPadding(dp(16), 0, dp(16), 0)
            if (selected) setBackgroundColor(Color.parseColor("#1A2E1A"))

            val logo = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                Glide.with(this@TeamStatsFragment)
                    .load("https://media.api-sports.io/football/leagues/$leagueId.png")
                    .into(this)
            }
            addView(logo)

            val nameCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(10) }
            }
            nameCol.addView(TextView(requireContext()).apply {
                text = leagueIdToName(leagueId); textSize = 12f
                setTextColor(if (selected) Color.parseColor("#20E07A") else Color.WHITE)
            })
            nameCol.addView(TextView(requireContext()).apply {
                text = "$season/${season + 1}"; textSize = 11f
                setTextColor(if (selected) Color.parseColor("#20E07A") else Color.parseColor("#888888"))
            })
            addView(nameCol)

            if (selected) addView(TextView(requireContext()).apply {
                text = "✓"; textSize = 14f
                setTextColor(Color.parseColor("#20E07A"))
            })
        }
    }

    // ── 데이터 로드 ───────────────────────────────────────────
    private fun loadStats(season: Int) {
        binding.pbLoading.visibility = View.VISIBLE
        showAllRating = false; showAllGoals = false; showAllAssists = false

        RetrofitClient.apiService.getTeamStats(focusTeamId, leagueId, season)
            .enqueue(object : Callback<TeamStatsData> {
                override fun onResponse(call: Call<TeamStatsData>, response: Response<TeamStatsData>) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val data = response.body() ?: return
                    renderRecord(data.homeRecord, data.awayRecord)
                    allRating  = data.ratingRanking
                    allGoals   = data.goalRanking
                    allAssists = data.assistRanking
                    renderRanking(binding.layoutRatingList,  allRating,  5, "%.2f")
                    renderRanking(binding.layoutGoalList,    allGoals,   5, "%.0f")
                    renderRanking(binding.layoutAssistList,  allAssists, 5, "%.0f")
                    setupToggle()
                }
                override fun onFailure(call: Call<TeamStatsData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    // ── 홈/원정 성적 ─────────────────────────────────────────
    private fun renderRecord(home: RecordData?, away: RecordData?) {
        fun bind(
            tvPlayed: TextView, tvWin: TextView, tvDraw: TextView,
            tvLose: TextView, tvGoals: TextView, tvGoalDiff: TextView,
            tvPoints: TextView, rec: RecordData?
        ) {
            if (rec == null) return
            tvPlayed.text   = rec.played.toString()
            tvWin.text      = rec.win.toString()
            tvDraw.text     = rec.draw.toString()
            tvLose.text     = rec.lose.toString()
            tvGoals.text    = "${rec.goalsFor}-${rec.goalsAgainst}"
            val diff = rec.goalsDiff
            tvGoalDiff.text = if (diff > 0) "+$diff" else diff.toString()
            tvGoalDiff.setTextColor(when {
                diff > 0 -> Color.parseColor("#20E07A")
                diff < 0 -> Color.parseColor("#FF5555")
                else     -> Color.WHITE
            })
            tvPoints.text = rec.points.toString()
        }
        bind(binding.tvHomePlayed, binding.tvHomeWin, binding.tvHomeDraw,
            binding.tvHomeLose, binding.tvHomeGoals, binding.tvHomeGoalDiff,
            binding.tvHomePoints, home)
        bind(binding.tvAwayPlayed, binding.tvAwayWin, binding.tvAwayDraw,
            binding.tvAwayLose, binding.tvAwayGoals, binding.tvAwayGoalDiff,
            binding.tvAwayPoints, away)
    }

    // ── 선수 랭킹 렌더링 ─────────────────────────────────────
    private fun renderRanking(
        container: LinearLayout,
        list: List<PlayerRankData>,
        limit: Int,
        fmt: String
    ) {
        container.removeAllViews()
        val display = if (limit < list.size) list.take(limit) else list

        if (display.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "데이터 없음 (Phase 2 선수 스탯 적재 필요)"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                setPadding(dp(14), dp(14), dp(14), dp(14))
            })
            return
        }

        display.forEach { player ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_player_rank, container, false)

            row.findViewById<TextView>(R.id.tvPlayerRank).text  = player.rank.toString()
            row.findViewById<TextView>(R.id.tvPlayerName).text  = player.playerName
            row.findViewById<TextView>(R.id.tvPlayerApps).text  = "${player.appearances}경기"
            row.findViewById<TextView>(R.id.tvPlayerValue).text =
                String.format(fmt, player.value)

            Glide.with(this)
                .load(player.photoUrl)
                .circleCrop()
                .placeholder(R.mipmap.ic_launcher)
                .into(row.findViewById(R.id.ivPlayerPhoto))

            container.addView(row)
        }
    }

    // ── 전체보기 토글 ─────────────────────────────────────────
    private fun setupToggle() {
        fun toggle(
            tv: TextView,
            list: List<PlayerRankData>,
            container: LinearLayout,
            fmt: String,
            isExpanded: () -> Boolean,
            setExpanded: (Boolean) -> Unit
        ) {
            tv.setOnClickListener {
                val expand = !isExpanded()
                setExpanded(expand)
                renderRanking(container, list, if (expand) list.size else 5, fmt)
                tv.text = if (expand) "접기 ▲" else "전체보기 ▼"
            }
        }

        toggle(binding.tvRatingToggle, allRating, binding.layoutRatingList, "%.2f",
            { showAllRating }, { showAllRating = it })
        toggle(binding.tvGoalToggle, allGoals, binding.layoutGoalList, "%.0f",
            { showAllGoals }, { showAllGoals = it })
        toggle(binding.tvAssistToggle, allAssists, binding.layoutAssistList, "%.0f",
            { showAllAssists }, { showAllAssists = it })
    }

    private fun leagueIdToName(id: Int) = when (id) {
        39 -> "Premier League"; 140 -> "La Liga"
        78 -> "Bundesliga"; 135 -> "Serie A"; 61 -> "Ligue 1"
        else -> "League $id"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}