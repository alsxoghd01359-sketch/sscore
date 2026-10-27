package com.example.livescore

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.livescore.databinding.FragmentTeamTrophiesBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamTrophiesFragment : Fragment() {

    private var _binding: FragmentTeamTrophiesBinding? = null
    private val binding get() = _binding!!
    private var teamId = 0

    companion object {
        fun newInstance(teamId: Int, teamName: String) =
            TeamTrophiesFragment().apply {
                arguments = Bundle().apply {
                    putInt("teamId", teamId)
                    putString("teamName", teamName)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamTrophiesBinding.inflate(inflater, container, false)
        teamId = arguments?.getInt("teamId") ?: return binding.root
        loadTrophies()
        return binding.root
    }

    private fun loadTrophies() {
        binding.pbTrophyLoading.visibility = View.VISIBLE
        RetrofitClient.apiService.getTeamTrophies(teamId)
            .enqueue(object : Callback<List<TrophyData>> {
                override fun onResponse(call: Call<List<TrophyData>>, response: Response<List<TrophyData>>) {
                    if (!isAdded || _binding == null) return
                    binding.pbTrophyLoading.visibility = View.GONE
                    val trophies = response.body() ?: return
                    renderTrophies(trophies)
                }
                override fun onFailure(call: Call<List<TrophyData>>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbTrophyLoading.visibility = View.GONE
                }
            })
    }

    private fun renderTrophies(trophies: List<TrophyData>) {
        val container = binding.layoutTrophies
        container.removeAllViews()

        if (trophies.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "우승 기록이 없습니다"
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }

        trophies.forEach { trophy ->
            // 카드 박스
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1C1C1C"))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10) }
            }

            // 🏆 이모지
            card.addView(TextView(requireContext()).apply {
                text = "🏆"
                textSize = 28f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(14) }
            })

            // 리그명 + 마지막 우승
            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(requireContext()).apply {
                text = trophy.league ?: "리그"
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            })
            infoCol.addView(TextView(requireContext()).apply {
                text = "최근 우승: ${trophy.lastWinSeason ?: "-"}"
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
            })
            card.addView(infoCol)

            // 우승 횟수
            card.addView(TextView(requireContext()).apply {
                text = "${trophy.winCount}회"
                textSize = 18f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            })

            container.addView(card)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}