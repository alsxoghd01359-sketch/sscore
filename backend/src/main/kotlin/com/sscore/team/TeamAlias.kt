package com.sscore.team

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

// 팀 한글 이름. is_primary 인 이름이 화면에 보이는 한글 이름이고, 나머지는 검색에만 쓰는 별칭이다.
@Entity
@Table(name = "team_aliases")
class TeamAlias(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "team_id", nullable = false)
    val teamId: Int,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(name = "is_primary", nullable = false)
    val primary: Boolean = false
)

@Repository
interface TeamAliasRepository : JpaRepository<TeamAlias, Long> {
    @Query("SELECT a FROM TeamAlias a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun searchByName(@Param("q") q: String): List<TeamAlias>

    fun findByTeamIdIn(teamIds: Collection<Int>): List<TeamAlias>
}
