package com.example.livescore.push

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.livescore.MainActivity
import com.example.livescore.MatchData
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import com.example.livescore.match.MatchDetailActivity
import kotlinx.coroutines.launch

// 알림을 누르면 열리는 화면. 알림에는 경기 번호만 담겨 있어서, 지금 경기 정보를 받아 경기 상세로 보낸다.
// (알림이 온 뒤 점수가 바뀌었어도 상세 화면에는 최신 점수가 보인다)
class PushLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtureId = intent.getStringExtra("fixtureId")?.toLongOrNull() ?: intent.getLongExtra("fixtureId", -1L)
        if (fixtureId == -1L) {
            openHome()
            return
        }
        lifecycleScope.launch {
            when (val result = apiCall { RetrofitClient.matchApi.match(fixtureId) }) {
                is ApiResult.Success -> openMatch(result.data)
                is ApiResult.Failure -> {
                    Toast.makeText(this@PushLaunchActivity, result.message, Toast.LENGTH_SHORT).show()
                    openHome()
                }
            }
        }
    }

    private fun openMatch(m: MatchData) {
        startActivity(
            Intent(this, MatchDetailActivity::class.java).apply {
                putExtra("fixtureId", m.fixtureId)
                putExtra("homeTeam", m.homeTeam)
                putExtra("awayTeam", m.awayTeam)
                putExtra("homeTeamId", m.homeTeamId)
                putExtra("awayTeamId", m.awayTeamId)
                putExtra("matchTime", m.matchTime)
                putExtra("matchDate", m.matchDate)
                putExtra("score", m.score)
                putExtra("leagueId", m.leagueId)
                putExtra("season", m.season)
                putExtra("stadium", m.stadium)
                putExtra("matchRound", m.matchRound)
            }
        )
        finish()
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
