package com.sscore.repository

import com.sscore.entity.TeamEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : JpaRepository<TeamEntity, Int> {
    @Query("SELECT t FROM TeamEntity t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY t.name ASC")
    fun searchByName(@Param("q") q: String, pageable: Pageable): List<TeamEntity>
}