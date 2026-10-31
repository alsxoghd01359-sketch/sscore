package com.sscore.match

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

// 끝난 경기의 한 팀 통계. 외부 API가 주지 않은 항목은 null이다.
@Entity
@Table(name = "match_team_stats")
class MatchTeamStat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "fixture_id", nullable = false) val fixtureId: Long,
    @Column(name = "team_id", nullable = false) val teamId: Int,
    @Column(name = "is_home", nullable = false) val home: Boolean,
    @Column(name = "possession") val possession: Int? = null,
    @Column(name = "shots_total") val shotsTotal: Int? = null,
    @Column(name = "shots_on_goal") val shotsOnGoal: Int? = null,
    @Column(name = "shots_off_goal") val shotsOffGoal: Int? = null,
    @Column(name = "shots_blocked") val shotsBlocked: Int? = null,
    @Column(name = "shots_inside_box") val shotsInsideBox: Int? = null,
    @Column(name = "shots_outside_box") val shotsOutsideBox: Int? = null,
    @Column(name = "corners") val corners: Int? = null,
    @Column(name = "fouls") val fouls: Int? = null,
    @Column(name = "offsides") val offsides: Int? = null,
    @Column(name = "yellow_cards") val yellowCards: Int? = null,
    @Column(name = "red_cards") val redCards: Int? = null,
    @Column(name = "saves") val saves: Int? = null,
    @Column(name = "passes_total") val passesTotal: Int? = null,
    @Column(name = "passes_accurate") val passesAccurate: Int? = null,
    @Column(name = "pass_pct") val passPct: Int? = null,
    @Column(name = "expected_goals", precision = 5, scale = 2) val expectedGoals: BigDecimal? = null
)

@Repository
interface MatchTeamStatRepository : JpaRepository<MatchTeamStat, Long> {
    fun findByFixtureId(fixtureId: Long): List<MatchTeamStat>
    fun existsByFixtureId(fixtureId: Long): Boolean

    @Modifying
    @Query("delete from MatchTeamStat s where s.fixtureId = :fixtureId")
    fun deleteByFixtureId(@Param("fixtureId") fixtureId: Long): Int
}

// 외부 API 경기 상세 응답의 "statistics"(팀별 항목 목록)를 팀 통계로 바꾼다.
// 응답에 통계가 없으면(옛 경기) 두 팀 모두 빈 값으로 만들어, 받은 적이 있다는 표시를 남긴다.
object TeamStatParser {
    fun parse(fixtureId: Long, homeTeamId: Int?, awayTeamId: Int?, details: Map<*, *>): List<MatchTeamStat> {
        val fromApi = (details["statistics"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { team ->
            val teamId = ((team["team"] as? Map<*, *>)?.get("id") as? Number)?.toInt() ?: return@mapNotNull null
            val values = (team["statistics"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                .associate { it["type"].toString() to it["value"] }
            build(fixtureId, teamId, teamId == homeTeamId, values)
        }
        if (fromApi.isNotEmpty()) return fromApi
        return listOfNotNull(
            homeTeamId?.let { build(fixtureId, it, true, emptyMap()) },
            awayTeamId?.let { build(fixtureId, it, false, emptyMap()) }
        )
    }

    private fun build(fixtureId: Long, teamId: Int, home: Boolean, v: Map<String, Any?>): MatchTeamStat {
        fun int(name: String) = number(v[name])?.toInt()
        return MatchTeamStat(
            fixtureId = fixtureId, teamId = teamId, home = home,
            possession = int("Ball Possession"),
            shotsTotal = int("Total Shots"), shotsOnGoal = int("Shots on Goal"), shotsOffGoal = int("Shots off Goal"),
            shotsBlocked = int("Blocked Shots"), shotsInsideBox = int("Shots insidebox"), shotsOutsideBox = int("Shots outsidebox"),
            corners = int("Corner Kicks"), fouls = int("Fouls"), offsides = int("Offsides"),
            yellowCards = int("Yellow Cards"), redCards = int("Red Cards"), saves = int("Goalkeeper Saves"),
            passesTotal = int("Total passes"), passesAccurate = int("Passes accurate"), passPct = int("Passes %"),
            expectedGoals = number(v["expected_goals"])?.let { BigDecimal.valueOf(it).setScale(2, java.math.RoundingMode.HALF_UP) }
        )
    }

    // 값은 숫자, "60%" 같은 글자, null 중 하나로 온다
    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.removeSuffix("%").trim().toDoubleOrNull()
        else -> null
    }
}
