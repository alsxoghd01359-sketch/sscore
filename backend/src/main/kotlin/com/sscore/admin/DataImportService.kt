package com.sscore.admin

import com.sscore.entity.MatchEntity
import com.sscore.league.LeagueCatalog
import com.sscore.match.MatchDetailService
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchEventRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerStatRepository
import com.sscore.service.ApiFootballService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 관리자가 실행하는 데이터 적재 작업(경기·우승팀·선수 기록·이벤트)
@Service
class DataImportService(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val playerStatRepository: PlayerStatRepository,
    private val apiService: ApiFootballService,
    private val championRepository: ChampionRepository,
    private val matchDetails: MatchDetailService,
    private val leagueCatalog: LeagueCatalog
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 시즌별로 외부 API에서 받은 경기를 저장한다. 외부 API 오류가 나면 그 시즌부터 중단하고 오류를 그대로 올린다(앞 시즌 저장분은 유지).
    fun saveSeasonsRange(start: Int, end: Int) {
        for (season in start..end) {
            val matchDtos = apiService.fetchMatchesBySeason(season)
            if (matchDtos.isEmpty()) continue

            val entities = matchDtos.map { dto ->
                val existing = matchRepository.findByFixtureId(dto.fixtureId!!)
                MatchEntity(
                    id = existing?.id,
                    fixtureId = dto.fixtureId,
                    homeTeam = dto.homeTeam,
                    awayTeam = dto.awayTeam,
                    homeTeamId = dto.homeTeamId,
                    awayTeamId = dto.awayTeamId,
                    matchDate = dto.matchDate,
                    matchTime = dto.matchTime,
                    score = dto.score,
                    season = dto.season,
                    leagueId = dto.leagueId,
                    matchRound = dto.matchRound,
                    stadium = dto.stadium,
                    referee = dto.referee
                )
            }
            matchRepository.saveAll(entities)
        }
    }

    @Transactional
    fun addChampionManually(leagueId: Int, season: Int, teamId: Int, teamName: String) {
        val existing = championRepository.findByTeamId(teamId)
            .find { it.leagueId == leagueId && it.season == season }
        if (existing != null) return  // 이미 존재하면 스킵
        championRepository.save(
            com.sscore.entity.ChampionEntity(
                leagueId = leagueId,
                season   = season,
                teamId   = teamId,
                teamName = teamName
            )
        )
    }

    fun syncChampions(leagueId: Int, season: Int) =
        apiService.syncChampions(leagueId, season)


    // ── 리그 상세 ──────────────────────────────────────────────────

    // ── 통합 검색 ──────────────────────────────────────────────

    // 선수 스탯(평점·출전시간) 일괄 수집 - 백그라운드 실행
    fun importPlayerStatsForLeague(season: Int, leagueId: Int, delayMs: Long = 200): String {
        val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
        val total = matches.size
        log.info("[PlayerStats] 시작 — 리그={} 시즌={} 총{}경기", leagueId, season, total)

        Thread({
            var done = 0; var skipped = 0; var failed = 0
            matches.forEach { m ->
                val fid = m.fixtureId ?: return@forEach
                if (playerStatRepository.findByFixtureId(fid).isNotEmpty()) {
                    skipped++; return@forEach
                }
                try {
                    matchDetails.getInfo(fid)
                    done++
                    log.info("[PlayerStats] {}/{} fixture={} 저장", done, total, fid)
                    Thread.sleep(delayMs)
                } catch (e: Exception) {
                    failed++
                    log.warn("[PlayerStats] fixture={} 실패: {}", fid, e.message)
                }
            }
            log.info("[PlayerStats] 완료 — 저장:{} 스킵:{} 실패:{}", done, skipped, failed)
        }, "player-stats-import").apply { isDaemon = true; start() }

        return "백그라운드 실행 중 — 총 ${total}경기 (서버 콘솔에서 진행상황 확인)"
    }

    fun importMatchEventsForTeam(teamId: Int, season: Int): String {
        val matches = matchRepository.findByTeamAndSeason(teamId, season, leagueCatalog.ids())
        var imported = 0; var skipped = 0; var failed = 0
        matches.forEach { m ->
            val fid = m.fixtureId ?: return@forEach
            if (matchEventRepository.findByFixtureId(fid).isNotEmpty()) { skipped++; return@forEach }
            try {
                val events = apiService.fetchFixtureEvents(fid)
                if (events.isNotEmpty()) { matchEventRepository.saveAll(events); imported++ }
                Thread.sleep(400)
            } catch (e: Exception) {
                failed++
                log.warn("[EventImport] fixture={} 실패: {}", fid, e.message)
            }
        }
        return "완료: 임포트 $imported 경기, 스킵 $skipped 경기, 실패 $failed 경기"
    }

    // 5대 리그 전체 경기 이벤트 일괄 수집
    fun importAllLeagueMatchEvents(season: Int, delayMs: Long = 400, leagueId: Int? = null): String {
        val bigFive  = if (leagueId != null) listOf(leagueId) else leagueCatalog.ids()
        var imported = 0; var skipped = 0; var failed = 0

        bigFive.forEach { leagueId ->
            val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
            log.info("[EventImport] 리그={} 시즌={} 경기수={}", leagueId, season, matches.size)

            matches.forEach { m ->
                val fid = m.fixtureId ?: return@forEach
                // 이미 있으면 스킵
                if (matchEventRepository.findByFixtureId(fid).isNotEmpty()) {
                    skipped++; return@forEach
                }
                try {
                    val events = apiService.fetchFixtureEvents(fid)
                    if (events.isNotEmpty()) {
                        matchEventRepository.saveAll(events)
                        imported++
                        log.info("[EventImport] fixture={} 이벤트={}개 저장", fid, events.size)
                    }
                    Thread.sleep(delayMs)
                } catch (e: Exception) {
                    failed++
                    log.warn("[EventImport] fixture={} 실패: {}", fid, e.message)
                }
            }
        }
        return "완료 — 임포트: ${imported}경기 / 스킵(기존): ${skipped}경기 / 실패: ${failed}경기"
    }
}
