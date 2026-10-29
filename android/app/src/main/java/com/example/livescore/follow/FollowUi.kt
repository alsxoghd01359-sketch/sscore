package com.example.livescore.follow

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.livescore.R
import com.example.livescore.auth.requireLogin

private fun TextView.showFollowing(following: Boolean) {
    visibility = View.VISIBLE
    text = if (following) "★ 팔로잉" else "☆ 팔로우"
    setBackgroundResource(if (following) R.drawable.bg_follow_on else R.drawable.bg_follow_off)
    setTextColor(context.getColor(if (following) R.color.on_accent else R.color.accent))
}

// 팀 상세의 팔로우 버튼: 상태를 보여 주고 누르면 바로 바꾼다. 게스트가 누르면 로그인 화면으로 안내한다.
fun AppCompatActivity.bindTeamFollow(button: TextView, team: FollowedTeamData) {
    FollowStore.follows.observe(this) { button.showFollowing(FollowStore.isTeamFollowed(team.id)) }
    button.setOnClickListener {
        requireLogin {
            FollowStore.toggleTeam(team) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }
}

fun AppCompatActivity.bindPlayerFollow(button: TextView, player: FollowedPlayerData) {
    FollowStore.follows.observe(this) { button.showFollowing(FollowStore.isPlayerFollowed(player.id)) }
    button.setOnClickListener {
        requireLogin {
            FollowStore.togglePlayer(player) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }
}
