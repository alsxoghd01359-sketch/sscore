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

// 한 경기에서 결장했거나 출전이 불투명했던 선수 (부상 등). 외부 API는 20/21 시즌부터 준다.
@Entity
@Table(name = "injuries")
class Injury(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "fixture_id", nullable = false) val fixtureId: Long,
    @Column(name = "league_id", nullable = false) val leagueId: Int,
    @Column(name = "season", nullable = false) val season: Int,
    @Column(name = "team_id", nullable = false) val teamId: Int,
    @Column(name = "player_id", nullable = false) val playerId: Long,
    @Column(name = "player_name", nullable = false, length = 100) val playerName: String,
    @Column(name = "type", length = 40) val type: String?,
    @Column(name = "reason", length = 100) val reason: String?
)

@Repository
interface InjuryRepository : JpaRepository<Injury, Long> {
    fun findByFixtureId(fixtureId: Long): List<Injury>

    @Modifying
    @Query("delete from Injury i where i.leagueId = :leagueId and i.season = :season")
    fun deleteByLeagueIdAndSeason(@Param("leagueId") leagueId: Int, @Param("season") season: Int): Int
}
