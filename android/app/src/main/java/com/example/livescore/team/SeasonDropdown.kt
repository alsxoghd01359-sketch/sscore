package com.example.livescore.team

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.league.seasonLabel

// 순위·통계 탭 맨 위의 시즌 선택 드롭다운. 시즌 목록과 선택은 TeamDetailViewModel의 값을 쓰므로 두 탭이 함께 바뀐다.
class SeasonDropdown(
    private val fragment: Fragment,
    private val vm: TeamDetailViewModel,
    private val selector: View,
    private val scroll: View,
    private val list: LinearLayout,
    private val arrow: TextView,
    private val title: TextView,
    private val label: TextView,
    private val logo: ImageView
) {
    private var open = false
    private val owner get() = fragment.viewLifecycleOwner
    private val context get() = fragment.requireContext()

    fun bind() {
        selector.setOnClickListener { setOpen(!open) }
        vm.selectedSeason.observe(owner) { refresh() }
        vm.seasons.observe(owner) { refresh() }
        vm.info.observe(owner) { refresh() }
    }

    private fun setOpen(value: Boolean) {
        open = value
        scroll.visibility = if (open) View.VISIBLE else View.GONE
        arrow.text = if (open) "▲" else "▼"
    }

    private fun refresh() {
        val leagueId = vm.leagueId.value
        val leagueName = vm.info.value?.leagueName.orEmpty()
        val selected = vm.selectedSeason.value
        title.text = leagueName
        label.text = selected?.let { seasonLabel(it) }.orEmpty()
        if (leagueId != null) {
            Glide.with(fragment).load("https://media.api-sports.io/football/leagues/$leagueId.png").into(logo)
        }

        list.removeAllViews()
        vm.seasons.value.orEmpty().forEach { season ->
            val isSelected = season == selected
            list.addView(row(season, leagueId, leagueName, isSelected).apply {
                setOnClickListener {
                    vm.selectSeason(season)
                    setOpen(false)
                }
            })
        }
    }

    private fun row(season: Int, leagueId: Int?, leagueName: String, isSelected: Boolean): LinearLayout {
        val accent = Color.parseColor("#F2711C")
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
            setPadding(dp(16), 0, dp(16), 0)
            if (isSelected) setBackgroundColor(Color.parseColor("#FFF1E6"))

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                if (leagueId != null) {
                    Glide.with(fragment).load("https://media.api-sports.io/football/leagues/$leagueId.png").into(this)
                }
            })

            val names = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(10) }
            }
            names.addView(TextView(context).apply {
                text = leagueName
                textSize = 12f
                setTextColor(if (isSelected) accent else Color.parseColor("#1A1A1A"))
            })
            names.addView(TextView(context).apply {
                text = seasonLabel(season)
                textSize = 11f
                setTextColor(if (isSelected) accent else Color.parseColor("#6B7280"))
            })
            addView(names)

            if (isSelected) {
                addView(TextView(context).apply {
                    text = "✓"
                    textSize = 14f
                    setTextColor(accent)
                })
            }
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
