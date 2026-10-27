package com.sscore.repository

import com.sscore.entity.PlayerStatEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlayerStatRepository : JpaRepository<PlayerStatEntity, Long> {
    fun findByFixtureId(fixtureId: Long): List<PlayerStatEntity>
    fun findByFixtureIdAndPlayerId(fixtureId: Long, playerId: Long): PlayerStatEntity?
    fun findByPlayerId(playerId: Long): List<PlayerStatEntity>
    fun deleteByFixtureId(fixtureId: Long)
}