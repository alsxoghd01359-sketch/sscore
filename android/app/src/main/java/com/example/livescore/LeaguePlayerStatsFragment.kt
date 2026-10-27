package com.example.livescore

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.databinding.FragmentLeaguePlayerStatsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeaguePlayerStatsFragment : Fragment() {

    private var _binding: FragmentLeaguePlayerStatsBinding? = null
    private val binding get() = _binding!!
    private var leagueId = 39
    private var currentSeason = 2025
    private var spinnerReady = false
    private val seasons = (2025 downTo 2015).toList()

    companion object {
        fun newInstance(leagueId: Int) = LeaguePlayerStatsFragment().apply {
            arguments = Bundle().apply { putInt("leagueId", leagueId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaguePlayerStatsBinding.inflate(inflater, container, false)
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
        RetrofitClient.apiService.getLeaguePlayerStats(leagueId, currentSeason)
            .enqueue(object : Callback<LeaguePlayerStatsData> {
                override fun onResponse(
                    call: Call<LeaguePlayerStatsData>, response: Response<LeaguePlayerStatsData>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val d = response.body() ?: return
                    buildSection("득점 순위", d.goalRanking) { "%.0f".format(it.value) }
                    buildSection("도움 순위", d.assistRanking) { "%.0f".format(it.value) }
                    buildSection("공격 포인트", d.attackPointRanking) { "%.0f".format(it.value) }
                    buildSection("평균 평점 순위", d.ratingRanking) { "%.2f".format(it.value) }
                }
                override fun onFailure(call: Call<LeaguePlayerStatsData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    private fun buildSection(
        title: String,
        players: List<PlayerRankData>,
        valueLabel: (PlayerRankData) -> String
    ) {
        val container = binding.layoutContent
        var expanded = false

        // 섹션 헤더
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            text = "▼"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
        }
        header.addView(tvTitle)
        header.addView(tvArrow)
        container.addView(header)

        // 행 컨테이너
        val rowsBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(rowsBox)

        fun renderRows(showAll: Boolean) {
            rowsBox.removeAllViews()
            val list = if (showAll) players else players.take(3)
            list.forEachIndexed { idx, p ->
                if (idx > 0) rowsBox.addView(View(requireContext()).apply {
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(dp(14), 0, dp(14), 0) }
                })
                rowsBox.addView(buildPlayerRow(idx + 1, p, valueLabel(p)))
            }
        }

        renderRows(false)

        // 확장/축소 버튼
        val btnMore = TextView(requireContext()).apply {
            text = "전체보기 ▼"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundColor(Color.parseColor("#181818"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        container.addView(btnMore)

        fun toggle() {
            expanded = !expanded
            renderRows(expanded)
            btnMore.text = if (expanded) "접기 ▲" else "전체보기 ▼"
            tvArrow.text  = if (expanded) "▲" else "▼"
        }
        header.setOnClickListener { toggle() }
        btnMore.setOnClickListener { toggle() }
    }

    private fun buildPlayerRow(rank: Int, p: PlayerRankData, valueStr: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                startActivity(Intent(requireContext(), PlayerDetailActivity::class.java).apply {
                    putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, p.playerId)
                })
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
        // 사진
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                .also { it.marginEnd = dp(10) }
            background = requireContext().getDrawable(R.drawable.bg_player_circle)
        }
        Glide.with(this).load(p.photoUrl)
            .transform(FaceCropTransformation(), CircleCrop())
            .placeholder(R.drawable.bg_player_circle).into(iv)
        row.addView(iv)
        // 이름 + 팀
        val infoCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        infoCol.addView(TextView(requireContext()).apply {
            text = p.playerName
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        infoCol.addView(TextView(requireContext()).apply {
            text = "${p.appearances}경기"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })
        row.addView(infoCol)
        // 값
        row.addView(TextView(requireContext()).apply {
            text = valueStr
            textSize = 16f
            setTextColor(Color.parseColor("#20E07A"))
            setTypeface(null, Typeface.BOLD)
        })
        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}