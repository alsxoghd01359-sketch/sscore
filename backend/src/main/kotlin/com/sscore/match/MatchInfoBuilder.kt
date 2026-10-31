package com.sscore.match

import com.sscore.dto.MatchEventDto
import com.sscore.dto.MatchInfoResponseDto
import com.sscore.dto.MatchStatItemDto
import com.sscore.dto.MomPlayerDto
import com.sscore.entity.MatchEntity
import com.sscore.entity.MatchEventEntity
import com.sscore.entity.PlayerStatEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class BuiltMatchInfo(
    val info: MatchInfoResponseDto,
    val playerStats: List<PlayerStatEntity>,   // 선수별 경기 기록 (평점 포함)
    val events: List<MatchEventEntity>,        // 타임라인
    val finished: Boolean,                     // 외부 API가 경기가 끝났다고 알려 줬는가
    val referee: String?,                      // 외부 API가 알려 준 주심 (없으면 null)
    val teamStats: List<MatchTeamStat> = emptyList()   // 팀별 통계 (승부예측 학습용)
)

// 외부 API의 경기 상세 응답을 경기 정보(타임라인·통계·평점 상위 선수)로 바꾼다. 저장은 하지 않는다.
object MatchInfoBuilder {
    private val FINISHED_STATUS = setOf("FT", "AET", "PEN")
    private val KST = ZoneId.of("Asia/Seoul")

