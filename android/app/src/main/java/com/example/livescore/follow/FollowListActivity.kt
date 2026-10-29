package com.example.livescore.follow

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.FaceCropTransformation
import com.example.livescore.R
import com.example.livescore.databinding.ActivityFollowListBinding
import com.example.livescore.player.PlayerDetailActivity
import com.example.livescore.team.TeamDetailActivity

// 내가 팔로우한 팀과 선수를 모아 보고, 눌러서 상세로 가거나 해제한다.
class FollowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private val adapter = FollowAdapter(
        onTeam = { team ->
            startActivity(Intent(this, TeamDetailActivity::class.java).apply {
                putExtra(TeamDetailActivity.EXTRA_TEAM_ID, team.id)
                putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, team.name)
            })
        },
        onPlayer = { player ->
            startActivity(Intent(this, PlayerDetailActivity::class.java).apply {
                putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, player.id)
            })
        },
        onUnfollowTeam = { FollowStore.toggleTeam(it) { msg -> toast(msg) } },
        onUnfollowPlayer = { FollowStore.togglePlayer(it) { msg -> toast(msg) } }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvFollows.layoutManager = LinearLayoutManager(this)
        binding.rvFollows.adapter = adapter

        FollowStore.follows.observe(this) { follows ->
            adapter.submit(follows)
            binding.tvEmpty.visibility = if (follows.teams.isEmpty() && follows.players.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        FollowStore.refresh()   // 다른 기기에서 바꾼 팔로우도 반영한다
    }

    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}

private class FollowAdapter(
    private val onTeam: (FollowedTeamData) -> Unit,
    private val onPlayer: (FollowedPlayerData) -> Unit,
    private val onUnfollowTeam: (FollowedTeamData) -> Unit,
    private val onUnfollowPlayer: (FollowedPlayerData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Team(val team: FollowedTeamData) : Row()
        data class Player(val player: FollowedPlayerData) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(follows: FollowListData) {
        rows = buildList {
            if (follows.teams.isNotEmpty()) {
                add(Row.Header("팀"))
                follows.teams.forEach { add(Row.Team(it)) }
            }
            if (follows.players.isNotEmpty()) {
                add(Row.Header("선수"))
                follows.players.forEach { add(Row.Player(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) HEADER else ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == HEADER) {
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_stat_header, parent, false)) {}
        } else {
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_follow_row, parent, false)) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView
        when (val row = rows[position]) {
            is Row.Header -> (view as TextView).text = row.title
            is Row.Team -> bind(view, row.team.name, row.team.logoUrl, face = false, open = { onTeam(row.team) }, unfollow = { onUnfollowTeam(row.team) })
            is Row.Player -> bind(view, row.player.name, row.player.photoUrl, face = true, open = { onPlayer(row.player) }, unfollow = { onUnfollowPlayer(row.player) })
        }
    }

    private fun bind(view: View, name: String, imageUrl: String?, face: Boolean, open: () -> Unit, unfollow: () -> Unit) {
        view.findViewById<TextView>(R.id.tvFollowName).text = name
        val image = view.findViewById<ImageView>(R.id.ivFollowImage)
        val request = Glide.with(view).load(imageUrl).placeholder(R.drawable.bg_player_circle)
        if (face) request.transform(FaceCropTransformation(), CircleCrop()).into(image) else request.into(image)
        view.setOnClickListener { open() }
        view.findViewById<View>(R.id.btnUnfollow).setOnClickListener { unfollow() }
    }

    private companion object {
        const val HEADER = 0
        const val ITEM = 1
    }
}
