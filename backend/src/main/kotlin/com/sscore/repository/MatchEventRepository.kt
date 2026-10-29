package com.sscore.repository

import com.sscore.entity.MatchEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchEventRepository : JpaRepository<MatchEventEntity, Long> {

    // 🌟 특정 경기의 실시간 이벤트를 조회하는 메서드
    fun findByFixtureId(fixtureId: Long): List<MatchEventEntity>

    fun findByFixtureIdIn(fixtureIds: Collection<Long>): List<MatchEventEntity>

    // 🌟 실시간으로 최신 이벤트를 덮어쓰기(Update) 전에 기존 데이터를 싹 지우는 핵심 메서드
    fun deleteByFixtureId(fixtureId: Long)
}