package com.sscore.notification

import com.sscore.common.ApiException
import com.sscore.follow.FollowRepository
import com.sscore.follow.FollowService
import com.sscore.follow.FollowType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 알림 종류 (라인업 발표 / 경기 시작 / 득점·이벤트 / 경기 종료·결과)
enum class NotificationKind { MATCH_START, GOALS_EVENTS, MATCH_END, LINEUP }

// 회원의 알림 종류별 설정. 행이 없으면 모두 켜짐이다.
@Entity
@Table(name = "notification_settings")
class NotificationSetting(
    @Id
    @Column(name = "member_id")
    val memberId: Int,

    @Column(name = "match_start", nullable = false) var matchStart: Boolean = true,
    @Column(name = "goals_events", nullable = false) var goalsEvents: Boolean = true,
    @Column(name = "match_end", nullable = false) var matchEnd: Boolean = true,
    @Column(name = "lineup", nullable = false) var lineup: Boolean = true
) {
    fun isOn(kind: NotificationKind) = when (kind) {
        NotificationKind.MATCH_START -> matchStart
        NotificationKind.GOALS_EVENTS -> goalsEvents
        NotificationKind.MATCH_END -> matchEnd
        NotificationKind.LINEUP -> lineup
    }
}

@Repository
interface NotificationSettingRepository : JpaRepository<NotificationSetting, Int>

// 알림이 나가는 규칙: 알림 종류가 켜져 있고 그 대상(팀/선수)의 알림도 켜져 있어야 한다.
object NotificationRules {
    fun shouldSend(setting: NotificationSetting?, kind: NotificationKind, targetNotify: Boolean): Boolean =
        (setting?.isOn(kind) ?: true) && targetNotify
}

data class KindSettings(val matchStart: Boolean, val goalsEvents: Boolean, val matchEnd: Boolean, val lineup: Boolean)

// 대상 하나에 대해 종류별로 실제 알림이 나가는지 (종류 설정 AND 대상 설정)
data class TargetSettings(val id: Long, val name: String, val imageUrl: String?, val notify: Boolean, val effective: KindSettings)

data class NotificationSettingsView(val kinds: KindSettings, val teams: List<TargetSettings>, val players: List<TargetSettings>)

data class KindsPatch(val matchStart: Boolean? = null, val goalsEvents: Boolean? = null, val matchEnd: Boolean? = null, val lineup: Boolean? = null)
data class TargetPatch(val enabled: Boolean? = null)

@Service
class NotificationSettingsService(
    private val settings: NotificationSettingRepository,
    private val follows: FollowRepository,
    private val followService: FollowService
) {
    fun view(memberId: Int): NotificationSettingsView {
        val setting = settings.findById(memberId).orElse(null)
        val kinds = (setting ?: NotificationSetting(memberId)).toKinds()
        val notifyById = follows.findByMemberIdOrderByIdAsc(memberId).associate { (it.targetType to it.targetId) to it.notify }
        val list = followService.list(memberId)

        fun effective(notify: Boolean) = KindSettings(
            NotificationRules.shouldSend(setting, NotificationKind.MATCH_START, notify),
            NotificationRules.shouldSend(setting, NotificationKind.GOALS_EVENTS, notify),
            NotificationRules.shouldSend(setting, NotificationKind.MATCH_END, notify),
            NotificationRules.shouldSend(setting, NotificationKind.LINEUP, notify)
        )
        return NotificationSettingsView(
            kinds,
            list.teams.map {
                val notify = notifyById[FollowType.TEAM.name to it.id.toLong()] ?: true
                TargetSettings(it.id.toLong(), it.name, it.logoUrl, notify, effective(notify))
            },
            list.players.map {
                val notify = notifyById[FollowType.PLAYER.name to it.id] ?: true
                TargetSettings(it.id, it.name, it.photoUrl, notify, effective(notify))
            }
        )
    }

    @Transactional
    fun updateKinds(memberId: Int, patch: KindsPatch): NotificationSettingsView {
        val row = settings.findById(memberId).orElseGet { NotificationSetting(memberId) }
        patch.matchStart?.let { row.matchStart = it }
        patch.goalsEvents?.let { row.goalsEvents = it }
        patch.matchEnd?.let { row.matchEnd = it }
        patch.lineup?.let { row.lineup = it }
        settings.save(row)
        return view(memberId)
    }

    @Transactional
    fun updateTarget(memberId: Int, type: FollowType, targetId: Long, patch: TargetPatch): NotificationSettingsView {
        val enabled = patch.enabled ?: throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "enabled 값이 필요합니다.")
        val follow = follows.findByMemberIdAndTargetTypeAndTargetId(memberId, type.name, targetId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOLLOWING", "팔로우하지 않은 대상입니다.")
        follow.notify = enabled
        follows.save(follow)
        return view(memberId)
    }

    private fun NotificationSetting.toKinds() = KindSettings(matchStart, goalsEvents, matchEnd, lineup)
}

@RestController
@RequestMapping("/api/members/me/notification-settings")
class NotificationSettingsController(private val service: NotificationSettingsService) {

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt): NotificationSettingsView = service.view(jwt.subject.toInt())

    @PatchMapping("/kinds")
    fun updateKinds(@AuthenticationPrincipal jwt: Jwt, @RequestBody patch: KindsPatch): NotificationSettingsView =
        service.updateKinds(jwt.subject.toInt(), patch)

    @PatchMapping("/teams/{teamId}")
    fun updateTeam(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Int, @RequestBody patch: TargetPatch): NotificationSettingsView =
        service.updateTarget(jwt.subject.toInt(), FollowType.TEAM, teamId.toLong(), patch)

    @PatchMapping("/players/{playerId}")
    fun updatePlayer(@AuthenticationPrincipal jwt: Jwt, @PathVariable playerId: Long, @RequestBody patch: TargetPatch): NotificationSettingsView =
        service.updateTarget(jwt.subject.toInt(), FollowType.PLAYER, playerId, patch)
}
