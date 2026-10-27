package com.example.livescore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimelineAdapter(
    private val events: List<MatchEventData>,
    private val homeTeamId: Int
) : RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    class TimelineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventTime:    TextView   = view.findViewById(R.id.tvEventTime)
        val layoutHomeEvent: LinearLayout = view.findViewById(R.id.layoutHomeEvent)
        val tvHomePlayer:   TextView   = view.findViewById(R.id.tvHomePlayer)
        val tvHomeAssist:   TextView   = view.findViewById(R.id.tvHomeAssist)
        val tvHomeIcon:     TextView   = view.findViewById(R.id.tvHomeIcon)
        val ivHomePenMiss:  ImageView  = view.findViewById(R.id.ivHomePenMiss)
        val layoutAwayEvent: LinearLayout = view.findViewById(R.id.layoutAwayEvent)
        val tvAwayPlayer:   TextView   = view.findViewById(R.id.tvAwayPlayer)
        val tvAwayAssist:   TextView   = view.findViewById(R.id.tvAwayAssist)
        val tvAwayIcon:     TextView   = view.findViewById(R.id.tvAwayIcon)
        val ivAwayPenMiss:  ImageView  = view.findViewById(R.id.ivAwayPenMiss)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val event = events[position]

        // ── 이벤트 분류 ─────────────────────────────────────
        val isSubst         = event.type == "subst"
        val isPenaltyGoal   = event.type == "Goal" && event.detail.equals("Penalty", ignoreCase = true)
        val isMissedPenalty = event.type == "Goal" && event.detail.equals("Missed Penalty", ignoreCase = true)
        val isOwnGoal       = event.type == "Goal" && event.detail.equals("Own Goal", ignoreCase = true)
        val isVar           = event.type.equals("Var", ignoreCase = true)

        // ── 시간 ─────────────────────────────────────────────
        holder.tvEventTime.text =
            if (event.extraTime != null) "${event.time}+${event.extraTime}'"
            else "${event.time}'"

        // ── 재사용 리셋 ───────────────────────────────────────
        holder.tvHomePlayer.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        holder.tvHomeAssist.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        holder.tvAwayPlayer.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        holder.tvAwayAssist.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        holder.tvHomePlayer.setTextColor(Color.WHITE)
        holder.tvHomeAssist.setTextColor(Color.parseColor("#888888"))
        holder.tvAwayPlayer.setTextColor(Color.WHITE)
        holder.tvAwayAssist.setTextColor(Color.parseColor("#888888"))
        holder.tvHomeIcon.visibility   = View.VISIBLE
        holder.tvAwayIcon.visibility   = View.VISIBLE
        holder.ivHomePenMiss.visibility = View.GONE
        holder.ivAwayPenMiss.visibility = View.GONE

        // ── 아이콘 ────────────────────────────────────────────
        val icon = when {
            isSubst || isMissedPenalty -> ""   // 교체/실축: 텍스트 아이콘 사용 안 함
            event.type == "Goal"       -> "⚽"
            event.type == "Card"       ->
                if (event.detail?.contains("Yellow", ignoreCase = true) == true) "🟨" else "🟥"
            isVar                      -> "📹"  // VAR 판정
            else -> "📌"
        }

        // ── 부가 텍스트 (어시스트 / 패널티 종류 / 교체 선수) ──
        val subText = when {
            isSubst         -> event.assistPlayerName
            isMissedPenalty -> "패널티킥 실축"
            isPenaltyGoal   -> "패널티킥"
            isOwnGoal       -> "자책골"
            isVar           -> varDetailToKorean(event.detail)
            event.type == "Goal" ->
                if (!event.assistPlayerName.isNullOrBlank() && event.assistPlayerName != "null")
                    "어시스트: ${event.assistPlayerName}"
                else null
            else -> null
        }

        // ── 홈팀 바인딩 ──────────────────────────────────────
        if (event.teamId == homeTeamId) {
            holder.layoutHomeEvent.visibility = View.VISIBLE
            holder.layoutAwayEvent.visibility = View.INVISIBLE

            holder.tvHomeIcon.text = icon
            holder.tvHomePlayer.text = event.playerName ?: "알 수 없는 선수"

            // 패널티 실축: ImageView 사용
            if (isMissedPenalty) {
                holder.tvHomeIcon.visibility   = View.GONE
                holder.ivHomePenMiss.visibility = View.VISIBLE
            }

            when {
                isSubst -> {
                    holder.tvHomeIcon.visibility = View.GONE
                    holder.tvHomePlayer.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_sub_in, 0)
                    holder.tvHomePlayer.compoundDrawablePadding = 12
                    if (!subText.isNullOrBlank() && subText != "null") {
                        holder.tvHomeAssist.visibility = View.VISIBLE
                        holder.tvHomeAssist.text = subText
                        holder.tvHomeAssist.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_sub_out, 0)
                        holder.tvHomeAssist.compoundDrawablePadding = 12
                        holder.tvHomeAssist.setTextColor(Color.parseColor("#FF4D4D"))
                    } else {
                        holder.tvHomeAssist.visibility = View.GONE
                    }
                }
                subText != null -> {
                    holder.tvHomeAssist.visibility = View.VISIBLE
                    holder.tvHomeAssist.text = subText
                }
                else -> holder.tvHomeAssist.visibility = View.GONE
            }

            // ── 원정팀 바인딩 ─────────────────────────────────────
        } else {
            holder.layoutHomeEvent.visibility = View.INVISIBLE
            holder.layoutAwayEvent.visibility = View.VISIBLE

            holder.tvAwayIcon.text = icon
            holder.tvAwayPlayer.text = event.playerName ?: "알 수 없는 선수"

            // 패널티 실축: ImageView 사용
            if (isMissedPenalty) {
                holder.tvAwayIcon.visibility   = View.GONE
                holder.ivAwayPenMiss.visibility = View.VISIBLE
            }

            when {
                isSubst -> {
                    holder.tvAwayIcon.visibility = View.GONE
                    holder.tvAwayPlayer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_sub_in, 0, 0, 0)
                    holder.tvAwayPlayer.compoundDrawablePadding = 12
                    if (!subText.isNullOrBlank() && subText != "null") {
                        holder.tvAwayAssist.visibility = View.VISIBLE
                        holder.tvAwayAssist.text = subText
                        holder.tvAwayAssist.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_sub_out, 0, 0, 0)
                        holder.tvAwayAssist.compoundDrawablePadding = 12
                        holder.tvAwayAssist.setTextColor(Color.parseColor("#FF4D4D"))
                    } else {
                        holder.tvAwayAssist.visibility = View.GONE
                    }
                }
                subText != null -> {
                    holder.tvAwayAssist.visibility = View.VISIBLE
                    holder.tvAwayAssist.text = subText
                }
                else -> holder.tvAwayAssist.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = events.size

    private fun varDetailToKorean(detail: String?): String {
        val d = detail?.lowercase() ?: return "VAR 판정"
        return when {
            d.contains("foul")                    -> "득점 취소 - 파울"
            d.contains("offside")                 -> "득점 취소 - 오프사이드"
            d.contains("handball")                -> "득점 취소 - 핸드볼"
            d.contains("disallowed") ||
                    d.contains("cancelled") &&
                    d.contains("goal")                    -> "득점 취소"
            d.contains("penalty confirmed")       -> "페널티킥 인정"
            d.contains("penalty cancelled") ||
                    d.contains("penalty reverted")        -> "페널티킥 취소"
            d.contains("card upgrade")            -> "카드 업그레이드"
            d.contains("card cancelled")          -> "카드 취소"
            else                                  -> "VAR - $detail"
        }
    }
}