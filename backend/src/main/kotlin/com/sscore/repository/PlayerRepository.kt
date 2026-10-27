package com.sscore.repository

import com.sscore.entity.PlayerEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerRepository : JpaRepository<PlayerEntity, Long> {
    @Query("SELECT p FROM PlayerEntity p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) AND p.team IS NOT NULL ORDER BY p.name ASC")
    fun searchByName(@Param("q") q: String): List<PlayerEntity>
}