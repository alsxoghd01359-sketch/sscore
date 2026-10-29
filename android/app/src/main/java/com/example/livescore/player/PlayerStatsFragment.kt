package com.example.livescore.player

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.livescore.databinding.FragmentPlayerStatsBinding
import com.example.livescore.*
import com.example.livescore.common.renderState

class PlayerStatsFragment : Fragment() {

    private var _binding: FragmentPlayerStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: PlayerDetailViewModel
    private var seasonList = listOf<PlayerSeasonStatsDetailData>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = playerViewModel()
        binding.errorView.btnRetry.setOnClickListener { vm.seasonStats.retry() }
        vm.seasonStats.ensureLoaded()
        vm.seasonStats.state.observe(viewLifecycleOwner) { state ->
            renderState(state, binding.pbLoading, binding.errorView) { data ->
                seasonList = data
                if (data.isNotEmpty()) setupSpinner()
            }
        }
    }

    private fun setupSpinner() {
        val labels = seasonList.map {
            "${it.season}/${(it.season+1).toString().takeLast(2)}  ${it.leagueName}  (${it.teamName})"
        }
        binding.spinnerSeason.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                renderStats(seasonList[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        renderStats(seasonList[0])
    }

    private fun renderStats(d: PlayerSeasonStatsDetailData) {
        val c = binding.layoutStats
        c.removeAllViews()

        // ── 시즌 요약 ──────────────────────────────────────────
        c.addView(sectionHeader("시즌 요약"))

        val summaryBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F4F5F7"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80)
            ).also { it.bottomMargin = dp(14) }
        }
        listOf(
            "경기" to d.appearances.toString(),
            "득점" to d.goals.toString(),
            "도움" to d.assists.toString(),
            "평점" to (d.avgRating?.let { "%.2f".format(it) } ?: "-"),
            "경고" to d.yellowCards.toString(),
            "퇴장" to d.redCards.toString()
        ).forEach { (label, value) ->
            summaryBox.addView(statCell(label, value))
        }
        c.addView(summaryBox)

        val isGK = d.position?.uppercase() == "G"

        // ── 패스 ──────────────────────────────────────────────
        if (!isGK) {
            c.addView(sectionHeader("패스"))
            c.addView(statsGroup(
                "패스 시도" to d.passesTotal.toString(),
                "패스 정확도" to "%.1f%%".format(d.passesAccuracyAvg),
                "키 패스" to d.passesKey.toString()
            ))
        }

        // ── 슈팅 (필드플레이어) ───────────────────────────────
        if (!isGK) {
            c.addView(sectionHeader("슈팅"))
            c.addView(statsGroup(
                "총 슈팅" to d.shotsTotal.toString(),
                "유효슈팅" to d.shotsOnTarget.toString(),
                "슈팅 정확도" to if (d.shotsTotal > 0) "%.1f%%".format(d.shotsOnTarget.toDouble() / d.shotsTotal * 100) else "-"
            ))
        }

        // ── 수비 ──────────────────────────────────────────────
        if (!isGK) {
            c.addView(sectionHeader("수비"))
            c.addView(statsGroup(
                "태클" to d.tacklesTotal.toString(),
                "블록" to d.tacklesBlocks.toString(),
                "인터셉트" to d.tacklesInterceptions.toString(),
                "듀얼 승리" to "${d.duelsWon}/${d.duelsTotal}"
            ))
        }

        // ── 파울 ──────────────────────────────────────────────
        if (!isGK) {
            c.addView(sectionHeader("파울"))
            c.addView(statsGroup(
                "얻은 파울" to d.foulsDrawn.toString(),
                "범한 파울" to d.foulsCommitted.toString()
            ))
        }

        // ── 골키퍼 ────────────────────────────────────────────
        if (isGK) {
            c.addView(sectionHeader("골키퍼"))
            c.addView(statsGroup(
                "세이브" to d.goalkeeperSaves.toString(),
                "실점" to d.goalkeeperConceded.toString(),
                "박스 내 세이브" to d.goalkeeperSavesInsideBox.toString()
            ))
        }

        // 데이터 출처 안내
        if (d.matchesWithData == 0) {
            c.addView(TextView(requireContext()).apply {
                text = "* 경기 상세를 조회한 경기가 없어 상세 스탯 집계 불가"
                textSize = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            c.addView(TextView(requireContext()).apply {
                text = "* 상세 스탯: ${d.matchesWithData}경기 기준 집계"
                textSize = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun sectionHeader(title: String) = TextView(requireContext()).apply {
        text = title
        textSize = 14f
        setTextColor(Color.parseColor("#6B7280"))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun statCell(label: String, value: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addView(TextView(requireContext()).apply {
                text = value
                textSize = 18f
                setTextColor(Color.parseColor("#1A1A1A"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(requireContext()).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun statsGroup(vararg pairs: Pair<String, String>): LinearLayout {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F5F7"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(14) }
        }
        pairs.forEachIndexed { idx, (label, value) ->
            if (idx > 0) box.addView(View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#E6E8EB"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(dp(14), 0, dp(14), 0) }
            })
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            row.addView(TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#4B5563"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = value
                textSize = 15f
                setTextColor(Color.parseColor("#1A1A1A"))
                setTypeface(null, Typeface.BOLD)
            })
            box.addView(row)
        }
        return box
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}