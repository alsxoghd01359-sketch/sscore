package com.sscore.follow

import com.sscore.common.ApiException
import com.sscore.repository.PlayerRepository
import com.sscore.repository.TeamRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDateTime

enum class FollowType { TEAM, PLAYER }

// 회원이 팔로우한 팀/선수 한 건. (회원, 종류, 대상)은 유일하다.
@Entity
@Table(name = "member_follows")
class Follow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "member_id", nullable = false)
    val memberId: Int,

    @Column(name = "target_type", nullable = false, length = 10)
    val targetType: String,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    // 이 대상의 알림을 받을지 (알림 종류 설정과 함께 모두 켜져 있어야 알림이 나간다)
    @Column(name = "notify", nullable = false)
    var notify: Boolean = true
)

@Repository
interface FollowRepository : JpaRepository<Follow, Long> {
    fun findByMemberIdOrderByIdAsc(memberId: Int): List<Follow>
    fun findByTargetTypeAndTargetIdIn(targetType: String, targetIds: Collection<Long>): List<Follow>
    fun findByMemberIdAndTargetTypeAndTargetId(memberId: Int, targetType: String, targetId: Long): Follow?
    fun existsByMemberIdAndTargetTypeAndTargetId(memberId: Int, targetType: String, targetId: Long): Boolean
    fun deleteByMemberIdAndTargetTypeAndTargetId(memberId: Int, targetType: String, targetId: Long): Long
}

data class FollowedTeam(val id: Int, val name: String, val logoUrl: String?)
data class FollowedPlayer(val id: Long, val name: String, val photoUrl: String?)
data class FollowList(val teams: List<FollowedTeam>, val players: List<FollowedPlayer>)

// 팀·선수 팔로우. 추가/해제는 몇 번을 눌러도 같은 결과(이미 팔로우 중이면 그대로, 없으면 그대로)다.
@Service
class FollowService(
    private val follows: FollowRepository,
    private val teams: TeamRepository,
    private val players: PlayerRepository,
    private val clock: Clock
) {
    fun list(memberId: Int): FollowList {
        val all = follows.findByMemberIdOrderByIdAsc(memberId)
        val teamIds = all.filter { it.targetType == FollowType.TEAM.name }.map { it.targetId.toInt() }
        val playerIds = all.filter { it.targetType == FollowType.PLAYER.name }.map { it.targetId }
        val teamsById = teams.findAllById(teamIds).associateBy { it.teamId }
        val playersById = players.findAllById(playerIds).associateBy { it.playerId }
        return FollowList(
            teams = teamIds.mapNotNull { teamsById[it] }.map { FollowedTeam(it.teamId, it.name, it.logoUrl) },
            players = playerIds.mapNotNull { playersById[it] }.map { FollowedPlayer(it.playerId, it.name, it.photoUrl) }
        )
    }

    @Transactional
    fun followTeam(memberId: Int, teamId: Int) {
        if (!teams.existsById(teamId)) throw ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "팀을 찾을 수 없습니다.")
        add(memberId, FollowType.TEAM, teamId.toLong())
    }

    @Transactional
    fun followPlayer(memberId: Int, playerId: Long) {
        if (!players.existsById(playerId)) throw ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "선수를 찾을 수 없습니다.")
        add(memberId, FollowType.PLAYER, playerId)
    }

    @Transactional
    fun unfollow(memberId: Int, type: FollowType, targetId: Long) {
        follows.deleteByMemberIdAndTargetTypeAndTargetId(memberId, type.name, targetId)
    }

    private fun add(memberId: Int, type: FollowType, targetId: Long) {
        if (follows.existsByMemberIdAndTargetTypeAndTargetId(memberId, type.name, targetId)) return
        try {
            follows.saveAndFlush(Follow(memberId = memberId, targetType = type.name, targetId = targetId, createdAt = LocalDateTime.now(clock)))
        } catch (e: DataIntegrityViolationException) {
            // 동시에 같은 대상을 눌러 이미 들어간 경우: 유일 키가 막아 주므로 성공으로 본다
        }
    }
}

@RestController
@RequestMapping("/api/members/me/follows")
class FollowController(private val service: FollowService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt): FollowList = service.list(jwt.subject.toInt())

    @PutMapping("/teams/{teamId}")
    fun followTeam(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Int): ResponseEntity<Void> {
        service.followTeam(jwt.subject.toInt(), teamId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/teams/{teamId}")
    fun unfollowTeam(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Int): ResponseEntity<Void> {
        service.unfollow(jwt.subject.toInt(), FollowType.TEAM, teamId.toLong())
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/players/{playerId}")
    fun followPlayer(@AuthenticationPrincipal jwt: Jwt, @PathVariable playerId: Long): ResponseEntity<Void> {
        service.followPlayer(jwt.subject.toInt(), playerId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/players/{playerId}")
    fun unfollowPlayer(@AuthenticationPrincipal jwt: Jwt, @PathVariable playerId: Long): ResponseEntity<Void> {
        service.unfollow(jwt.subject.toInt(), FollowType.PLAYER, playerId)
        return ResponseEntity.noContent().build()
    }
}
