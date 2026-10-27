package com.sscore.service

import com.sscore.entity.LeagueEntity
import com.sscore.repository.LeagueRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.TeamRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 서버 시작 시 DB가 비어 있을 경우에만 현재 시즌 데이터를 가볍게 채웁니다.
// 10년치 대량 적재는 BulkHistoricalInitService + /api/soccer/admin/import/ 엔드포인트로 실행하세요.
@Service
class DataInitService(
    private val apiService: ApiFootballService,
    private val leagueRepository: LeagueRepository,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    @Value("\${sscore.init.enabled:true}") private val initEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val bigFiveLeagueIds = listOf(39, 140, 78, 135, 61)
    private val currentSeason    = 2025

    @PostConstruct
    fun initLeagues() {
        val leagues = listOf(
            LeagueEntity(39,  "Premier League", "England", "https://media.api-sports.io/football/leagues/39.png",  "League"),
            LeagueEntity(140, "La Liga",         "Spain",   "https://media.api-sports.io/football/leagues/140.png", "League"),
            LeagueEntity(78,  "Bundesliga",      "Germany", "https://media.api-sports.io/football/leagues/78.png",  "League"),
            LeagueEntity(135, "Serie A",         "Italy",   "https://media.api-sports.io/football/leagues/135.png", "League"),
            LeagueEntity(61,  "Ligue 1",         "France",  "https://media.api-sports.io/football/leagues/61.png",  "League")
        )
        leagues.forEach { league ->
            if (!leagueRepository.existsById(league.leagueId)) {
                leagueRepository.save(league)
                log.info("✅ 리그 등록: {}", league.name)
            }
        }
    }

    @PostConstruct
    fun initCurrentSeasonIfEmpty() {
        if (!initEnabled) return
        val matchCount = matchRepository.count()
        if (matchCount > 0) {
            log.info("✅ [DataInitService] DB에 경기 데이터 {}건 존재 → 초기화 스킵", matchCount)
            return
        }

        log.info("🚀 [DataInitService] DB가 비어 있음 → 현재 시즌({}) 데이터 적재 시작...", currentSeason)
        for (leagueId in bigFiveLeagueIds) {
            runCatching { apiService.syncTeams(leagueId, currentSeason) }
                .onFailure { log.error("팀 초기화 실패: leagueId={}", leagueId) }

            Thread.sleep(300)

            runCatching { apiService.syncFixtures(leagueId, currentSeason) }
                .onFailure { log.error("경기 초기화 실패: leagueId={}", leagueId) }

            Thread.sleep(300)
        }
        log.info("✅ [DataInitService] 현재 시즌({}) 초기 적재 완료", currentSeason)
    }
}