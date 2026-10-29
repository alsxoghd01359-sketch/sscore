package com.example.livescore.match

import com.example.livescore.*

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.common.StateHost
import com.example.livescore.player.PlayerMatchStatsDialog

class StatisticsFragment : Fragment() {
    private lateinit var containerStats: LinearLayout
    private lateinit var tvPossessionHome: TextView
    private lateinit var tvPossessionAway: TextView
    private lateinit var viewPossessionHome: View
    private lateinit var viewPossessionAway: View
    private lateinit var layoutTopPlayers: LinearLayout
    private lateinit var containerTopHome: LinearLayout
    private lateinit var containerTopAway: LinearLayout

    private lateinit var host: StateHost
    private var fixtureId = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_statistics, container, false)
        containerStats      = view.findViewById(R.id.containerStats)
        tvPossessionHome    = view.findViewById(R.id.tvPossessionHome)
        tvPossessionAway    = view.findViewById(R.id.tvPossessionAway)
        viewPossessionHome  = view.findViewById(R.id.viewPossessionHome)
        viewPossessionAway  = view.findViewById(R.id.viewPossessionAway)
        layoutTopPlayers    = view.findViewById(R.id.layoutTopPlayers)
        containerTopHome    = view.findViewById(R.id.containerTopHome)
        containerTopAway    = view.findViewById(R.id.containerTopAway)

        fixtureId = arguments?.getLong("fixtureId") ?: -1L
        val homeTeamName = arguments?.getString("homeTeamName") ?: ""
        val awayTeamName = arguments?.getString("awayTeamName") ?: ""

        viewPossessionHome.setBackgroundColor(Color.parseColor(getTeamColor(homeTeamName, true)))
        viewPossessionAway.setBackgroundColor(Color.parseColor(getTeamColor(awayTeamName, false)))

        host = StateHost(requireContext(), view)
        return host.root
    }

    private fun getTeamColor(teamName: String, isHome: Boolean): String {
        val name = teamName.lowercase()
        return when {
            name.contains("liverpool")                          -> "#E31B23"
            name.contains("chelsea")                           -> "#034694"
            name.contains("city") || name.contains("manchester city") -> "#6CABDD"
            name.contains("united") || name.contains("manchester utd") -> "#DA291C"
            name.contains("arsenal")                           -> "#EF0107"
            name.contains("tottenham") || name.contains("spurs") -> "#132257"
            name.contains("real madrid")                       -> "#1A1A1A"
            name.contains("barcelona")                         -> "#004D98"
            name.contains("bayern") || name.contains("munich") -> "#DC052D"
            name.contains("paris") || name.contains("psg")    -> "#004170"
            else -> if (isHome) "#F2711C" else "#2563EB"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = matchDetailViewModel()
        host.onRetry { vm.info.retry() }
        vm.info.state.observe(viewLifecycleOwner) { state ->
            host.render(state) { matchInfo ->
                renderStats(matchInfo.statistics)
                if (matchInfo.homeTopPlayers.isNotEmpty() || matchInfo.awayTopPlayers.isNotEmpty()) {
                    renderTopPlayers(matchInfo.homeTopPlayers, matchInfo.awayTopPlayers)
                }
            }
        }
        vm.info.ensureLoaded()
    }

    private fun renderStats(stats: List<MatchStatItemData>) {
        containerStats.removeAllViews()

        val possessionStat = stats.find { it.name == "점유율" }
        if (possessionStat != null) {
            val homePos = possessionStat.homeValue.replace("%", "").toIntOrNull() ?: 50
            val awayPos = possessionStat.awayValue.replace("%", "").toIntOrNull() ?: 50
            tvPossessionHome.text = "$homePos%"
            tvPossessionAway.text = "$awayPos%"
            (viewPossessionHome.layoutParams as LinearLayout.LayoutParams).weight = homePos.toFloat()
            viewPossessionHome.layoutParams = viewPossessionHome.layoutParams
            (viewPossessionAway.layoutParams as LinearLayout.LayoutParams).weight = awayPos.toFloat()
            viewPossessionAway.layoutParams = viewPossessionAway.layoutParams
        }

        var currentCategory = ""
        for (stat in stats) {
            if (stat.name == "점유율") continue
            if (stat.category != currentCategory) {
                currentCategory = stat.category
                val headerView = layoutInflater.inflate(
                    R.layout.item_stat_header, containerStats, false) as TextView
                headerView.text = when (currentCategory) {
                    "OVERALL" -> "공격 및 전체"
                    "PASSES"  -> "패스 세부 지표"
                    "DEFENSE" -> "수비 세부 지표"
                    "DUELS"   -> "볼 경합 및 드리블"
                    else      -> currentCategory
                }
                containerStats.addView(headerView)
            }
            val rowView = layoutInflater.inflate(R.layout.item_stat_row, containerStats, false)
            rowView.findViewById<TextView>(R.id.tvHomeStat).text  = stat.homeValue
            rowView.findViewById<TextView>(R.id.tvStatTitle).text = stat.name
            rowView.findViewById<TextView>(R.id.tvAwayStat).text  = stat.awayValue
            containerStats.addView(rowView)
        }
    }

    private fun renderTopPlayers(
        homePlayers: List<MomPlayerData>,
        awayPlayers: List<MomPlayerData>
    ) {
        layoutTopPlayers.visibility = View.VISIBLE
        containerTopHome.removeAllViews()
        containerTopAway.removeAllViews()

        fun bindPlayer(player: MomPlayerData, parent: LinearLayout) {
            val rowView = layoutInflater.inflate(R.layout.item_top_player_row, parent, false)
            rowView.findViewById<TextView>(R.id.tvTopPlayerName).text   = player.name
            rowView.findViewById<TextView>(R.id.tvTopPlayerRating).text =
                String.format("%.1f", player.rating)

            val ivPhoto = rowView.findViewById<ImageView>(R.id.ivTopPlayerPhoto)
            Glide.with(this).load(player.photoUrl)
                .transform(FaceCropTransformation(), CircleCrop())
                .placeholder(R.drawable.bg_player_circle)
                .into(ivPhoto)

            // 선수 사진 클릭 → 경기 스탯 다이얼로그 오픈
            // photoUrl에서 playerId 추출: .../players/{id}.png
            val playerId = player.photoUrl
                ?.substringAfterLast("/")
                ?.substringBefore(".")
                ?.toLongOrNull()

            if (playerId != null && fixtureId != -1L) {
                ivPhoto.setOnClickListener {
                    PlayerMatchStatsDialog.newInstance(playerId, fixtureId)
                        .show(parentFragmentManager, "player_stats")
                }
            }
            parent.addView(rowView)
        }

        homePlayers.forEach { bindPlayer(it, containerTopHome) }
        awayPlayers.forEach { bindPlayer(it, containerTopAway) }
    }

    companion object {
        fun newInstance(fixtureId: Long, homeTeamName: String, awayTeamName: String) =
            StatisticsFragment().apply {
                arguments = Bundle().apply {
                    putLong("fixtureId", fixtureId)
                    putString("homeTeamName", homeTeamName)
                    putString("awayTeamName", awayTeamName)
                }
            }
    }
}