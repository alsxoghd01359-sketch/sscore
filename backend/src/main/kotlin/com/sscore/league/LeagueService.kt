package com.sscore.league

import com.sscore.dto.LeagueInfoDto
import com.sscore.dto.LeagueMatchDayDto
import com.sscore.dto.LeaguePlayerStatsDto
import com.sscore.dto.LeagueTeamStatsDto
import com.sscore.dto.MatchDto
import com.sscore.dto.PlayerRankDto
import com.sscore.dto.StandingDto
import com.sscore.dto.TeamRankDto
import com.sscore.entity.MatchEntity
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.football.SeasonResolver
import com.sscore.repository.LeagueRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.service.ApiFootballService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

data class LeagueSeasonsDto(val default: Int, val seasons: List<Int>)

// 리그 상세 화면(순위·경기·선수 통계·팀 통계)의 조회 로직
@Service
class LeagueService(
    private val catalog: LeagueCatalog,
    private val seasonResolver: SeasonResolver,
    private val apiService: ApiFootballService,
    private val leagueRepository: LeagueRepository,
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val playerSeasonStatRepository: PlayerSeasonStatRepository,
    private val clock: Clock
) {
    fun listLeagues(): List<LeagueInfoDto> = catalog.all().map { meta ->
        leagueRepository.findById(meta.id).orElse(null)?.let {
            LeagueInfoDto(it.leagueId, it.name, it.country, it.logoUrl, it.leagueType)
        } ?: LeagueInfoDto(meta.id, meta.nameEn.ifEmpty { meta.nameKo }, meta.country.ifEmpty { null }, catalog.logoUrl(meta.id), "League")
    }

    fun getLeagueInfo(leagueId: Int): LeagueInfoDto? {
        catalog.require(leagueId)
        val league = leagueRepository.findById(leagueId).orElse(null) ?: return null
        return LeagueInfoDto(
            leagueId = league.leagueId,
            name = league.name,
            country = league.country,
            logoUrl = league.logoUrl,
            leagueType = league.leagueType
        )
    }

    // 데이터가 있는 시즌 목록(최신순)과 기본으로 보여 줄 시즌
    fun getSeasons(leagueId: Int): LeagueSeasonsDto {
        catalog.require(leagueId)
        val available = matchRepository.seasonsOfLeague(leagueId)
        val preferred = seasonResolver.effective()
        val default = if (preferred in available || available.isEmpty()) preferred else available.first()
        return LeagueSeasonsDto(default, available.ifEmpty { listOf(default) })
    }

    fun getStandings(leagueId: Int, season: Int): List<StandingDto> {
        catalog.require(leagueId)
        return apiService.fetchStandings(leagueId, season)
    }

    fun getLeagueMatches(leagueId: Int, season: Int): List<LeagueMatchDayDto> {
        catalog.require(leagueId)
        val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
        val logos = teamRepository.findAllById(
            matches.flatMap { listOfNotNull(it.homeTeamId, it.awayTeamId) }.toSet()
        ).associate { it.teamId to it.logoUrl }

        return matches
            .sortedWith(compareByDescending<MatchEntity> { it.matchDate }.thenByDescending { it.matchTime })
            .groupBy { it.matchDate }
            .map { (date, dayMatches) ->
                LeagueMatchDayDto(
                    date = date,
                    matches = dayMatches.map { m ->
                        MatchDto(
                            fixtureId = m.fixtureId,
                            homeTeam = m.homeTeam,
                            awayTeam = m.awayTeam,
                            homeTeamId = m.homeTeamId,
                            awayTeamId = m.awayTeamId,
                            matchDate = m.matchDate,
                            matchTime = m.matchTime,
                            score = m.score,
                            leagueId = m.leagueId,
                            season = m.season,
                            matchRound = m.matchRound,
                            stadium = m.stadium,
                            referee = m.referee,
                            homeLogoUrl = m.homeTeamId?.let { logos[it] },
                            awayLogoUrl = m.awayTeamId?.let { logos[it] }
                        )
                    }
                )
            }
    }

    // 득점/도움/공격 포인트/평점 TOP 10
    fun getLeaguePlayerStats(leagueId: Int, season: Int): LeaguePlayerStatsDto {
        val leagueName = catalog.require(leagueId).nameEn
        val rawStats = playerSeasonStatRepository.findBySeasonAndLeagueName(season, leagueName)

        // 선수별 중복 제거 (출장수 최대 기록 1개)
        val stats = rawStats.groupBy { it.playerId }
            .mapValues { (_, e) -> e.maxByOrNull { it.appearances }!! }
            .values.toList()

        val playerMap = playerRepository.findAllById(stats.map { it.playerId })
            .associateBy { it.playerId }

        fun rank(selector: (PlayerSeasonStatEntity) -> Double, minApps: Int = 1) =
            stats.filter { selector(it) > 0 && it.appearances >= minApps }
                .sortedByDescending { selector(it) }
                .take(10)
                .mapIndexed { i, s ->
                    PlayerRankDto(
                        rank = i + 1,
                        playerId = s.playerId,
                        playerName = playerMap[s.playerId]?.name ?: "",
                        photoUrl = playerMap[s.playerId]?.photoUrl,
                        value = selector(s),
                        appearances = s.appearances
                    )
                }

        return LeaguePlayerStatsDto(
            goalRanking = rank({ it.goals.toDouble() }),
            assistRanking = rank({ it.assists.toDouble() }),
            attackPointRanking = rank({ (it.goals + it.assists).toDouble() }),
            ratingRanking = rank({ it.rating ?: 0.0 }, minApps = 15)
        )
    }

    // 평균 평점/경기당 득점/경기당 실점/클린시트 랭킹
    fun getLeagueTeamStats(leagueId: Int, season: Int): LeagueTeamStatsDto {
        val leagueName = catalog.require(leagueId).nameEn
        val today = LocalDate.now(clock).toString()
        val matches = matchRepository.findByLeagueIdAndSeason(leagueId, season)
            .filter { it.matchDate <= today }

        data class TS(var played: Int = 0, var goalsFor: Int = 0, var goalsAgainst: Int = 0, var cleanSheets: Int = 0)

        val tsMap = mutableMapOf<String, TS>()
        matches.forEach { m ->
            // 종료된 경기만 (matchTime이 숫자나 "종료" 포함)
            val finished = m.matchTime.let { it.contains("종료") || it.any { c -> c.isDigit() } }
            if (!finished) return@forEach

            // "1 - 0", "1-0", "2:1" 등 다양한 형식 처리
            val parts = m.score.replace(" ", "").split("-", ":").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 2) return@forEach
            val hg = parts[0]
            val ag = parts[1]

            tsMap.getOrPut(m.homeTeam) { TS() }.also {
                it.played++; it.goalsFor += hg; it.goalsAgainst += ag
                if (ag == 0) it.cleanSheets++
            }
            tsMap.getOrPut(m.awayTeam) { TS() }.also {
                it.played++; it.goalsFor += ag; it.goalsAgainst += hg
                if (hg == 0) it.cleanSheets++
            }
        }

        val logoMap = teamRepository.findAll().associateBy { it.name }
        fun tr(name: String, value: Double, display: String): TeamRankDto {
            val t = logoMap[name]
            return TeamRankDto(t?.teamId, name, t?.logoUrl, value, display)
        }

        val valid = tsMap.filter { it.value.played > 0 }

        // 선수 평균 평점 (출장 5경기 이상만)
        val avgRating = playerSeasonStatRepository.findBySeasonAndLeagueName(season, leagueName)
            .filter { (it.rating ?: 0.0) > 0 && it.appearances >= 5 }
            .groupBy { it.teamName }
            .mapValues { (_, e) -> e.mapNotNull { it.rating }.average() }

        return LeagueTeamStatsDto(
            avgRatingRanking = avgRating
                .map { (n, r) -> tr(n, r, "%.2f".format(r)) }
                .sortedByDescending { it.value },
            goalsPerGameRanking = valid
                .map { (n, s) ->
                    val v = s.goalsFor.toDouble() / s.played
                    tr(n, v, "%.2f".format(v))
                }.sortedByDescending { it.value },
            concededPerGameRanking = valid
                .map { (n, s) ->
                    val v = s.goalsAgainst.toDouble() / s.played
                    tr(n, v, "%.2f".format(v))
                }.sortedBy { it.value },   // 낮은 순
            cleanSheetRanking = valid
                .map { (n, s) -> tr(n, s.cleanSheets.toDouble(), "${s.cleanSheets}회") }
                .sortedByDescending { it.value }
        )
    }
}
