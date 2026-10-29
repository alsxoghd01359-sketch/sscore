package com.sscore.league

import com.sscore.common.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

data class LeagueMeta(val id: Int, val nameKo: String, val nameEn: String, val country: String)

// 서비스가 지원하는 리그 목록. 기본은 5대 리그이고, 리그를 늘리려면 설정 sscore.football.league-ids(쉼표로 구분)에 리그 ID를 추가한다.
// 아래 KNOWN에 없는 ID는 이름이 "리그 {ID}"로 표시되므로, 이름을 제대로 보이려면 KNOWN에도 한 줄 추가한다.
@Component
class LeagueCatalog(
    @Value("\${sscore.football.league-ids:39,140,78,135,61}") idsCsv: String
) {
    private companion object {
        val KNOWN = listOf(
            LeagueMeta(39, "프리미어리그", "Premier League", "England"),
            LeagueMeta(140, "라리가", "La Liga", "Spain"),
            LeagueMeta(78, "분데스리가", "Bundesliga", "Germany"),
            LeagueMeta(135, "세리에 A", "Serie A", "Italy"),
            LeagueMeta(61, "리그 1", "Ligue 1", "France")
        )
    }

    private val supported: List<LeagueMeta> = idsCsv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
        .map { id -> KNOWN.find { it.id == id } ?: LeagueMeta(id, "리그 $id", "", "") }

    fun all(): List<LeagueMeta> = supported

    fun ids(): List<Int> = supported.map { it.id }

    fun isSupported(leagueId: Int): Boolean = supported.any { it.id == leagueId }

    // 지원하지 않는 리그로는 외부 API 호출이나 조회를 하지 않는다
    fun require(leagueId: Int): LeagueMeta = supported.find { it.id == leagueId }
        ?: throw ApiException(HttpStatus.NOT_FOUND, "LEAGUE_NOT_FOUND", "지원하지 않는 리그입니다.")

    fun nameKo(leagueId: Int): String = supported.find { it.id == leagueId }?.nameKo ?: "리그 $leagueId"

    // player_season_stats.league_name 에 저장된 영어 리그 이름
    fun nameEn(leagueId: Int): String = supported.find { it.id == leagueId }?.nameEn.orEmpty()

    fun logoUrl(leagueId: Int): String = "https://media.api-sports.io/football/leagues/$leagueId.png"
}
