package com.example.livescore.auth

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.livescore.RetrofitClient
import com.example.livescore.follow.FollowStore
import com.example.livescore.member.ProfileActivity
import kotlinx.coroutines.launch

class SScoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        FollowStore.init()
    }
}

// 개인화 기능(팔로우·알림·프로필 등)에서 쓴다. 로그인 상태면 바로 실행하고, 게스트면 로그인 화면으로 안내한다.
fun AppCompatActivity.requireLogin(onLoggedIn: () -> Unit) {
    if (SessionManager.isLoggedIn) {
        onLoggedIn()
    } else {
        Toast.makeText(this, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
    }
}

// 이 기기의 로그인을 끝낸다. 서버에 알리지 못해도(네트워크 오류 등) 기기에서는 로그아웃한다.
fun AppCompatActivity.logout() {
    val access = SessionManager.accessToken
    SessionManager.clear()
    if (access != null) {
        lifecycleScope.launch {
            AuthRepository(RetrofitClient.authApi).logout(access)
        }
    }
}

// 헤더의 계정 버튼: 게스트는 로그인 화면으로, 회원은 내 정보 화면으로 간다.
fun AppCompatActivity.showAccountMenu() {
    val member = SessionManager.member.value
    if (member == null) {
        startActivity(Intent(this, LoginActivity::class.java))
        return
    }
    startActivity(Intent(this, ProfileActivity::class.java))
}
