package com.example.livescore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StandingsAdapter(
    private val list: List<StandingData>,
    private val currentHomeTeamId: Int,
    private val currentAwayTeamId: Int,
    private val onTeamClick: ((StandingData) -> Unit)? = null
) : RecyclerView.Adapter<StandingsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvRank:     TextView  = v.findViewById(R.id.tvRank)
        val ivLogo:     ImageView = v.findViewById(R.id.ivTeamLogo)
        val tvTeam:     TextView  = v.findViewById(R.id.tvTeam)
        val tvPlayed:   TextView  = v.findViewById(R.id.tvPlayed)
        val tvWin:      TextView  = v.findViewById(R.id.tvWin)
        val tvDraw:     TextView  = v.findViewById(R.id.tvDraw)
        val tvLose:     TextView  = v.findViewById(R.id.tvLose)
        val tvGoals:    TextView  = v.findViewById(R.id.tvGoals)
        val tvGoalDiff: TextView  = v.findViewById(R.id.tvGoalDiff)
        val tvPoints:   TextView  = v.findViewById(R.id.tvPoints)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_standing, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = list[position]
        val isHighlight = s.teamId == currentHomeTeamId || s.teamId == currentAwayTeamId

        // 기본 텍스트 색
        val nameColor = if (isHighlight) Color.parseColor("#20E07A") else Color.WHITE
        val rowBg     = if (isHighlight) Color.parseColor("#1A2A1A") else Color.TRANSPARENT
        holder.itemView.setBackgroundColor(rowBg)

        holder.tvRank.text   = s.rank.toString()
        holder.tvTeam.text   = s.teamName
        holder.tvTeam.setTextColor(nameColor)
        holder.tvPlayed.text = s.played.toString()
        holder.tvWin.text    = s.win.toString()
        holder.tvDraw.text   = s.draw.toString()
        holder.tvLose.text   = s.lose.toString()

        // 득점-실점 (+/-)
        holder.tvGoals.text = "${s.goalsFor}-${s.goalsAgainst}"

        // 득실차 (=) - 부호 표시 + 색상
        val diff = s.goalsDiff
        holder.tvGoalDiff.text = when {
            diff > 0 -> "+$diff"
            else     -> diff.toString()
        }
        holder.tvGoalDiff.setTextColor(when {
            diff > 0 -> Color.parseColor("#20E07A")
            diff < 0 -> Color.parseColor("#FF5555")
            else     -> Color.WHITE
        })

        holder.tvPoints.text = s.points.toString()
        holder.tvPoints.setTextColor(nameColor)

        // 팀 행 클릭 → 구단 상세 페이지
        holder.itemView.setOnClickListener {
            onTeamClick?.invoke(s)
        }

        // 팀 로고
        Glide.with(holder.itemView.context)
            .load("https://media.api-sports.io/football/teams/${s.teamId}.png")
            .placeholder(R.mipmap.ic_launcher)
            .into(holder.ivLogo)
    }

    override fun getItemCount(): Int = list.size
}