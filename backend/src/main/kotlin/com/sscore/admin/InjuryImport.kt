package com.sscore.admin

import com.sscore.league.LeagueCatalog
import com.sscore.match.Injury
import com.sscore.match.InjuryRepository
import com.sscore.service.ApiFootballService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

// 리그·시즌의 부상/결장 명단을 외부 API에서 받아 저장한다. 리그·시즌당 호출 1번이고, 다시 받으면 그 리그·시즌 것을 새로 바꾼다.
@Service
class InjuryImportService(
    private val api: ApiFootballService,
    private val injuries: InjuryRepository,
    private val catalog: LeagueCatalog,
    private val tx: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 저장한 건수를 돌려준다
    fun load(from: Int, to: Int): Int {
        var total = 0
        for (season in from..to) {
            for (leagueId in catalog.ids()) {
                val rows = parse(api.fetchInjuries(leagueId, season), leagueId, season)
                tx.executeWithoutResult {
                    injuries.deleteByLeagueIdAndSeason(leagueId, season)
                    injuries.saveAll(rows)
                }
                log.info("[Injuries] 리그={} 시즌={} {}건 저장", leagueId, season, rows.size)
                total += rows.size
            }
        }
        return total
    }

    // (경기, 선수)는 한 번만 저장한다. 응답에 같은 선수가 두 번 나오면 첫 번째만 쓴다.
    private fun parse(items: List<Map<*, *>>, leagueId: Int, season: Int): List<Injury> =
        items.mapNotNull { item ->
            val player = item["player"] as? Map<*, *> ?: return@mapNotNull null
            val team = item["team"] as? Map<*, *>
            val fixtureId = ((item["fixture"] as? Map<*, *>)?.get("id") as? Number)?.toLong() ?: return@mapNotNull null
            val playerId = (player["id"] as? Number)?.toLong() ?: return@mapNotNull null
            Injury(
                fixtureId = fixtureId, leagueId = leagueId, season = season,
                teamId = (team?.get("id") as? Number)?.toInt() ?: return@mapNotNull null,
                playerId = playerId,
                playerName = player["name"]?.toString()?.take(100) ?: "",
                type = player["type"]?.toString()?.take(40),
                reason = player["reason"]?.toString()?.take(100)
            )
        }.distinctBy { it.fixtureId to it.playerId }
}
