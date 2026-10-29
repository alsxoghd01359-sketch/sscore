package com.sscore.repository

import com.sscore.entity.PlayerEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerRepository : JpaRepository<PlayerEntity, Long> {
    // 이름에 검색어가 들어간 선수. 검색어로 시작하는 이름을 먼저, 그다음 이름순. (utf8mb4_unicode_ci 라 악센트·대소문자를 구분하지 않는다)
    @Query("""
        SELECT p FROM PlayerEntity p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) AND p.team IS NOT NULL
        ORDER BY CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:q, '%')) THEN 0 ELSE 1 END, p.name ASC
    """)
    fun searchByName(@Param("q") q: String, pageable: Pageable): List<PlayerEntity>
}