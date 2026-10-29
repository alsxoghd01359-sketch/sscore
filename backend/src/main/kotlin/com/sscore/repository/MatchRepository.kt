package com.sscore.repository

import com.sscore.entity.MatchEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MatchRepository : JpaRepository<MatchEntity, Long> {
    fun findByMatchDateIn(dates: Collection<String>): List<MatchEntity>


    fun findByLeagueIdAndSeason(leagueId: Int, season: Int): List<MatchEntity>

    fun findByFixtureId(fixtureId: Long): MatchEntity?

    fun findByFixtureIdIn(fixtureIds: Collection<Long>): List<MatchEntity>

    fun findByMatchDateInAndLeagueIdIn(matchDates: Collection<String>, leagueIds: Collection<Int>): List<MatchEntity>

    fun existsBySeason(season: Int): Boolean

    // 팀이 지원 리그에서 뛴 (리그, 시즌) 목록 (홈 경기 기준)
    @Query("""
        SELECT new com.sscore.repository.TeamLeagueSeason(m.homeTeamId, m.leagueId, m.season)
        FROM MatchEntity m
        WHERE m.homeTeamId IN :teamIds AND m.leagueId IN :leagueIds
        GROUP BY m.homeTeamId, m.leagueId, m.season
    """)
    fun teamLeagueSeasons(
        @Param("teamIds") teamIds: Collection<Int>,
        @Param("leagueIds") leagueIds: Collection<Int>
    ): List<TeamLeagueSeason>

    @Query("SELECT DISTINCT m.season FROM MatchEntity m WHERE m.leagueId = :leagueId ORDER BY m.season DESC")
    fun seasonsOfLeague(@Param("leagueId") leagueId: Int): List<Int>

    // 데이터가 있는 가장 최근 시즌 (기준 시즌 이하)
    @Query("SELECT MAX(m.season) FROM MatchEntity m WHERE m.season <= :season")
    fun latestSeasonUpTo(@Param("season") season: Int): Int?

    fun findByMatchDate(matchDate: String): List<MatchEntity>

    fun findByMatchDateAndLeagueId(matchDate: String, leagueId: Int): List<MatchEntity>

    // 팀+시즌 경기 (지원 리그만)
    @Query("""
        SELECT m FROM MatchEntity m
        WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
          AND m.season = :season
          AND m.leagueId IN :leagueIds
        ORDER BY m.matchDate ASC, m.matchTime ASC
    """)
    fun findByTeamAndSeason(
        @Param("teamId") teamId: Int,
        @Param("season") season: Int,
        @Param("leagueIds") leagueIds: Collection<Int>
    ): List<MatchEntity>

    // 팀의 리그 ID 조회 (최신 시즌 기준)
    @Query("""
        SELECT m.leagueId FROM MatchEntity m
        WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
        ORDER BY m.season DESC
    """)
    fun findLeagueIdByTeamId(@Param("teamId") teamId: Int): List<Int>

    // 최근 종료 경기 (개요 탭)
    @Query("""
        SELECT m FROM MatchEntity m
        WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
          AND m.matchTime LIKE '%종료%'
        ORDER BY m.matchDate DESC, m.matchTime DESC
    """)
    fun findRecentFinishedByTeam(
        @Param("teamId") teamId: Int,
        pageable: Pageable
    ): List<MatchEntity>

    // 팀 전체 경기 페이지네이션
    @Query("""
        SELECT m FROM MatchEntity m
        WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
    """)
    fun findAllMatchesByTeam(
        @Param("teamId") teamId: Int,
        pageable: Pageable
    ): Page<MatchEntity>
}

data class TeamLeagueSeason(val teamId: Int, val leagueId: Int, val season: Int)
