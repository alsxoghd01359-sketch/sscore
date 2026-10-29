package com.example.livescore.team

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
import com.example.livescore.*
import com.example.livescore.common.renderState

class TeamStatsFragment : Fragment() {

    private var _binding: FragmentTeamStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: TeamDetailViewModel

    // 전체 랭킹 데이터 저장 (펼치기/접기용)
    private var allRating  = listOf<PlayerRankData>()
    private var allGoals   = listOf<PlayerRankData>()
    private var allAssists = listOf<PlayerRankData>()
    private var showAllRating  = false
    private var showAllGoals   = false
    private var showAllAssists = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = teamViewModel()
        SeasonDropdown(
            this, vm, binding.layoutSeasonSelector, binding.scrollSeasonList, binding.layoutSeasonList,
            binding.tvDropdownArrow, binding.tvLeagueName, binding.tvCurrentSeason, binding.ivLeagueLogo
        ).bind()
        binding.errorView.btnRetry.setOnClickListener { vm.retry(vm.stats) }
        vm.selectedSeason.observe(viewLifecycleOwner) { season -> if (season != null) vm.stats.load(season) }
        vm.stats.state.observe(viewLifecycleOwner) { state ->
            renderState(state, binding.pbLoading, binding.errorView) { data ->
                showAllRating = false; showAllGoals = false; showAllAssists = false
                renderRecord(data.homeRecord, data.awayRecord)
                allRating  = data.ratingRanking
                allGoals   = data.goalRanking
                allAssists = data.assistRanking
                renderRanking(binding.layoutRatingList,  allRating,  5, "%.2f")
                renderRanking(binding.layoutGoalList,    allGoals,   5, "%.0f")
                renderRanking(binding.layoutAssistList,  allAssists, 5, "%.0f")
                setupToggle()
            }
        }
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
                diff > 0 -> Color.parseColor("#F2711C")
                diff < 0 -> Color.parseColor("#DC2626")
                else     -> Color.parseColor("#1A1A1A")
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
                setTextColor(Color.parseColor("#6B7280"))
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}