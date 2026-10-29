package com.sscore.search

import com.sscore.dto.SearchResultDto
import com.sscore.league.LeagueCatalog
import com.sscore.repository.LeagueRepository
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.repository.PlayerSeasonStatRepository
import com.sscore.repository.TeamRepository
import com.sscore.team.TeamAliasRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

// 선수·팀·리그를 한 번에 검색한다. 영어 이름은 대소문자와 악센트를 구분하지 않고(ö = o), 팀과 리그는 한글 이름으로도 찾는다.
@Service
class SearchService(
    private val players: PlayerRepository,
    private val teams: TeamRepository,
    private val leagues: LeagueRepository,
    private val aliases: TeamAliasRepository,
    private val matches: MatchRepository,
    private val seasonStats: PlayerSeasonStatRepository,
    private val catalog: LeagueCatalog
) {
    private companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 50
        const val PLAYER_LIMIT = 10
        const val TEAM_LIMIT = 5
    }

    // 리그 순서(지원 리그 목록의 순서)로 정렬한다. 지원하지 않는 리그는 맨 뒤.
    private fun orderOf(leagueId: Int?): Int = catalog.ids().indexOf(leagueId).let { if (it >= 0) it + 1 else 99 }

    fun search(query: String): List<SearchResultDto> {
        val q = query.trim().take(MAX_LENGTH)
        if (q.length < MIN_LENGTH) return emptyList()

        // 1. 선수 (이름이 검색어로 시작하는 선수를 먼저)
        val foundPlayers = players.searchByName(q, PageRequest.of(0, PLAYER_LIMIT))
        val latestStat = seasonStats.findByPlayerIdIn(foundPlayers.map { it.playerId })
            .groupBy { it.playerId }
            .mapValues { (_, list) -> list.maxByOrNull { it.season }!! }

        // 2. 팀: 영어 이름 + 한글 이름/별칭, 그리고 찾은 선수의 소속팀
        val directTeams = teams.searchByName(q, PageRequest.of(0, TEAM_LIMIT))
        val aliasTeams = aliases.searchByName(q).map { it.teamId }.distinct().take(TEAM_LIMIT).let { teams.findAllById(it) }
        // 선수의 소속팀은 가장 최근 시즌 기록의 팀을 우선한다(선수 테이블의 팀은 옛 정보일 수 있다)
        val teamsByName = teams.findAll().associateBy { it.name }
        val playerTeams = foundPlayers.mapNotNull { p -> latestStat[p.playerId]?.teamName?.let { teamsByName[it] } ?: p.team }
        val teamList = (playerTeams + directTeams + aliasTeams).distinctBy { it.teamId }

        val leagueOfTeam = latestLeagueByTeam(teamList.map { it.teamId })
        val koreanNames = aliases.findByTeamIdIn(teamList.map { it.teamId }).filter { it.primary }.associate { it.teamId to it.name }

        val playerResults = foundPlayers.map { p ->
            val stat = latestStat[p.playerId]
            val leagueId = stat?.leagueName?.let { name -> catalog.all().find { it.nameEn == name }?.id }
            SearchResultDto(
                "PLAYER", p.playerId, p.name, stat?.teamName ?: p.team?.name, p.photoUrl, null, orderOf(leagueId)
            )
        }.sortedBy { it.sortOrder }

        val teamResults = teamList.map { t ->
            val leagueId = leagueOfTeam[t.teamId]
            SearchResultDto(
                "TEAM", t.teamId.toLong(), t.name, leagueId?.let { catalog.nameKo(it) }, t.logoUrl, leagueId,
                orderOf(leagueId), koreanNames[t.teamId]
            )
        }.sortedBy { it.sortOrder }

        // 3. 리그: 영어·한글 이름 (지원하는 리그만)
        val needle = q.lowercase()
        val leagueResults = catalog.all()
            .filter { it.nameEn.lowercase().contains(needle) || it.nameKo.lowercase().contains(needle) }
            .map { meta ->
                val entity = leagues.findById(meta.id).orElse(null)
                SearchResultDto(
                    "LEAGUE", meta.id.toLong(), entity?.name ?: meta.nameEn.ifEmpty { meta.nameKo },
                    entity?.country ?: meta.country.ifEmpty { null }, entity?.logoUrl ?: catalog.logoUrl(meta.id), meta.id,
                    orderOf(meta.id), meta.nameKo
                )
            }

        return playerResults + teamResults + leagueResults
    }

    // 팀이 가장 최근 시즌에 뛴 지원 리그를 한 번에 구한다
    private fun latestLeagueByTeam(teamIds: List<Int>): Map<Int, Int> {
        if (teamIds.isEmpty()) return emptyMap()
        val rows = matches.teamLeagueSeasons(teamIds, catalog.ids())
        return rows.groupBy { it.teamId }.mapValues { (_, list) -> list.maxByOrNull { it.season }!!.leagueId }
    }
}
