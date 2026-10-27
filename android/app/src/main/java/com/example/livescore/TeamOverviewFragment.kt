package com.example.livescore

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.example.livescore.databinding.FragmentTeamOverviewBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamOverviewFragment : Fragment() {

    private var _binding: FragmentTeamOverviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(teamId: Int, teamName: String, leagueId: Int, season: Int) =
            TeamOverviewFragment().apply {
                arguments = Bundle().apply {
                    putInt("teamId", teamId)
                    putString("teamName", teamName)
                    putInt("leagueId", leagueId)
                    putInt("season", season)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamOverviewBinding.inflate(inflater, container, false)
        val teamId   = arguments?.getInt("teamId")   ?: return binding.root
        val leagueId = arguments?.getInt("leagueId") ?: return binding.root
        val season   = arguments?.getInt("season")   ?: return binding.root
        loadOverview(teamId, leagueId, season)
        return binding.root
    }

    private fun loadOverview(teamId: Int, leagueId: Int, season: Int) {
        RetrofitClient.apiService.getTeamOverview(teamId, leagueId, season)
            .enqueue(object : Callback<TeamOverviewData> {
                override fun onResponse(call: Call<TeamOverviewData>, response: Response<TeamOverviewData>) {
                    if (!isAdded || _binding == null) return
                    val data = response.body() ?: return
                    renderRecentMatches(data.recentMatches, teamId)
                    renderMiniStandings(data.miniStandings, teamId)
                    renderVenue(data.venue)
                    renderTrophies(data.leagueTrophies)
                }
                override fun onFailure(call: Call<TeamOverviewData>, t: Throwable) {}
            })
    }

    // ─── ① 최근 5경기 ──────────────────────────────────────────────
    private fun renderRecentMatches(matches: List<RecentMatchData>, teamId: Int) {
        val container = binding.layoutRecentMatches
        container.removeAllViews()

        matches.forEach { match ->
            val opponentId = if (match.homeTeamId == teamId) match.awayTeamId else match.homeTeamId
            val item = LayoutInflater.from(context).inflate(R.layout.item_recent_match, container, false)

            val tvResult = item.findViewById<TextView>(R.id.tvResultBox)
            val ivLogo   = item.findViewById<ImageView>(R.id.ivOpponentLogo)

            val bgColor = when (match.result) {
                "W"  -> Color.parseColor("#1A7A45")
                "D"  -> Color.parseColor("#555555")
                else -> Color.parseColor("#8B1A1A")
            }
            tvResult.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                cornerRadius = 8f
            }
            tvResult.text = match.score ?: "-"

            Glide.with(this)
                .load("https://media.api-sports.io/football/teams/${opponentId ?: 0}.png")
                .placeholder(R.mipmap.ic_launcher)
                .into(ivLogo)

            container.addView(item)
        }
    }

    // ─── ② 미니 순위표 (±1) ────────────────────────────────────────
    private fun renderMiniStandings(standings: List<StandingData>, focusTeamId: Int) {
        val container = binding.layoutMiniStandings
        container.removeAllViews()

        standings.forEach { row ->
            val isMe = row.teamId == focusTeamId
            val textColor = if (isMe) Color.parseColor("#20E07A") else Color.WHITE

            val rowView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(12), dp(11), dp(12), dp(11))
                if (isMe) setBackgroundColor(Color.parseColor("#1A2E1A"))
            }
            rowView.addView(txt(row.rank.toString(),  32, textColor))
            rowView.addView(txt(row.teamName,          0,  textColor, weight = 1f))
            rowView.addView(txt(row.played.toString(), 36, Color.parseColor("#CCCCCC"), Gravity.CENTER))
            rowView.addView(txt(row.points.toString(), 36, textColor, Gravity.CENTER))
            container.addView(rowView)
        }
    }

    // ─── ③ 경기장 ──────────────────────────────────────────────────
    private fun renderVenue(venue: VenueData?) {
        if (venue == null) { binding.layoutVenue.visibility = View.GONE; return }
        binding.tvVenueName.text     = venue.name ?: "경기장 정보 없음"
        binding.tvVenueCity.text     = listOfNotNull(venue.city, venue.address).joinToString(" · ").ifEmpty { "-" }
        binding.tvVenueSurface.text  = surfaceLabel(venue.surface)
        binding.tvVenueCapacity.text = venue.capacity?.let { "%,d명".format(it) } ?: "-"
    }

    private fun surfaceLabel(s: String?) = when (s?.lowercase()) {
        "grass"                         -> "천연 잔디 (Grass)"
        "artificial grass", "astroturf" -> "인조 잔디 (Artificial)"
        "hybrid"                        -> "하이브리드 잔디 (Hybrid)"
        else                            -> s ?: "-"
    }

    // ─── ④ 리그 트로피 ─────────────────────────────────────────────
    private fun renderTrophies(trophies: List<TrophyData>) {
        val container = binding.layoutTrophies
        container.removeAllViews()

        if (trophies.isEmpty()) {
            container.addView(txt("우승 이력 없음", 0, Color.parseColor("#888888"),
                Gravity.CENTER, padding = dp(14)))
            return
        }

        trophies.forEachIndexed { idx, trophy ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                gravity = Gravity.CENTER_VERTICAL
            }

            // 트로피 아이콘
            row.addView(txt("🏆", 28, Color.WHITE, Gravity.CENTER_VERTICAL, textSize = 18f))

            // 리그명 + 국가 (세로)
            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8), 0, 0, 0)
            }
            infoCol.addView(txt(trophy.league ?: "-", 0, Color.WHITE, textSize = 14f))
            if (!trophy.country.isNullOrBlank()) {
                infoCol.addView(txt(trophy.country, 0, Color.parseColor("#888888"), textSize = 11f))
            }
            row.addView(infoCol)

            // 우승 횟수
            row.addView(txt("×${trophy.winCount}", 36, Color.parseColor("#20E07A"),
                Gravity.CENTER, textSize = 16f, bold = true))

            // 마지막 우승 시즌 (세로)
            val seasonCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.END
            }
            seasonCol.addView(txt("마지막", 0, Color.parseColor("#888888"), Gravity.END, textSize = 10f))
            seasonCol.addView(txt(trophy.lastWinSeason ?: "-", 0, Color.WHITE, Gravity.END, textSize = 12f))
            row.addView(seasonCol)

            container.addView(row)

            // 구분선
            if (idx < trophies.lastIndex) {
                container.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(dp(14), 0, dp(14), 0) }
                    setBackgroundColor(Color.parseColor("#333333"))
                })
            }
        }
    }

    // ─── 텍스트뷰 생성 헬퍼 ────────────────────────────────────────
    private fun txt(
        text: String, widthDp: Int, color: Int,
        gravity: Int = Gravity.START, weight: Float = 0f,
        textSize: Float = 13f, bold: Boolean = false, padding: Int = 0
    ) = TextView(requireContext()).apply {
        this.text      = text
        setTextColor(color)
        this.textSize  = textSize
        this.gravity   = gravity
        if (bold) setTypeface(null, Typeface.BOLD)
        if (padding > 0) setPadding(padding, padding, padding, padding)
        layoutParams   = LinearLayout.LayoutParams(
            if (widthDp > 0) dp(widthDp) else LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}