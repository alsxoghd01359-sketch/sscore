package com.sscore.repository

import com.sscore.entity.PlayerSeasonStatEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlayerSeasonStatRepository : JpaRepository<PlayerSeasonStatEntity, Long> {
    fun findBySeasonAndLeagueName(season: Int, leagueName: String): List<PlayerSeasonStatEntity>
    fun findByPlayerIdOrderBySeasonDesc(playerId: Long): List<PlayerSeasonStatEntity>
    fun existsByPlayerIdAndSeasonAndTeamName(playerId: Long, season: Int, teamName: String): Boolean
    // 여러 리그 기록 중 출장수가 가장 많은 것(주 리그) 반환
    fun findFirstByPlayerIdAndSeasonAndTeamNameOrderByAppearancesDesc(playerId: Long, season: Int, teamName: String): PlayerSeasonStatEntity?
    fun findBySeasonAndTeamName(season: Int, teamName: String): List<PlayerSeasonStatEntity>
}