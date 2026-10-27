package com.sscore.repository

import com.sscore.entity.LeagueEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LeagueRepository : JpaRepository<LeagueEntity, Int> {
    fun findByLeagueIdIn(leagueIds: List<Int>): List<LeagueEntity>
    @Query("SELECT l FROM LeagueEntity l WHERE LOWER(l.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY l.name ASC")
    fun searchByName(@Param("q") q: String): List<LeagueEntity>
}