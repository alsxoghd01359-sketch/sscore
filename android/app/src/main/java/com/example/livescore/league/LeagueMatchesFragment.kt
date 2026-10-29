package com.example.livescore.league

import com.example.livescore.match.MatchDetailActivity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentLeagueMatchesBinding
import com.example.livescore.*
import com.example.livescore.common.renderState

class LeagueMatchesFragment : Fragment() {

    private var _binding: FragmentLeagueMatchesBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: LeagueDetailViewModel
    private val leagueId get() = vm.leagueId
    private val selectedSeason get() = vm.selectedSeason.value ?: 0

    companion object {
        fun newInstance(leagueId: Int) = LeagueMatchesFragment().apply {
            arguments = Bundle().apply { putInt(ARG_LEAGUE_ID, leagueId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeagueMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = leagueViewModel()
        binding.spinnerSeason.bindSeasons(viewLifecycleOwner, vm)
        binding.errorView.btnRetry.setOnClickListener { vm.retry(vm.matches) }
        vm.selectedSeason.observe(viewLifecycleOwner) { season -> if (season != null) vm.matches.load(season) }
        vm.matches.state.observe(viewLifecycleOwner) { state ->
            binding.layoutMatchDays.removeAllViews()
            renderState(state, binding.pbLoading, binding.errorView) { data ->
                data.forEach { renderMatchDay(it) }
            }
        }
    }

    private fun renderMatchDay(day: LeagueMatchDayData) {
        val container = binding.layoutMatchDays

        // 날짜 헤더
        container.addView(TextView(requireContext()).apply {
            text = formatDate(day.date)
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(12), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // 날짜 박스
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F5F7"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(4) }
        }

        day.matches.forEachIndexed { idx, match ->
            // 구분선 (첫 행 제외)
            if (idx > 0) {
                box.addView(View(requireContext()).apply {
                    setBackgroundColor(Color.parseColor("#E6E8EB"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(dp(12), 0, dp(12), 0) }
                })
            }
            box.addView(buildMatchRow(match))
        }

        container.addView(box)
    }

    private fun buildMatchRow(match: MatchData): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                match.fixtureId?.let { fid ->
                    startActivity(Intent(requireContext(), MatchDetailActivity::class.java).apply {
                        putExtra("fixtureId", fid)
                        putExtra("homeTeam",   match.homeTeam)
                        putExtra("awayTeam",   match.awayTeam)
                        putExtra("homeTeamId", match.homeTeamId ?: 0)
                        putExtra("awayTeamId", match.awayTeamId ?: 0)
                        putExtra("matchTime",  match.matchTime)
                        putExtra("score",      match.score)
                        putExtra("leagueId",   leagueId)
                        putExtra("season",     selectedSeason)
                    })
                }
            }
        }

        // 홈팀 (로고 + 이름) - 우측 정렬
        val homeLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
        }
        homeLayout.addView(TextView(requireContext()).apply {
            text = match.homeTeam
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        homeLayout.addView(ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                .also { it.marginStart = dp(8) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            Glide.with(this@LeagueMatchesFragment).load(match.homeLogoUrl).into(this)
        })
        row.addView(homeLayout)

        // 스코어 or 시간
        val isFinished = (match.score ?: "").contains("-") && match.score != "0 - 0"
                && match.matchTime != "TBD"
        row.addView(TextView(requireContext()).apply {
            text = if (isFinished) (match.score ?: "") else (match.matchTime ?: "")
            textSize = if (isFinished) 16f else 13f
            setTextColor(if (isFinished) Color.parseColor("#1A1A1A") else Color.parseColor("#6B7280"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(80),
                LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // 어웨이팀 (로고 + 이름) - 좌측 정렬
        val awayLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
        }
        awayLayout.addView(ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                .also { it.marginEnd = dp(8) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            Glide.with(this@LeagueMatchesFragment).load(match.awayLogoUrl).into(this)
        })
        awayLayout.addView(TextView(requireContext()).apply {
            text = match.awayTeam
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(awayLayout)

        return row
    }

    private fun formatDate(raw: String): String {
        return try {
            val parts = raw.split("-")
            val days = arrayOf("일","월","화","수","목","금","토")
            val cal = java.util.Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            "${parts[0]}.${parts[1]}.${parts[2]} (${days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]})"
        } catch (e: Exception) { raw }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}