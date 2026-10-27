package com.example.livescore

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.databinding.DialogPlayerMatchStatsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlayerMatchStatsDialog : DialogFragment() {

    private var _binding: DialogPlayerMatchStatsBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(playerId: Long, fixtureId: Long) =
            PlayerMatchStatsDialog().apply {
                arguments = Bundle().apply {
                    putLong("playerId", playerId)
                    putLong("fixtureId", fixtureId)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlayerMatchStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        val playerId  = arguments?.getLong("playerId")  ?: return
        val fixtureId = arguments?.getLong("fixtureId") ?: return
        loadStats(playerId, fixtureId)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels  * 0.92f).toInt(),
                (resources.displayMetrics.heightPixels * 0.85f).toInt()
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    // ── 데이터 로드 ───────────────────────────────────────────
    private fun loadStats(playerId: Long, fixtureId: Long) {
        RetrofitClient.apiService.getPlayerMatchStats(playerId, fixtureId)
            .enqueue(object : Callback<PlayerMatchStatData> {
                override fun onResponse(
                    call: Call<PlayerMatchStatData>,
                    response: Response<PlayerMatchStatData>
                ) {
                    if (!isAdded || _binding == null) return
                    val data = response.body() ?: return
                    renderHeader(data)
                    // 포지션 분기: G=골키퍼, 나머지=필드플레이어
                    if (data.position?.uppercase() == "G") renderGkStats(data)
                    else renderFieldStats(data)
                }
                override fun onFailure(call: Call<PlayerMatchStatData>, t: Throwable) {}
            })
    }

    // ── 헤더 ─────────────────────────────────────────────────
    private fun renderHeader(d: PlayerMatchStatData) {
        Glide.with(this)
            .load(d.photoUrl)
            .transform(FaceCropTransformation(), CircleCrop())
            .placeholder(R.drawable.bg_player_circle)
            .into(binding.ivPlayerPhoto)

        binding.tvPlayerName.text  = d.playerName
        binding.tvTeamName.text    = d.teamName
        binding.tvPosition.text    = positionLabel(d.position)
        binding.tvNationality.text = d.nationality ?: "-"
        binding.tvAge.text         = if (d.age != null) "${d.age}세" else "-"

        val ratingText = d.rating?.let { "%.1f".format(it) } ?: "-"
        binding.tvRatingBadge.text = ratingText
        binding.tvRatingBadge.setTextColor(Color.parseColor("#FFFFFF"))
        val bgColor = if (d.rating != null) ratingBgColor(d.rating) else Color.parseColor("#6B7280")
        binding.tvRatingBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * resources.displayMetrics.density
            setColor(bgColor)
        }
    }

    // ── 골키퍼 스탯 (데이터 있는 항목만 표시) ──────────────
    private fun renderGkStats(d: PlayerMatchStatData) {
        val c = binding.layoutStats
        c.removeAllViews()

        fun cnt(v: Int?) = (v ?: 0).toString()

        addSection(c, "경기 기록")
        addRow(c, "출전 시간",       d.minutesPlayed?.let { "${it}분" })
        addRow(c, "평점",            d.rating?.let { "%.2f".format(it) })
        addRow(c, "선방수",          cnt(d.saves))
        addRow(c, "실점수",          cnt(d.goalsConceded))
        if (d.opponentXg != null) {
            addRow(c, "상대 xG",     "%.2f".format(d.opponentXg))
        }

        addSection(c, "패스")
        val passStr = if (d.passesTotal != null && d.passesAccuracy != null)
            "${d.passesAccuracy}/${d.passesTotal}"
        else
            d.passesTotal?.toString()
        if (passStr != null) addRow(c, "패스 성공/시도", passStr)
    }

    // ── 필드플레이어 스탯 ────────────────────────────────────
    private fun renderFieldStats(d: PlayerMatchStatData) {
        val c = binding.layoutStats
        c.removeAllViews()

        fun cnt(v: Int?) = (v ?: 0).toString()

        addSection(c, "경기 기록")
        addRow(c, "출전 시간",        d.minutesPlayed?.let { "${it}분" })
        addRow(c, "평점",             d.rating?.let { "%.2f".format(it) })
        addRow(c, "득점",             cnt(d.goals))
        addRow(c, "어시스트",         cnt(d.assists))

        addSection(c, "슈팅")
        addRow(c, "유효 슈팅",        cnt(d.shotsOn))
        val shotsOff = (d.shotsTotal ?: 0) - (d.shotsOn ?: 0)
        addRow(c, "빗나간 슈팅",      shotsOff.toString())
        val shotAcc = if ((d.shotsTotal ?: 0) > 0 && d.shotsOn != null)
            "${"%.0f".format(d.shotsOn * 100.0 / d.shotsTotal!!)}%" else null
        addRow(c, "슈팅 정확도",      shotAcc)

        addSection(c, "패스")
        val passStr = if (d.passesTotal != null && d.passesAccuracy != null)
            "${d.passesAccuracy}/${d.passesTotal}" else cnt(d.passesTotal)
        addRow(c, "성공/시도",        passStr)
        addRow(c, "기회 창출",        cnt(d.keyPasses))

        addSection(c, "드리블 & 볼경합")
        val dribStr = if (d.dribblesAttempts != null)
            "${d.dribblesSuccess ?: 0}/${d.dribblesAttempts}" else cnt(d.dribblesSuccess)
        addRow(c, "드리블 성공/시도", dribStr)
        val duelStr = if (d.duelsTotal != null)
            "${d.duelsWon ?: 0}/${d.duelsTotal}" else cnt(d.duelsWon)
        addRow(c, "볼경합 성공/시도", duelStr)

        addSection(c, "수비")
        addRow(c, "태클",             cnt(d.tacklesTotal))
        addRow(c, "볼 차단",          cnt(d.tacklesBlocks))
        addRow(c, "가로채기",         cnt(d.tacklesInterceptions))

        addSection(c, "파울")
        addRow(c, "당한 파울",        cnt(d.foulsDrawn))
        addRow(c, "반칙 횟수",        cnt(d.foulsCommitted))
    }

    // ── 뷰 헬퍼 ──────────────────────────────────────────────
    private fun addSection(parent: LinearLayout, title: String) {
        parent.addView(TextView(requireContext()).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(4))
        })
        parent.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                .also { it.setMargins(dp(16), 0, dp(16), 0) }
            setBackgroundColor(Color.parseColor("#E6E8EB"))
        })
    }

    private fun addRow(parent: LinearLayout, label: String, value: String?) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        row.addView(TextView(requireContext()).apply {
            text = label
            textSize = 15f
            setTextColor(Color.parseColor("#374151"))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val isNull = value == null
        row.addView(TextView(requireContext()).apply {
            text = value ?: "-"
            textSize = 15f
            setTextColor(if (!isNull) Color.parseColor("#1A1A1A") else Color.parseColor("#9CA3AF"))
            setTypeface(null, if (!isNull) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        parent.addView(row)
    }

    private fun positionLabel(pos: String?) = when (pos?.uppercase()) {
        "G"  -> "골키퍼"
        "D"  -> "수비수"
        "M"  -> "미드필더"
        "F"  -> "공격수"
        else -> pos ?: "-"
    }

    private fun ratingBgColor(r: Double) = when {
        r >= 8.0 -> Color.parseColor("#0891B2")
        r >= 7.0 -> Color.parseColor("#16A34A")
        r >= 6.0 -> Color.parseColor("#D97706")
        else     -> Color.parseColor("#DC2626")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}