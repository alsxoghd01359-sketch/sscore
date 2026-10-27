package com.sscore.repository

import com.sscore.entity.ChampionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChampionRepository : JpaRepository<ChampionEntity, Long> {
    fun findByTeamId(teamId: Int): List<ChampionEntity>
    fun existsByLeagueIdAndSeason(leagueId: Int, season: Int): Boolean
    fun existsByLeagueIdAndSeasonAndTeamId(leagueId: Int, season: Int, teamId: Int): Boolean
}