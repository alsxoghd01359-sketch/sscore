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

    fun findByLeagueIdAndSeason(leagueId: Int, season: Int): List<MatchEntity>

    fun findByFixtureId(fixtureId: Long): MatchEntity?

    fun findByMatchDate(matchDate: String): List<MatchEntity>

    fun findByMatchDateAndLeagueId(matchDate: String, leagueId: Int): List<MatchEntity>

    // 팀+시즌 경기 (빅5 리그만)
    @Query("""
        SELECT m FROM MatchEntity m
        WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId)
          AND m.season = :season
          AND m.leagueId IN (39, 140, 78, 135, 61)
        ORDER BY m.matchDate ASC, m.matchTime ASC
    """)
    fun findByTeamAndSeason(
        @Param("teamId") teamId: Int,
        @Param("season") season: Int
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