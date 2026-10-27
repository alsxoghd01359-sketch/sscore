package com.example.livescore

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentLeagueTeamStatsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeagueTeamStatsFragment : Fragment() {

    private var _binding: FragmentLeagueTeamStatsBinding? = null
    private val binding get() = _binding!!
    private var leagueId = 39
    private var currentSeason = 2025
    private var spinnerReady = false
    private val seasons = (2025 downTo 2015).toList()

    companion object {
        fun newInstance(leagueId: Int) = LeagueTeamStatsFragment().apply {
            arguments = Bundle().apply { putInt("leagueId", leagueId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeagueTeamStatsBinding.inflate(inflater, container, false)
        leagueId = arguments?.getInt("leagueId") ?: 39
        setupSpinner()
        load()
        return binding.root
    }

    private fun setupSpinner() {
        val labels = seasons.map { "$it/${(it+1).toString().takeLast(2)}" }
        binding.spinnerSeason.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!spinnerReady) { spinnerReady = true; return }
                currentSeason = seasons[pos]; load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun load() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.layoutContent.removeAllViews()
        RetrofitClient.apiService.getLeagueTeamStats(leagueId, currentSeason)
            .enqueue(object : Callback<LeagueTeamStatsData> {
                override fun onResponse(
                    call: Call<LeagueTeamStatsData>, response: Response<LeagueTeamStatsData>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val d = response.body() ?: return
                    buildSection("선수단 평균 평점", d.avgRatingRanking)
                    buildSection("경기당 득점", d.goalsPerGameRanking)
                    buildSection("경기당 실점 (낮은 순)", d.concededPerGameRanking)
                    buildSection("클린시트", d.cleanSheetRanking)
                }
                override fun onFailure(call: Call<LeagueTeamStatsData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    private fun buildSection(title: String, teams: List<TeamRankData>) {
        val container = binding.layoutContent
        var expanded  = false

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(12) }
        }
        val tvTitle = TextView(requireContext()).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvArrow = TextView(requireContext()).apply {
            text = "▼"; textSize = 13f
            setTextColor(Color.parseColor("#888888"))
        }
        header.addView(tvTitle); header.addView(tvArrow)
        container.addView(header)

        val rowsBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(rowsBox)

        fun renderRows(showAll: Boolean) {
            rowsBox.removeAllViews()
            val list = if (showAll) teams else teams.take(3)
            list.forEachIndexed { idx, t ->
                if (idx > 0) rowsBox.addView(View(requireContext()).apply {
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(dp(14), 0, dp(14), 0) }
                })
                rowsBox.addView(buildTeamRow(idx + 1, t))
            }
        }
        renderRows(false)

        val btnMore = TextView(requireContext()).apply {
            text = "전체보기 ▼"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundColor(Color.parseColor("#181818"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(btnMore)

        fun toggle() {
            expanded = !expanded
            renderRows(expanded)
            btnMore.text = if (expanded) "접기 ▲" else "전체보기 ▼"
            tvArrow.text = if (expanded) "▲" else "▼"
        }
        header.setOnClickListener { toggle() }
        btnMore.setOnClickListener { toggle() }
    }

    private fun buildTeamRow(rank: Int, t: TeamRankData): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                t.teamId?.let { id ->
                    startActivity(Intent(requireContext(), TeamDetailActivity::class.java).apply {
                        putExtra(TeamDetailActivity.EXTRA_TEAM_ID,   id)
                        putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, t.teamName)
                        putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, leagueId)
                    })
                }
            }
        }
        // 순위
        row.addView(TextView(requireContext()).apply {
            text = "$rank"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        // 로고
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                .also { it.marginEnd = dp(10) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(this).load(t.logoUrl).into(iv)
        row.addView(iv)
        // 팀 이름
        row.addView(TextView(requireContext()).apply {
            text = t.teamName
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, Typeface.BOLD)
        })
        // 값
        row.addView(TextView(requireContext()).apply {
            text = t.displayValue
            textSize = 16f
            setTextColor(Color.parseColor("#20E07A"))
            setTypeface(null, Typeface.BOLD)
        })
        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}