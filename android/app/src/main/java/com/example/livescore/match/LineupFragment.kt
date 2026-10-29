package com.example.livescore.match

import com.example.livescore.*

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.databinding.FragmentLineupBinding
import com.example.livescore.common.StateHost
import com.example.livescore.common.UiState
import kotlin.math.max
import com.example.livescore.player.PlayerMatchStatsDialog

class LineupFragment : Fragment() {

    private var _binding: FragmentLineupBinding? = null
    private val binding get() = _binding!!

    private lateinit var host: StateHost
    private var ratings: Map<Long, Double> = emptyMap()

    private val substOutMap = mutableMapOf<String, SubstInfo>()
    private val substInMap  = mutableMapOf<String, SubstInfo>()

    private var fixtureId = -1L

    private data class SubstInfo(val minute: Int, val extraTime: Int?, val partnerName: String)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding  = FragmentLineupBinding.inflate(inflater, container, false)
        fixtureId = arguments?.getLong("fixtureId") ?: -1L
        host = StateHost(requireContext(), binding.root)
        return host.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = matchDetailViewModel()
        host.onRetry { vm.lineups.retry() }
        // 라인업이 화면의 주인공이고, 경기 정보(교체·평점)는 도착하는 대로 덧붙인다 (정보가 실패해도 라인업은 보인다)
        vm.lineups.state.observe(viewLifecycleOwner) { render(vm) }
        vm.info.state.observe(viewLifecycleOwner) { render(vm) }
        vm.lineups.ensureLoaded()
        vm.info.ensureLoaded()
    }

    private fun render(vm: MatchDetailViewModel) {
        val info = (vm.info.state.value as? UiState.Success)?.data
        host.render(vm.lineups.state.value ?: UiState.Loading) { lineups ->
            if (lineups.size < 2) {
                binding.tvTargetTeamName.text = "라인업이 아직 발표되지 않았습니다"
                binding.tvFormation.text = ""
                binding.pitchContainer.removeAllViews()
                binding.substitutesContainer.removeAllViews()
                return@render
            }
            ratings = info?.playerRatings ?: emptyMap()
            buildSubstMaps(info?.events ?: emptyList())
            renderFullMatchLineup(lineups[0], lineups[1])
        }
    }

    // ── 교체 맵 빌드 ─────────────────────────────────────────
    private fun buildSubstMaps(events: List<MatchEventData>) {
        substOutMap.clear(); substInMap.clear()
        events.forEach { e ->
            if (e.type == "subst") {
                val out = e.playerName        ?: return@forEach
                val inn = e.assistPlayerName  ?: return@forEach
                val min = e.time              ?: return@forEach
                substOutMap[out] = SubstInfo(min, e.extraTime, inn)
                substInMap[inn]  = SubstInfo(min, e.extraTime, out)
            }
        }
    }

    // ── 전체 라인업 렌더링 ────────────────────────────────────
    private fun renderFullMatchLineup(home: LineupData, away: LineupData) {
        binding.tvTargetTeamName.text = "${home.teamName} vs ${away.teamName}"
        binding.tvFormation.text =
            "포메이션: ${home.formation ?: "-"} (홈) / ${away.formation ?: "-"} (원정)"

        binding.pitchContainer.removeAllViews()
        drawPitchLineup(home, isHome = true)
        drawPitchLineup(away, isHome = false)

        binding.tvHomeCoach.text = home.coachName ?: "감독 미정"
        Glide.with(this).load(home.coachPhotoUrl)
            .transform(CircleCrop()).placeholder(R.drawable.bg_player_circle)
            .into(binding.ivHomeCoachPhoto)
        binding.tvAwayCoach.text = away.coachName ?: "감독 미정"
        Glide.with(this).load(away.coachPhotoUrl)
            .transform(CircleCrop()).placeholder(R.drawable.bg_player_circle)
            .into(binding.ivAwayCoachPhoto)

        renderSubstitutes(home.substitutes, away.substitutes)
    }

    // ── 피치 선수 배치 ────────────────────────────────────────
    private fun drawPitchLineup(lineup: LineupData, isHome: Boolean) {
        val pitch = binding.pitchContainer

        val rowCountMap = mutableMapOf<Int, Int>()
        lineup.startXI.forEach { p ->
            val row = p.grid?.substringBefore(":")?.toIntOrNull() ?: return@forEach
            rowCountMap[row] = (rowCountMap[row] ?: 0) + 1
        }
        val totalRows = rowCountMap.keys.maxOrNull() ?: 4

        lineup.startXI.forEach { player ->
            val grid  = player.grid ?: return@forEach
            val parts = grid.split(":")
            val row   = parts.getOrNull(0)?.toFloatOrNull() ?: return@forEach
            val col   = parts.getOrNull(1)?.toFloatOrNull() ?: return@forEach

            val playerView = layoutInflater.inflate(R.layout.item_player_pitch, pitch, false)
            playerView.id  = View.generateViewId()

            val ivPhoto     = playerView.findViewById<ImageView>(R.id.ivPlayerPhoto)
            val tvName      = playerView.findViewById<TextView>(R.id.tvPlayerName)
            val tvSubstInfo = playerView.findViewById<TextView>(R.id.tvSubstInfo)
            val tvRating    = playerView.findViewById<TextView>(R.id.tvPlayerRating)

            player.id?.let { ratings[it.toLong()] }?.let { rating ->
                tvRating.text = String.format("%.1f", rating)
                tvRating.backgroundTintList = android.content.res.ColorStateList.valueOf(ratingColor(rating))
                tvRating.visibility = View.VISIBLE
            }

            tvName.text = "${player.number ?: "-"} - ${player.name}"

            player.id?.let { pid ->
                Glide.with(this)
                    .load("https://media.api-sports.io/football/players/$pid.png")
                    .placeholder(R.drawable.bg_player_circle)
                    .transform(FaceCropTransformation(), CircleCrop())
                    .into(ivPhoto)

                // 선수 사진 클릭 → 경기 스탯 다이얼로그
                ivPhoto.setOnClickListener {
                    if (fixtureId != -1L) {
                        PlayerMatchStatsDialog.newInstance(pid, fixtureId)
                            .show(parentFragmentManager, "player_stats")
                    }
                }
            }

            // 교체 OUT 어노테이션
            substOutMap[player.name]?.let { info ->
                val t = if (info.extraTime != null) "${info.minute}+${info.extraTime}'" else "${info.minute}'"
                tvSubstInfo.text = "⬇ $t ${info.partnerName}"
                tvSubstInfo.visibility = View.VISIBLE
            }

            pitch.addView(playerView)

            // 포지션 배치
            val vBias = if (isHome)
                0.00f + (row - 1f) * (0.44f / max(1f, totalRows - 1f))
            else
                1f   - (row - 1f) * (0.44f / max(1f, totalRows - 1f))

            val maxColInRow = rowCountMap[row.toInt()]?.toFloat() ?: 1f
            val gap         = 0.25f
            val startBias   = 0.5f - ((maxColInRow - 1f) * gap / 2f)
            val baseHBias   = startBias + ((maxColInRow - col) * gap)
            val hBias       = if (isHome) baseHBias else (1f - baseHBias)

            ConstraintSet().apply {
                clone(pitch)
                connect(playerView.id, ConstraintSet.TOP,    pitch.id, ConstraintSet.TOP)
                connect(playerView.id, ConstraintSet.BOTTOM, pitch.id, ConstraintSet.BOTTOM)
                connect(playerView.id, ConstraintSet.START,  pitch.id, ConstraintSet.START)
                connect(playerView.id, ConstraintSet.END,    pitch.id, ConstraintSet.END)
                setHorizontalBias(playerView.id, hBias)
                setVerticalBias(playerView.id, vBias)
                applyTo(pitch)
            }
        }
    }

    private fun nameWithRating(p: PlayerData): String {
        val rating = p.id?.let { ratings[it.toLong()] } ?: return p.name
        return "${p.name}  ★" + String.format("%.1f", rating)
    }

    // 7점 이상 초록, 6점 미만 빨강, 그 사이는 강조색
    private fun ratingColor(rating: Double): Int = when {
        rating >= 7.0 -> android.graphics.Color.parseColor("#1FA971")
        rating < 6.0 -> android.graphics.Color.parseColor("#D64545")
        else -> android.graphics.Color.parseColor("#F2711C")
    }

    // ── 교체 명단 ─────────────────────────────────────────────
    private fun renderSubstitutes(homeSubs: List<PlayerData>, awaySubs: List<PlayerData>) {
        val subContainer = binding.substitutesContainer
        subContainer.removeAllViews()

        for (i in 0 until max(homeSubs.size, awaySubs.size)) {
            val rowView = layoutInflater.inflate(
                R.layout.item_substitute_row, subContainer, false)

            val layoutHome   = rowView.findViewById<View>(R.id.layoutHomeSub)
            val ivHomePhoto  = rowView.findViewById<ImageView>(R.id.ivHomeSubPhoto)
            val tvHomeNumber = rowView.findViewById<TextView>(R.id.tvHomeSubNumber)
            val tvHomePlayer = rowView.findViewById<TextView>(R.id.tvHomeSubPlayer)
            val tvHomeInfo   = rowView.findViewById<TextView>(R.id.tvHomeSubInfo)

            val layoutAway   = rowView.findViewById<View>(R.id.layoutAwaySub)
            val ivAwayPhoto  = rowView.findViewById<ImageView>(R.id.ivAwaySubPhoto)
            val tvAwayNumber = rowView.findViewById<TextView>(R.id.tvAwaySubNumber)
            val tvAwayPlayer = rowView.findViewById<TextView>(R.id.tvAwaySubPlayer)
            val tvAwayInfo   = rowView.findViewById<TextView>(R.id.tvAwaySubInfo)

            if (i < homeSubs.size) {
                val p = homeSubs[i]
                layoutHome.visibility = View.VISIBLE
                tvHomeNumber.text = (p.number ?: "").toString()
                tvHomePlayer.text = nameWithRating(p)
                p.id?.let { pid ->
                    Glide.with(this)
                        .load("https://media.api-sports.io/football/players/$pid.png")
                        .placeholder(R.drawable.bg_player_circle)
                        .transform(CircleCrop()).into(ivHomePhoto)
                    // 클릭
                    ivHomePhoto.setOnClickListener {
                        if (fixtureId != -1L)
                            PlayerMatchStatsDialog.newInstance(pid, fixtureId)
                                .show(parentFragmentManager, "player_stats")
                    }
                }
                substInMap[p.name]?.let { info ->
                    val t = if (info.extraTime != null) "${info.minute}+${info.extraTime}'" else "${info.minute}'"
                    tvHomeInfo.text = "⬆ $t ${info.partnerName}"
                    tvHomeInfo.visibility = View.VISIBLE
                }
            }

            if (i < awaySubs.size) {
                val p = awaySubs[i]
                layoutAway.visibility = View.VISIBLE
                tvAwayNumber.text = (p.number ?: "").toString()
                tvAwayPlayer.text = nameWithRating(p)
                p.id?.let { pid ->
                    Glide.with(this)
                        .load("https://media.api-sports.io/football/players/$pid.png")
                        .placeholder(R.drawable.bg_player_circle)
                        .transform(CircleCrop()).into(ivAwayPhoto)
                    // 클릭
                    ivAwayPhoto.setOnClickListener {
                        if (fixtureId != -1L)
                            PlayerMatchStatsDialog.newInstance(pid, fixtureId)
                                .show(parentFragmentManager, "player_stats")
                    }
                }
                substInMap[p.name]?.let { info ->
                    val t = if (info.extraTime != null) "${info.minute}+${info.extraTime}'" else "${info.minute}'"
                    tvAwayInfo.text = "⬆ $t ${info.partnerName}"
                    tvAwayInfo.visibility = View.VISIBLE
                }
            }

            subContainer.addView(rowView)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        fun newInstance(fixtureId: Long) = LineupFragment().apply {
            arguments = Bundle().apply { putLong("fixtureId", fixtureId) }
        }
    }
}