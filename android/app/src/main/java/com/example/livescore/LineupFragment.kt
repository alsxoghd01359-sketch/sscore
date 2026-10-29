package com.example.livescore

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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.max
import com.example.livescore.player.PlayerMatchStatsDialog

class LineupFragment : Fragment() {

    private var _binding: FragmentLineupBinding? = null
    private val binding get() = _binding!!

    private var lineupData:   List<LineupData>? = null
    private var matchInfoData: MatchInfoData?   = null

    private val substOutMap = mutableMapOf<String, SubstInfo>()
    private val substInMap  = mutableMapOf<String, SubstInfo>()

    private var fixtureId = -1L

    private data class SubstInfo(val minute: Int, val extraTime: Int?, val partnerName: String)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding  = FragmentLineupBinding.inflate(inflater, container, false)
        fixtureId = arguments?.getLong("fixtureId") ?: -1L
        if (fixtureId != -1L) {
            loadLineup(fixtureId)
            loadEvents(fixtureId)
        }
        return binding.root
    }

    // ── 라인업 로드 ───────────────────────────────────────────
    private fun loadLineup(fixtureId: Long) {
        RetrofitClient.apiService.getLineups(fixtureId)
            .enqueue(object : Callback<List<LineupData>> {
                override fun onResponse(
                    call: Call<List<LineupData>>, response: Response<List<LineupData>>
                ) {
                    if (!isAdded || _binding == null) return
                    lineupData = response.body()
                    tryRender()
                }
                override fun onFailure(call: Call<List<LineupData>>, t: Throwable) {}
            })
    }

    // ── 이벤트 로드 (교체 어노테이션용) ──────────────────────
    private fun loadEvents(fixtureId: Long) {
        RetrofitClient.apiService.getMatchInfo(fixtureId)
            .enqueue(object : Callback<MatchInfoData> {
                override fun onResponse(
                    call: Call<MatchInfoData>, response: Response<MatchInfoData>
                ) {
                    if (!isAdded || _binding == null) return
                    matchInfoData = response.body()
                    tryRender()
                }
                override fun onFailure(call: Call<MatchInfoData>, t: Throwable) {
                    matchInfoData = MatchInfoData(
                        fixtureId = fixtureId, momPlayer = null, referee = null,
                        matchFullDate = null, events = emptyList(), statistics = emptyList(),
                        homeTopPlayers = emptyList(), awayTopPlayers = emptyList()
                    )
                    tryRender()
                }
            })
    }

    private fun tryRender() {
        val lineups = lineupData ?: return
        val info    = matchInfoData ?: return
        if (lineups.size < 2) return
        buildSubstMaps(info.events)
        renderFullMatchLineup(lineups[0], lineups[1])
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
                tvHomePlayer.text = p.name
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
                tvAwayPlayer.text = p.name
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