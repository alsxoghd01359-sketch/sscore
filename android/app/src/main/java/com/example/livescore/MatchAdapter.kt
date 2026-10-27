package com.example.livescore

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide

class MatchAdapter(private var currentList: List<MatchData>) : RecyclerView.Adapter<MatchAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLeague: TextView = v.findViewById(R.id.tvLeague)
        val tvFullDate: TextView = v.findViewById(R.id.tvFullDate)
        val tvTime: TextView = v.findViewById(R.id.tvTime)

        val tvHomeTeam: TextView = v.findViewById(R.id.tvHomeTeam)
        val ivHomeLogo: ImageView = v.findViewById(R.id.ivHomeLogo)
        val tvScore: TextView = v.findViewById(R.id.tvScore)
        val ivAwayLogo: ImageView = v.findViewById(R.id.ivAwayLogo)
        val tvAwayTeam: TextView = v.findViewById(R.id.tvAwayTeam)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = currentList.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = currentList[position]

        val leagueName = when(m.leagueId) {
            39 -> "Premier League"
            140 -> "La Liga"
            135 -> "Serie A"
            78 -> "Bundesliga"
            61 -> "Ligue 1"
            else -> "유럽 리그"
        }
        holder.tvLeague.text = leagueName

        // 날짜 포맷: "yyyy-MM-dd" → "yyyy/MM/dd"
        holder.tvFullDate.text = m.matchDate
            ?.replace("-", "/")
            ?: ""

        holder.tvTime.text = m.matchTime ?: "시간 미정"
        holder.tvHomeTeam.text = m.homeTeam ?: "Unknown"
        holder.tvScore.text = m.score ?: "vs"
        holder.tvAwayTeam.text = m.awayTeam ?: "Unknown"

        val homeId = m.homeTeamId ?: 0
        val awayId = m.awayTeamId ?: 0
        val homeLogoUrl = "https://media.api-sports.io/football/teams/$homeId.png"
        val awayLogoUrl = "https://media.api-sports.io/football/teams/$awayId.png"

        Glide.with(holder.itemView.context)
            .load(homeLogoUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(holder.ivHomeLogo)

        Glide.with(holder.itemView.context)
            .load(awayLogoUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(holder.ivAwayLogo)

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, MatchDetailActivity::class.java).apply {
                putExtra("fixtureId", m.fixtureId)
                putExtra("homeTeam", m.homeTeam)
                putExtra("awayTeam", m.awayTeam)
                putExtra("homeTeamId", m.homeTeamId)
                putExtra("awayTeamId", m.awayTeamId)
                putExtra("matchTime", m.matchTime)
                putExtra("matchDate", m.matchDate)
                putExtra("score", m.score) // 🌟 여기서 점수를 상세 화면으로 넘겨줌!
                putExtra("leagueId", m.leagueId)
                putExtra("season", m.season)
                putExtra("stadium", m.stadium)
                putExtra("matchRound", m.matchRound)
            }
            context.startActivity(intent)
        }
    }

    fun updateData(newList: List<MatchData>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = currentList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                currentList[oldPos].fixtureId == newList[newPos].fixtureId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                currentList[oldPos] == newList[newPos]
        })
        currentList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}