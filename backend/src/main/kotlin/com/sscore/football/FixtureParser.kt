package com.sscore.football

import com.sscore.dto.MatchDto
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// 외부 API의 fixture 항목을 MatchDto로 바꾼다. 필수 값이 없거나 날짜를 읽을 수 없는 항목은 null(건너뜀)이다.
object FixtureParser {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val KST = ZoneId.of("Asia/Seoul")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(item: Map<*, *>): MatchDto? {
        val fixture = item["fixture"] as? Map<*, *> ?: return null
        val teams = item["teams"] as? Map<*, *> ?: return null
        val league = item["league"] as? Map<*, *> ?: return null
        val home = teams["home"] as? Map<*, *> ?: return null
        val away = teams["away"] as? Map<*, *> ?: return null
        val status = fixture["status"] as? Map<*, *> ?: return null

        val fixtureId = (fixture["id"] as? Number)?.toLong() ?: return null
        val homeId = (home["id"] as? Number)?.toInt() ?: return null
        val awayId = (away["id"] as? Number)?.toInt() ?: return null
        val homeName = home["name"]?.toString() ?: return null
        val awayName = away["name"]?.toString() ?: return null
        val leagueId = (league["id"] as? Number)?.toInt() ?: return null
        val season = (league["season"] as? Number)?.toInt() ?: return null
        val elapsed = (status["elapsed"] as? Number)?.toInt()
        val (date, time) = toKst(fixture["date"]?.toString(), status["short"]?.toString().orEmpty(), elapsed) ?: run {
            log.warn("경기 날짜를 읽을 수 없어 건너뜁니다: fixture={}", fixtureId)
            return null
        }

        val goals = item["goals"] as? Map<*, *>
        val score = if (goals?.get("home") != null && goals["away"] != null) "${goals["home"]}-${goals["away"]}" else "0-0"

        return MatchDto(
            fixtureId = fixtureId,
            homeTeam = homeName,
            awayTeam = awayName,
            homeTeamId = homeId,
            awayTeamId = awayId,
            matchDate = date,
            matchTime = time,
            score = score,
            season = season,
            leagueId = leagueId,
            matchRound = league["round"]?.toString(),
            stadium = (fixture["venue"] as? Map<*, *>)?.get("name")?.toString(),
            referee = fixture["referee"]?.toString()
        )
    }

    // UTC 시각을 한국 시간의 (날짜, "시각 (상태)") 로 바꾼다
    private fun toKst(iso: String?, statusShort: String, elapsed: Int?): Pair<String, String>? {
        if (iso.isNullOrBlank()) return null
        val kst = try {
            ZonedDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).withZoneSameInstant(KST)
        } catch (e: Exception) {
            return null
        }
        val time = kst.format(TIME_FORMAT)
        val label = when (statusShort) {
            "NS", "TBD" -> time
            "FT", "AET", "PEN" -> "$time (종료)"
            "1H", "2H", "ET" -> if (elapsed != null) "$time (진행중 ${elapsed}')" else "$time (진행중)"
            "HT", "BT", "P", "LIVE" -> "$time (진행중)"
            "CANC", "PSTP" -> "$time (취소)"
            else -> "$time ($statusShort)"
        }
        return kst.format(DATE_FORMAT) to label
    }
}
