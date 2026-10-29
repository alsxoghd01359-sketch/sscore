package com.example.livescore.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.livescore.R
import com.example.livescore.common.renderState
import com.example.livescore.databinding.ActivityNotificationSettingsBinding

// 알림 설정: 알림 종류 4개 + 팔로우한 팀·선수별 켜고 끄기
class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private val viewModel: NotificationSettingsViewModel by viewModels()
    private val adapter = SettingsAdapter(
        onKind = { key, on -> viewModel.setKind(key, on) },
        onTarget = { isTeam, id, on -> viewModel.setTarget(isTeam, id, on) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.errorView.btnRetry.setOnClickListener { viewModel.load() }
        binding.rvSettings.layoutManager = LinearLayoutManager(this)
        binding.rvSettings.adapter = adapter

        viewModel.state.observe(this) { state ->
            binding.rvSettings.visibility = if (state is com.example.livescore.common.UiState.Success) View.VISIBLE else View.GONE
            renderState(state, binding.pbLoading, binding.errorView) { adapter.submit(it) }
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.messageShown()
            }
        }
    }
}

private class SettingsAdapter(
    private val onKind: (key: String, on: Boolean) -> Unit,
    private val onTarget: (isTeam: Boolean, id: Long, on: Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String, val hint: String? = null) : Row()
        data class Kind(val key: String, val label: String, val on: Boolean) : Row()
        data class Target(val isTeam: Boolean, val data: TargetSettingsData) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(data: NotificationSettingsData) {
        val k = data.kinds
        rows = buildList {
            add(Row.Header("알림 종류", "꺼 둔 종류는 모든 팀·선수에게서 오지 않아요."))
            add(Row.Kind("matchStart", "경기 시작", k.matchStart))
            add(Row.Kind("goalsEvents", "득점 · 주요 이벤트", k.goalsEvents))
            add(Row.Kind("matchEnd", "경기 종료 · 결과", k.matchEnd))
            add(Row.Kind("lineup", "라인업 발표", k.lineup))
            if (data.teams.isNotEmpty() || data.players.isNotEmpty()) {
                add(Row.Header("팔로우 대상", "대상을 끄면 그 팀·선수의 알림은 오지 않아요."))
                data.teams.forEach { add(Row.Target(true, it)) }
                data.players.forEach { add(Row.Target(false, it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) HEADER else ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == HEADER) R.layout.item_settings_header else R.layout.item_settings_switch
        return object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView
        when (val row = rows[position]) {
            is Row.Header -> {
                view.findViewById<TextView>(R.id.tvHeaderTitle).text = row.title
                view.findViewById<TextView>(R.id.tvHeaderHint).apply {
                    text = row.hint.orEmpty()
                    visibility = if (row.hint == null) View.GONE else View.VISIBLE
                }
            }
            is Row.Kind -> bindSwitch(view, row.label, null, null, row.on) { on -> onKind(row.key, on) }
            is Row.Target -> {
                val d = row.data
                bindSwitch(view, d.name, d.imageUrl, if (row.isTeam) "팀" else "선수", d.notify) { on -> onTarget(row.isTeam, d.id, on) }
            }
        }
    }

    private fun bindSwitch(view: View, title: String, imageUrl: String?, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        view.findViewById<TextView>(R.id.tvSwitchTitle).text = title
        view.findViewById<TextView>(R.id.tvSwitchSub).apply {
            text = sub.orEmpty()
            visibility = if (sub == null) View.GONE else View.VISIBLE
        }
        val image = view.findViewById<ImageView>(R.id.ivSwitchImage)
        if (imageUrl == null && sub == null) image.visibility = View.GONE
        else {
            image.visibility = View.VISIBLE
            Glide.with(view).load(imageUrl).placeholder(R.drawable.bg_player_circle).into(image)
        }
        val toggle = view.findViewById<SwitchCompat>(R.id.switchValue)
        toggle.setOnCheckedChangeListener(null)   // 재사용된 칸에서 값을 채울 때 저장이 호출되지 않게
        toggle.isChecked = checked
        toggle.setOnCheckedChangeListener { _: CompoundButton, on: Boolean -> onChange(on) }
    }

    private companion object {
        const val HEADER = 0
        const val ITEM = 1
    }
}
