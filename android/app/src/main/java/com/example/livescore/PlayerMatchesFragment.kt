package com.example.livescore

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentPlayerMatchesBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlayerMatchesFragment : Fragment() {

    private var _binding: FragmentPlayerMatchesBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(playerId: Long) = PlayerMatchesFragment().apply {
            arguments = Bundle().apply { putLong("playerId", playerId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerMatchesBinding.inflate(inflater, container, false)
        val playerId = arguments?.getLong("playerId") ?: return binding.root
        load(playerId)
        return binding.root
    }

    private fun load(playerId: Long) {
        binding.pbLoading.visibility = View.VISIBLE
        RetrofitClient.apiService.getPlayerMatches(playerId)
            .enqueue(object : Callback<List<PlayerMatchRecordData>> {
                override fun onResponse(
                    call: Call<List<PlayerMatchRecordData>>,
                    response: Response<List<PlayerMatchRecordData>>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val list = response.body() ?: return
                    list.forEach { renderMatch(it) }
                }
                override fun onFailure(call: Call<List<PlayerMatchRecordData>>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    private fun renderMatch(m: PlayerMatchRecordData) {
        val container = binding.layoutMatches

        // ── 날짜 헤더 ────────────────────────────────────
        container.addView(TextView(requireContext()).apply {
            text = formatDate(m.matchDate)
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(4), dp(12), dp(4), dp(6))
        })

        // ── 경기 카드 ─────────────────────────────────────
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(2) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(requireContext(), MatchDetailActivity::class.java).apply {
                    putExtra("fixtureId",  m.fixtureId)
                    putExtra("homeTeam",   m.homeTeam)
                    putExtra("awayTeam",   m.awayTeam)
                    putExtra("homeTeamId", m.homeTeamId ?: 0)
                    putExtra("awayTeamId", m.awayTeamId ?: 0)
                    putExtra("matchTime",  m.matchTime)
                    putExtra("matchDate",  m.matchDate)
                    putExtra("score",      m.score)
                    putExtra("leagueId",   m.leagueId)
                    putExtra("season",     m.season)
                    putExtra("stadium",    m.stadium)
                    putExtra("matchRound", m.matchRound)
                })
            }
        }

        // 상대팀 로고
        val logo = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).also { it.marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(this).load(m.opponentLogo).into(logo)
        card.addView(logo)

        // 상대팀명 + 홈/원정·스코어
        val infoCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        infoCol.addView(TextView(requireContext()).apply {
            text = m.opponentName
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        infoCol.addView(TextView(requireContext()).apply {
            text = "${if (m.isHome) "홈" else "원정"}  ${m.score}"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(2) }
        })
        card.addView(infoCol)

        // ── 오른쪽 영역 ───────────────────────────────────
        val rightCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val hasStats = m.goals > 0 || m.assists > 0 || m.yellowCards > 0 ||
                m.redCards > 0 || m.minutesPlayed != null || m.rating != null

        if (hasStats) {
            // 이모지 (득점·도움·카드)
            val emojis = buildString {
                repeat(m.goals)        { append("⚽") }
                repeat(m.assists)      { append("👟") }
                repeat(m.yellowCards)  { append("🟨") }
                repeat(m.redCards)     { append("🟥") }
            }
            if (emojis.isNotEmpty()) {
                rightCol.addView(TextView(requireContext()).apply {
                    text = emojis
                    textSize = 15f
                    gravity = Gravity.END
                })
            }

            // 교체출전 + 출전시간
            val statusText = when (m.status) {
                "SUBBED_ON" -> "교체출전"
                "BENCH"     -> "교체명단"
                else        -> ""
            }
            val minuteRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2) }
            }
            if (statusText.isNotEmpty()) {
                minuteRow.addView(TextView(requireContext()).apply {
                    text = statusText
                    textSize = 12f
                    setTextColor(Color.parseColor(if (m.status == "SUBBED_ON") "#20E07A" else "#FF9C33"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = dp(6) }
                })
            }
            if (m.minutesPlayed != null && m.minutesPlayed > 0) {
                minuteRow.addView(TextView(requireContext()).apply {
                    text = "${m.minutesPlayed}'"
                    textSize = 14f
                    setTextColor(Color.parseColor("#AAAAAA"))
                })
            }
            if (minuteRow.childCount > 0) rightCol.addView(minuteRow)

            // 평점
            if (m.rating != null) {
                rightCol.addView(TextView(requireContext()).apply {
                    text = "%.1f".format(m.rating)
                    textSize = 18f
                    setTextColor(Color.parseColor(ratingColor(m.rating)))
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = dp(2) }
                })
            }
        } else {
            // 스탯 없음 → 교체명단 or 명단제외
            val fallback = if (m.status == "BENCH") "교체명단" else "명단제외"
            rightCol.addView(TextView(requireContext()).apply {
                text = fallback
                textSize = 13f
                setTextColor(Color.parseColor("#555555"))
                gravity = Gravity.END
            })
        }

        card.addView(rightCol)
        container.addView(card)
    }

    private fun formatDate(raw: String): String = try {
        val p = raw.split("-")
        "${p[1]}월 ${p[2]}일"
    } catch (e: Exception) { raw }

    private fun ratingColor(r: Double) = when {
        r >= 8.0 -> "#20EFFF"; r >= 7.0 -> "#20E07A"
        r >= 6.0 -> "#FF9C33"; else -> "#FF6363"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}