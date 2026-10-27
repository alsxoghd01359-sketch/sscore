package com.example.livescore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.livescore.databinding.ActivityMatchDetailBinding
import com.google.android.material.tabs.TabLayoutMediator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MatchDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fixtureId = intent.getLongExtra("fixtureId", -1L)
        val homeTeam = intent.getStringExtra("homeTeam") ?: "Home"
        val awayTeam = intent.getStringExtra("awayTeam") ?: "Away"
        val homeTeamId = intent.getIntExtra("homeTeamId", 0)
        val awayTeamId = intent.getIntExtra("awayTeamId", 0)
        val matchTime = intent.getStringExtra("matchTime") ?: "23:00"
        val matchDate = intent.getStringExtra("matchDate")
        val score = intent.getStringExtra("score") ?: "VS"
        val leagueId = intent.getIntExtra("leagueId", 0)
        val season = intent.getIntExtra("season", 2024)
        val stadium = intent.getStringExtra("stadium")
        val matchRound = intent.getStringExtra("matchRound")

        binding.tvDetailHomeName.text = homeTeam
        binding.tvDetailAwayName.text = awayTeam
        binding.tvDetailScore.text = score.replace("-", " - ")

        var finalDateString = matchDate ?: "날짜 미정"
        if (matchDate != null && matchDate.contains("-")) {
            try {
                val dateParts = matchDate.split("-")
                val month = if (dateParts.size == 3) dateParts[1].toInt().toString() else dateParts[0].toInt().toString()
                val day = if (dateParts.size == 3) dateParts[2].toInt().toString() else dateParts[1].toInt().toString()

                val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                finalDateString = if (matchDate == todayStr) {
                    "오늘 (${month}월 ${day}일)"
                } else {
                    "${month}월 ${day}일"
                }
            } catch (e: Exception) {
                finalDateString = matchDate
            }
        }

        binding.tvDetailMatchStatus.text = "$finalDateString | $matchTime"

        val homeLogoUrl = "https://media.api-sports.io/football/teams/$homeTeamId.png"
        val awayLogoUrl = "https://media.api-sports.io/football/teams/$awayTeamId.png"

        Glide.with(this).load(homeLogoUrl).into(binding.ivDetailHomeLogo)
        Glide.with(this).load(awayLogoUrl).into(binding.ivDetailAwayLogo)

        val tabTitles = mutableListOf<String>()

        when {
            matchTime.contains("종료") || matchTime.contains("FT") -> {
                tabTitles.addAll(listOf("정보", "라인업", "전체 통계", "상대전적"))
            }
            matchTime.contains("전반") || matchTime.contains("후반") || matchTime.contains("하프타임") || matchTime.contains("진행중") -> {
                tabTitles.addAll(listOf("정보", "라인업", "전체 통계", "상대전적"))
            }
            matchTime.contains(":") || matchTime.isEmpty() -> {
                tabTitles.addAll(listOf("미리보기", "라인업", "순위", "상대전적"))
            }
            else -> {
                tabTitles.addAll(listOf("미리보기", "라인업", "순위", "상대전적"))
            }
        }

        val adapter = MatchDetailPagerAdapter(
            fa = this,
            fixtureId = fixtureId,
            leagueId = leagueId,
            season = season,
            homeTeamId = homeTeamId,
            awayTeamId = awayTeamId,
            stadium = stadium,
            round = matchRound,
            tabTitles = tabTitles,
            homeTeamName = homeTeam, // 🌟 추가됨
            awayTeamName = awayTeam  // 🌟 추가됨
        )
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.btnBack.setOnClickListener { finish() }

        // 엠블럼 클릭 → 구단 상세 페이지 이동
        val leagueName = leagueIdToName(leagueId)

        binding.ivDetailHomeLogo.setOnClickListener {
            startTeamDetail(homeTeamId, homeTeam, leagueId, leagueName, season)
        }
        binding.ivDetailAwayLogo.setOnClickListener {
            startTeamDetail(awayTeamId, awayTeam, leagueId, leagueName, season)
        }
    }

    private fun startTeamDetail(
        teamId: Int, teamName: String,
        leagueId: Int, leagueName: String, season: Int
    ) {
        val intent = android.content.Intent(this, TeamDetailActivity::class.java).apply {
            putExtra("teamId", teamId)
            putExtra("teamName", teamName)
            putExtra("leagueId", leagueId)
            putExtra("leagueName", leagueName)
            putExtra("season", season)
        }
        startActivity(intent)
    }

    private fun leagueIdToName(leagueId: Int): String = when (leagueId) {
        39  -> "프리미어리그"
        140 -> "라리가"
        78  -> "분데스리가"
        135 -> "세리에 A"
        61  -> "리그 1"
        else -> "리그"
    }
}