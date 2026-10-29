package com.sscore.service

import com.sscore.entity.LeagueEntity
import com.sscore.football.SeasonResolver
import com.sscore.league.LeagueCatalog
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
    private val seasons: SeasonResolver,
    private val catalog: LeagueCatalog,
    @Value("\${sscore.init.enabled:true}") private val initEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(this::class.java)


    @PostConstruct
    fun initLeagues() {
        catalog.all().forEach { meta ->
            if (!leagueRepository.existsById(meta.id)) {
                leagueRepository.save(LeagueEntity(meta.id, meta.nameEn.ifEmpty { meta.nameKo }, meta.country.ifEmpty { null }, catalog.logoUrl(meta.id), "League"))
                log.info("리그 등록: {}", meta.nameKo)
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

        val currentSeason = seasons.current()
        log.info("🚀 [DataInitService] DB가 비어 있음 → 현재 시즌({}) 데이터 적재 시작...", currentSeason)
        for (leagueId in catalog.ids()) {
            runCatching { apiService.syncTeams(leagueId, currentSeason) }
                .onFailure { log.error("팀 초기화 실패: leagueId={}", leagueId, it) }

            Thread.sleep(300)

            runCatching { apiService.syncFixtures(leagueId, currentSeason) }
                .onFailure { log.error("경기 초기화 실패: leagueId={}", leagueId, it) }

            Thread.sleep(300)
        }
        log.info("✅ [DataInitService] 현재 시즌({}) 초기 적재 완료", currentSeason)
    }
}