    @Suppress("UNCHECKED_CAST")
    fun build(
        fixtureId: Long,
        match: MatchEntity?,
        details: Map<String, Any>?,
        dbPlayerStats: () -> List<PlayerStatEntity>
    ): BuiltMatchInfo {
        var refereeName = match?.referee
        var fetchedRefereeName: String? = null
        var bestPlayerId = 0L
        var bestPlayerName = ""
        var bestPlayerTeam = ""
        var highestRating = 0.0
        var foundMom = false
        var kstFullDateString = "일시 정보 없음"
        var finished = false

        val matchEvents = mutableListOf<MatchEventDto>()
        val eventEntities = mutableListOf<MatchEventEntity>()
        val statList = mutableListOf<MatchStatItemDto>()
        val homeStatsMap = mutableMapOf<String, String>()
        val awayStatsMap = mutableMapOf<String, String>()

        var hKeyPasses = 0; var aKeyPasses = 0
        var hTouches = 0; var aTouches = 0
        var hTackles = 0; var aTackles = 0
        var hInterceptions = 0; var aInterceptions = 0
        var hClearances = 0; var aClearances = 0
        var hDuelsTotal = 0; var hDuelsWon = 0
        var aDuelsTotal = 0; var aDuelsWon = 0
        var hDribbles = 0; var aDribbles = 0

        val homePlayers = mutableListOf<MomPlayerDto>()
        val playerRatings = mutableMapOf<Long, Double>()
        val awayPlayers = mutableListOf<MomPlayerDto>()
        val playerStatsToSave = mutableListOf<PlayerStatEntity>()

        if (details != null) {
            val fixtureMap = details["fixture"] as? Map<*, *>
            finished = (fixtureMap?.get("status") as? Map<*, *>)?.get("short")?.toString() in FINISHED_STATUS

            val fetchedReferee = fixtureMap?.get("referee")?.toString()
            if (!fetchedReferee.isNullOrBlank() && fetchedReferee != "null") {
                refereeName = fetchedReferee
                fetchedRefereeName = fetchedReferee
            }

            val fullDateStr = fixtureMap?.get("date")?.toString()
            if (!fullDateStr.isNullOrBlank() && fullDateStr != "null") {
                kstFullDateString = try {
                    val kst = ZonedDateTime.parse(fullDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).withZoneSameInstant(KST)
                    "${kst.year}년 ${kst.monthValue}월 ${kst.dayOfMonth}일 ${kst.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } catch (e: Exception) {
                    fullDateStr
                }
            }

            (details["statistics"] as? List<Map<*, *>>)?.forEach { teamStatMap ->
                val tId = (teamStatMap["team"] as? Map<*, *>)?.get("id")?.toString()?.toIntOrNull()
                val sArray = teamStatMap["statistics"] as? List<Map<*, *>> ?: emptyList()
                val targetMap = if (tId == match?.homeTeamId) homeStatsMap else awayStatsMap
                sArray.forEach { s ->
                    val type = s["type"]?.toString() ?: ""
                    val value = s["value"]?.toString()?.replace("%", "") ?: "0"
                    targetMap[type] = if (value != "null") value else "0"
                }
            }

            val playersArray = details["players"] as? List<Map<*, *>>
            if (playersArray != null) {
                for (teamData in playersArray) {
                    val teamMap = teamData["team"] as? Map<*, *>
                    val tId = (teamMap?.get("id") as? Number)?.toInt() ?: 0
                    val isHome = tId == match?.homeTeamId
                    val teamName = teamMap?.get("name")?.toString() ?: "알 수 없는 팀"

                    val squadArray = teamData["players"] as? List<Map<*, *>> ?: continue
                    for (playerData in squadArray) {
                        val playerMap = playerData["player"] as? Map<*, *>
                        val playerId = (playerMap?.get("id") as? Number)?.toLong() ?: 0L
                        val pName = playerMap?.get("name")?.toString() ?: ""

                        val firstStat = (playerData["statistics"] as? List<Map<*, *>>)?.firstOrNull()
                        val gamesMap = firstStat?.get("games") as? Map<*, *>
                        val passesMap = firstStat?.get("passes") as? Map<*, *>
                        val tacklesMap = firstStat?.get("tackles") as? Map<*, *>
                        val duelsMap = firstStat?.get("duels") as? Map<*, *>
                        val dribblesMap = firstStat?.get("dribbles") as? Map<*, *>
                        val shotsMap = firstStat?.get("shots") as? Map<*, *>
                        val goalsMap = firstStat?.get("goals") as? Map<*, *>
                        val foulsMap = firstStat?.get("fouls") as? Map<*, *>

                        val rating = gamesMap?.get("rating")?.toString()?.toDoubleOrNull() ?: 0.0
                        val minutes = (gamesMap?.get("minutes") as? Number)?.toInt() ?: 0
                        val position = gamesMap?.get("position")?.toString()

                        if (rating > highestRating) {
                            highestRating = rating
                            bestPlayerId = playerId
                            bestPlayerName = pName
                            bestPlayerTeam = teamName
                            foundMom = true
                        }

                        if (rating > 0) playerRatings[playerId] = rating

                        val playerDto = MomPlayerDto(
                            playerId = playerId, name = pName, teamName = teamName,
                            photoUrl = "https://media.api-sports.io/football/players/$playerId.png", rating = rating
                        )

                        val kp = (passesMap?.get("key") as? Number)?.toInt() ?: 0
                        val touches = (passesMap?.get("total") as? Number)?.toInt() ?: 0
                        val tack = (tacklesMap?.get("total") as? Number)?.toInt() ?: 0
                        val inter = (tacklesMap?.get("interceptions") as? Number)?.toInt() ?: 0
                        val clear = (tacklesMap?.get("blocks") as? Number)?.toInt() ?: 0
                        val dTot = (duelsMap?.get("total") as? Number)?.toInt() ?: 0
                        val dWon = (duelsMap?.get("won") as? Number)?.toInt() ?: 0
                        val drb = (dribblesMap?.get("success") as? Number)?.toInt() ?: 0

                        playerStatsToSave.add(
                            PlayerStatEntity(
                                fixtureId = fixtureId,
                                teamId = tId,
                                playerId = playerId,
                                playerName = pName,
                                rating = if (rating > 0) rating else null,
                                position = position,
                                minutesPlayed = if (minutes > 0) minutes else null,
                                goals = (goalsMap?.get("total") as? Number)?.toInt() ?: 0,
                                assists = (goalsMap?.get("assists") as? Number)?.toInt() ?: 0,
                                xg = shotsMap?.get("xg")?.toString()?.toDoubleOrNull(),
                                passesTotal = touches,
                                passesAccuracy = passesMap?.get("accuracy")?.toString()?.toDoubleOrNull()?.toInt() ?: 0,
                                passesKey = kp,
                                shotsTotal = (shotsMap?.get("total") as? Number)?.toInt() ?: 0,
                                shotsOnTarget = (shotsMap?.get("on") as? Number)?.toInt() ?: 0,
                                shotsOffTarget = 0,
                                ballTouches = touches,
                                turnovers = 0,
                                tacklesTotal = tack,
                                tacklesBlocks = clear,
                                tacklesInterceptions = inter,
                                duelsTotal = dTot,
                                duelsWon = dWon,
                                foulsDrawn = (foulsMap?.get("drawn") as? Number)?.toInt() ?: 0,
                                foulsCommitted = (foulsMap?.get("committed") as? Number)?.toInt() ?: 0,
                                goalkeeperSaves = (goalsMap?.get("saves") as? Number)?.toInt() ?: 0,
                                goalkeeperConceded = (goalsMap?.get("conceded") as? Number)?.toInt() ?: 0,
                                goalkeeperSavesInsideBox = 0,
                                goalkeeperPunches = 0
                            )
                        )

                        if (isHome) {
                            hKeyPasses += kp; hTouches += touches; hTackles += tack; hInterceptions += inter; hClearances += clear
                            hDuelsTotal += dTot; hDuelsWon += dWon; hDribbles += drb
                            if (rating > 0) homePlayers.add(playerDto)
                        } else {
                            aKeyPasses += kp; aTouches += touches; aTackles += tack; aInterceptions += inter; aClearances += clear
                            aDuelsTotal += dTot; aDuelsWon += dWon; aDribbles += drb
                            if (rating > 0) awayPlayers.add(playerDto)
                        }
                    }
                }
            }

            (details["events"] as? List<Map<*, *>>)?.forEach { eventMap ->
                val timeMap = eventMap["time"] as? Map<*, *>
                val teamMap = eventMap["team"] as? Map<*, *>
                val playerMap = eventMap["player"] as? Map<*, *>
                val assistMap = eventMap["assist"] as? Map<*, *>
                val time = (timeMap?.get("elapsed") as? Number)?.toInt() ?: 0
                val extra = (timeMap?.get("extra") as? Number)?.toInt()
                val teamId = (teamMap?.get("id") as? Number)?.toInt() ?: 0
                val teamName = teamMap?.get("name")?.toString() ?: ""
                val type = eventMap["type"]?.toString() ?: ""
                val detail = eventMap["detail"]?.toString()
                val playerName = playerMap?.get("name")?.toString()
                val assistName = assistMap?.get("name")?.toString()

                matchEvents.add(MatchEventDto(time, extra, teamId, teamName, type, detail, playerName, assistName))
                eventEntities.add(MatchEventEntity(fixtureId, time, extra, teamId, teamName, type, detail, playerName, assistName))
            }
        }

        fun addStat(cat: String, name: String, hVal: String, aVal: String) {
            statList.add(MatchStatItemDto(cat, name, hVal, aVal))
        }

        addStat("OVERALL", "점유율", homeStatsMap["Ball Possession"] ?: "50", awayStatsMap["Ball Possession"] ?: "50")
        addStat("OVERALL", "전체 슈팅수", homeStatsMap["Total Shots"] ?: "0", awayStatsMap["Total Shots"] ?: "0")
        addStat("OVERALL", "유효 슈팅수", homeStatsMap["Shots on Goal"] ?: "0", awayStatsMap["Shots on Goal"] ?: "0")
        addStat("OVERALL", "상대편 박스 터치", hTouches.toString(), aTouches.toString())

        val hXg = homeStatsMap["expected_goals"] ?: homeStatsMap["Expected Goals"] ?: "-"
        val aXg = awayStatsMap["expected_goals"] ?: awayStatsMap["Expected Goals"] ?: "-"
        if (hXg != "-" || aXg != "-") addStat("OVERALL", "xG", hXg, aXg)

        val hPassTot = homeStatsMap["Total passes"] ?: "0"
        val hPassAcc = homeStatsMap["Passes accurate"] ?: "0"
        val hPassPct = homeStatsMap["Passes %"] ?: "0"
        val aPassTot = awayStatsMap["Total passes"] ?: "0"
        val aPassAcc = awayStatsMap["Passes accurate"] ?: "0"
        val aPassPct = awayStatsMap["Passes %"] ?: "0"
        addStat("OVERALL", "패스 성공률", "$hPassAcc ($hPassPct%)", "$aPassAcc ($aPassPct%)")
        addStat("OVERALL", "반칙수", homeStatsMap["Fouls"] ?: "0", awayStatsMap["Fouls"] ?: "0")
        addStat("OVERALL", "오프사이드", homeStatsMap["Offsides"] ?: "0", awayStatsMap["Offsides"] ?: "0")
        addStat("OVERALL", "코너킥", homeStatsMap["Corner Kicks"] ?: "0", awayStatsMap["Corner Kicks"] ?: "0")

        addStat("PASSES", "총 패스 횟수", hPassTot, aPassTot)
        addStat("PASSES", "성공 횟수", hPassAcc, aPassAcc)
        if (hKeyPasses > 0 || aKeyPasses > 0) addStat("PASSES", "키 패스", hKeyPasses.toString(), aKeyPasses.toString())

        addStat("DEFENSE", "태클 횟수", hTackles.toString(), aTackles.toString())
        addStat("DEFENSE", "가로채기 횟수", hInterceptions.toString(), aInterceptions.toString())
        addStat("DEFENSE", "걷어내기 횟수", hClearances.toString(), aClearances.toString())
        addStat("DEFENSE", "골키퍼 선방횟수", homeStatsMap["Goalkeeper Saves"] ?: "0", awayStatsMap["Goalkeeper Saves"] ?: "0")

        addStat("DUELS", "볼 경합 성공수", "$hDuelsWon / $hDuelsTotal", "$aDuelsWon / $aDuelsTotal")
        addStat("DUELS", "드리블 성공수", hDribbles.toString(), aDribbles.toString())

        val homeTop5 = homePlayers.sortedByDescending { it.rating }.take(5)
        val awayTop5 = awayPlayers.sortedByDescending { it.rating }.take(5)

        // 외부 응답에 평점이 없으면 이미 저장된 선수 기록에서 최고 평점 선수를 찾는다
        if (!foundMom) {
            val dbBest = dbPlayerStats().maxByOrNull { it.rating ?: 0.0 }
            if (dbBest != null && (dbBest.rating ?: 0.0) > 0.0) {
                bestPlayerId = dbBest.playerId
                bestPlayerName = dbBest.playerName
                bestPlayerTeam = when (dbBest.teamId) {
                    match?.homeTeamId -> match?.homeTeam ?: "알 수 없는 팀"
                    match?.awayTeamId -> match?.awayTeam ?: "알 수 없는 팀"
                    else -> "알 수 없는 팀"
                }
                highestRating = dbBest.rating ?: 0.0
                foundMom = true
            }
        }

        val momPlayer = if (foundMom) {
            MomPlayerDto(
                playerId = bestPlayerId,
                name = bestPlayerName,
                teamName = bestPlayerTeam,
                photoUrl = "https://media.api-sports.io/football/players/$bestPlayerId.png",
                rating = Math.round(highestRating * 10) / 10.0
            )
        } else null

        val info = MatchInfoResponseDto(
            fixtureId = fixtureId,
            momPlayer = momPlayer,
            referee = if (!refereeName.isNullOrBlank() && refereeName != "null") refereeName else "심판 배정 전",
            matchFullDate = kstFullDateString,
            events = matchEvents,
            statistics = statList,
            homeTopPlayers = homeTop5,
            awayTopPlayers = awayTop5,
            playerRatings = playerRatings
        )
        val teamStats = if (details != null) TeamStatParser.parse(fixtureId, match?.homeTeamId, match?.awayTeamId, details) else emptyList()
        return BuiltMatchInfo(info, playerStatsToSave, eventEntities, finished, fetchedRefereeName, teamStats)
    }
}
