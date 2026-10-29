package com.sscore.team

import com.sscore.common.ApiException
import com.sscore.dto.PlayerRankDto
import com.sscore.dto.RecentMatchDto
import com.sscore.dto.SquadDto
import com.sscore.dto.TeamMatchesPageDto
import com.sscore.dto.TeamOverviewDto
import com.sscore.dto.TeamStatsDto
import com.sscore.dto.TrophyDto
import com.sscore.dto.toMatchDto
import com.sscore.entity.PlayerSeasonStatEntity
import com.sscore.entity.TeamEntity
import com.sscore.football.optionalExternal
import com.sscore.league.LeagueCatalog
import com.sscore.repository.ChampionRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.service.ApiFootballService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

data class TeamInfoDto(
    val teamId: Int,
    val name: String,
    val country: String?,
    val logoUrl: String?,
    val leagueId: Int?,
    val leagueName: String?,
    val nameKo: String? = null
)

// 팀 상세 화면(개요·경기·순위·통계·스쿼드)의 조회 로직
@Service
class TeamService(
    private val teams: TeamRepository,
    private val matches: MatchRepository,
    private val champions: ChampionRepository,
    private val players: PlayerRepository,
    private val playerSeasonStats: PlayerSeasonStatRepository,
    private val apiService: ApiFootballService,
    private val catalog: LeagueCatalog,
    private val aliases: TeamAliasRepository
) {
    // 없는 팀으로는 외부 API 호출이나 조회를 하지 않는다
    fun requireTeam(teamId: Int): TeamEntity = teams.findById(teamId).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "팀을 찾을 수 없습니다.")
    }

    // 팀이 뛰는 리그: 요청에서 받은 값이 있으면 그것(지원 리그여야 함), 없으면 가장 최근 시즌에 뛴 지원 리그
    private fun leagueOf(teamId: Int, requested: Int?): Int {
        if (requested != null) return catalog.require(requested).id
        return matches.findLeagueIdByTeamId(teamId).firstOrNull { catalog.isSupported(it) }
            ?: throw ApiException(HttpStatus.NOT_FOUND, "LEAGUE_NOT_FOUND", "이 팀의 리그를 찾을 수 없습니다.")
    }

    fun getInfo(teamId: Int): TeamInfoDto {
        val team = requireTeam(teamId)
        val leagueId = matches.findLeagueIdByTeamId(teamId).firstOrNull { catalog.isSupported(it) }
        val nameKo = aliases.findByTeamIdIn(listOf(teamId)).firstOrNull { it.primary }?.name
        return TeamInfoDto(team.teamId, team.name, team.country, team.logoUrl, leagueId, leagueId?.let { catalog.nameKo(it) }, nameKo)
    }

    fun getTrophies(teamId: Int): List<TrophyDto> {
        requireTeam(teamId)
        return trophiesOf(teamId)
    }

    // 챔피언스 기록(champions 테이블)을 리그별 우승 횟수로 묶는다
    private fun trophiesOf(teamId: Int): List<TrophyDto> =
        champions.findByTeamId(teamId)
            .groupBy { it.leagueId }
            .map { (leagueId, entries) ->
                val latest = entries.maxOf { it.season }
                TrophyDto(
                    league = catalog.nameKo(leagueId),
                    country = null,
                    winCount = entries.size,
                    lastWinSeason = "$latest/${(latest + 1).toString().takeLast(2)}"
                )
            }
            .sortedByDescending { it.winCount }

    fun getSquad(teamId: Int): SquadDto {
        requireTeam(teamId)
        val squad = apiService.fetchTeamSquad(teamId)

        // DB에서 국적 보완
        val dbMap = players.findAllById(squad.map { it.playerId }.toSet()).associateBy { it.playerId }
        val enriched = squad.map { p -> p.copy(nationality = p.nationality ?: dbMap[p.playerId]?.nationality) }

        fun group(pos: String) = enriched
            .filter { it.position.contains(pos, ignoreCase = true) }
            .sortedBy { it.number ?: 99 }

        return SquadDto(
            goalkeepers = group("Goalkeeper"),
            defenders = group("Defender"),
            midfielders = group("Midfielder"),
            attackers = group("Attacker")
        )
    }

    // 홈/원정 성적 + 평점/득점/도움 랭킹
    fun getStats(teamId: Int, leagueId: Int?, season: Int): TeamStatsDto {
        val team = requireTeam(teamId)
        val league = leagueOf(teamId, leagueId)

        val (home, away) = optionalExternal("홈/원정 성적(teamId=$teamId)", Pair(null, null)) {
            apiService.fetchTeamRecord(teamId, league, season)
        }

        // PL+CL 등 중복 기록이 있는 선수는 출장수가 가장 많은 기록(주 리그)만 사용 (선수 시즌 통계는 팀 이름 기준으로 저장됨)
        val stats = playerSeasonStats.findBySeasonAndTeamName(season, team.name)
            .groupBy { it.playerId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.appearances }!! }
            .values.toList()
        if (stats.isEmpty()) return TeamStatsDto(home, away, emptyList(), emptyList(), emptyList())

        val playerMap = players.findAllById(stats.map { it.playerId }.toSet()).associateBy { it.playerId }

        // 같은 값이면 같은 순위
        fun buildRanking(
            selector: (PlayerSeasonStatEntity) -> Double,
            minValue: Double = 0.0,
            minAppearances: Int = 1
        ): List<PlayerRankDto> {
            val sorted = stats
                .filter { selector(it) > minValue && it.appearances >= minAppearances }
                .sortedByDescending { selector(it) }
            var currentRank = 1
            return sorted.mapIndexed { idx, s ->
                if (idx > 0 && selector(s) != selector(sorted[idx - 1])) currentRank = idx + 1
                val p = playerMap[s.playerId]
                PlayerRankDto(
                    rank = currentRank,
                    playerId = s.playerId,
                    playerName = p?.name ?: "Unknown",
                    photoUrl = p?.photoUrl,
                    value = selector(s),
                    appearances = s.appearances
                )
            }
        }

        return TeamStatsDto(
            homeRecord = home,
            awayRecord = away,
            ratingRanking = buildRanking({ it.rating ?: 0.0 }, 0.0, minAppearances = 15),
            goalRanking = buildRanking({ it.goals.toDouble() }, minAppearances = 1),
            assistRanking = buildRanking({ it.assists.toDouble() }, minAppearances = 1)
        )
    }

    // 팀 경기 목록 (최신순, 페이지 단위)
    fun getMatches(teamId: Int, page: Int, size: Int): TeamMatchesPageDto {
        requireTeam(teamId)
        val pageable = PageRequest.of(
            page.coerceAtLeast(0), size.coerceIn(1, 50),
            Sort.by(Sort.Direction.DESC, "matchDate").and(Sort.by(Sort.Direction.DESC, "matchTime"))
        )
        val result = matches.findAllMatchesByTeam(teamId, pageable)
        return TeamMatchesPageDto(
            matches = result.content.map { it.toMatchDto() },
            hasMore = !result.isLast,
            currentPage = pageable.pageNumber,
            totalPages = result.totalPages
        )
    }

    // 최근 5경기 + 순위표(해당 팀 ±1) + 경기장 + 리그 트로피
    fun getOverview(teamId: Int, leagueId: Int?, season: Int): TeamOverviewDto {
        requireTeam(teamId)
        val league = leagueOf(teamId, leagueId)

        val recentMatches = matches.findRecentFinishedByTeam(teamId, PageRequest.of(0, 5)).map { m ->
            val parts = m.score.split("-")
            val homeScore = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val awayScore = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val isHome = m.homeTeamId == teamId
            val myScore = if (isHome) homeScore else awayScore
            val oppScore = if (isHome) awayScore else homeScore
            RecentMatchDto(
                fixtureId = m.fixtureId,
                homeTeamId = m.homeTeamId,
                awayTeamId = m.awayTeamId,
                homeTeam = m.homeTeam,
                awayTeam = m.awayTeam,
                score = m.score,
                result = when {
                    myScore > oppScore -> "W"
                    myScore == oppScore -> "D"
                    else -> "L"
                },
                matchDate = m.matchDate
            )
        }

        // 순위표/경기장은 부가 정보이므로 외부 API가 실패해도 개요 화면은 보여 준다
        val standings = optionalExternal("미니 순위표(leagueId=$league)", emptyList()) {
            apiService.fetchStandings(league, season)
        }
        val rank = standings.find { it.teamId == teamId }?.rank ?: 0
        val miniStandings = if (rank > 0) standings.filter { it.rank in (rank - 1)..(rank + 1) } else emptyList()
        val venue = optionalExternal("경기장 정보(teamId=$teamId)", null) { apiService.fetchTeamVenue(teamId) }

        return TeamOverviewDto(recentMatches, miniStandings, venue, trophiesOf(teamId))
    }
}
