package com.sscore.notification

import com.sscore.common.ApiException
import com.sscore.dto.MatchEventDto
import com.sscore.entity.MatchEntity
import com.sscore.follow.FollowRepository
import com.sscore.follow.FollowType
import com.sscore.match.MatchDetailService
import com.sscore.member.MemberRepository
import com.sscore.member.MemberStatus
import com.sscore.push.PushMessage
import com.sscore.push.PushService
import com.sscore.repository.MatchRepository
import com.sscore.repository.PlayerRepository
import com.sscore.service.ApiFootballService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// 이미 보낸 알림. (회원, 이벤트)는 유일해서 같은 이벤트를 한 회원에게 두 번 보내지 않는다.
@Entity
@Table(name = "notification_log")
class NotificationLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "member_id", nullable = false)
    val memberId: Int,

    @Column(name = "event_key", nullable = false, length = 190)
    val eventKey: String,

    @Column(name = "sent_at", nullable = false)
    val sentAt: LocalDateTime
)

@Repository
interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun existsByEventKey(eventKey: String): Boolean
}

// 경기에서 일어난 일(시작·득점/이벤트·종료·라인업)을 팔로우한 회원에게 알림으로 보낸다.
// 받는 사람: 두 팀을 팔로우한 회원, 그 팀 소속 선수를 팔로우한 회원(득점/이벤트는 그 선수가 관련된 경우만).
// 알림 종류와 그 대상이 모두 켜져 있어야 하고(NotificationRules), 탈퇴 유예 중인 회원은 제외한다.
// 한 회원은 같은 이벤트를 한 번만 받는다 (두 팀을 모두 팔로우해도, 다시 감지돼도, 서버를 다시 켜도).
@Service
class MatchNotifier(
    private val matches: MatchRepository,
    private val follows: FollowRepository,
    private val players: PlayerRepository,
    private val members: MemberRepository,
    private val settings: NotificationSettingRepository,
    private val sent: NotificationLogRepository,
    private val push: PushService,
    private val details: MatchDetailService,
    private val apiService: ApiFootballService,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        val LINEUP_WINDOW: Duration = Duration.ofMinutes(90)       // 라인업은 보통 킥오프 1시간 전쯤 나온다
        val LINEUP_CHECK_EVERY: Duration = Duration.ofMinutes(5)
        val TIME = Regex("""^(\d{2}):(\d{2})""")
    }

    private var lastLineupCheck: LocalDateTime? = null

    // ── 경기 시작 / 종료 ─────────────────────────────────────────

    fun matchStarted(m: MatchEntity) {
        val id = m.fixtureId ?: return
        notify(m, NotificationKind.MATCH_START, "start:$id", null) {
            PushMessage("경기 시작", "${m.homeTeam} vs ${m.awayTeam}", data(m, "MATCH_START"))
        }
    }

    fun matchEnded(m: MatchEntity) {
        val id = m.fixtureId ?: return
        notify(m, NotificationKind.MATCH_END, "end:$id", null) {
            PushMessage("경기 종료", "${m.homeTeam} ${m.score} ${m.awayTeam}", data(m, "MATCH_END"))
        }
    }

    // ── 득점 / 주요 이벤트 ───────────────────────────────────────

    // 점수가 바뀐 경기: 받을 사람이 있을 때만 경기 상세를 새로 받아 아직 보내지 않은 득점·퇴장을 알린다.
    fun scoreChanged(m: MatchEntity) {
        val id = m.fixtureId ?: return
        if (recipients(m, NotificationKind.GOALS_EVENTS, null).isEmpty()) return
        val events = try {
            details.refreshInfo(id).events
        } catch (e: ApiException) {
            log.warn("득점 알림용 경기 상세 조회 실패 fixture={}: {}", id, e.message)
            return
        }
        events.filter { isNotable(it) }.forEach { event ->
            val involved = listOfNotNull(event.playerName, event.assistPlayerName).toSet()
            notify(m, NotificationKind.GOALS_EVENTS, eventKey(id, event), involved) { eventMessage(m, event) }
        }
    }

    private fun isNotable(e: MatchEventDto) =
        (e.type == "Goal" && e.detail != "Missed Penalty") || (e.type == "Card" && e.detail?.contains("Red") == true)

    private fun eventKey(fixtureId: Long, e: MatchEventDto) =
        "event:$fixtureId:${e.time}:${e.extraTime ?: 0}:${e.type}:${e.detail}:${e.playerName}".take(190)

    private fun eventMessage(m: MatchEntity, e: MatchEventDto): PushMessage {
        val minute = if (e.extraTime != null) "${e.time}+${e.extraTime}'" else "${e.time}'"
        val player = e.playerName ?: "선수"
        return if (e.type == "Goal") {
            PushMessage("⚽ 골! ${m.homeTeam} ${m.score} ${m.awayTeam}", "$minute $player (${e.teamName})", data(m, "GOAL"))
        } else {
            PushMessage("🟥 퇴장 ${m.homeTeam} ${m.score} ${m.awayTeam}", "$minute $player (${e.teamName})", data(m, "RED_CARD"))
        }
    }

    // ── 라인업 발표 ──────────────────────────────────────────────

    // 곧 시작하는 경기 중 팔로우하는 사람이 있는 경기만 라인업이 나왔는지 5분마다 확인한다 (외부 호출을 아끼기 위해).
    fun checkLineups() {
        val now = LocalDateTime.now(clock)
        val last = lastLineupCheck
        if (last != null && now >= last && Duration.between(last, now) < LINEUP_CHECK_EVERY) return
        lastLineupCheck = now

        val dates = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).map { it.toString() }
        val upcoming = matches.findByMatchDateIn(dates).filter { m ->
            val kickoff = kickoffOf(m) ?: return@filter false
            !m.matchTime.contains("(") && now >= kickoff.minus(LINEUP_WINDOW) && now < kickoff
        }
        for (m in upcoming) {
            val id = m.fixtureId ?: continue
            if (sent.existsByEventKey("lineup:$id")) continue
            if (recipients(m, NotificationKind.LINEUP, null).isEmpty()) continue
            val lineups = try {
                apiService.fetchLineups(id)
            } catch (e: ApiException) {
                log.warn("라인업 확인 실패 fixture={}: {}", id, e.message)
                continue
            }
            if (lineups.size < 2) continue
            notify(m, NotificationKind.LINEUP, "lineup:$id", null) {
                PushMessage("라인업 발표", "${m.homeTeam} vs ${m.awayTeam}", data(m, "LINEUP"))
            }
        }
    }

    private fun kickoffOf(m: MatchEntity): LocalDateTime? {
        val t = TIME.find(m.matchTime) ?: return null
        val date = runCatching { LocalDate.parse(m.matchDate) }.getOrNull() ?: return null
        return LocalDateTime.of(date, LocalTime.of(t.groupValues[1].toInt(), t.groupValues[2].toInt()))
    }

    // ── 받는 사람과 발송 ─────────────────────────────────────────

    private fun data(m: MatchEntity, type: String) = mapOf("type" to type, "fixtureId" to m.fixtureId.toString())

    private fun notify(m: MatchEntity, kind: NotificationKind, key: String, involvedNames: Set<String>?, message: () -> PushMessage) {
        val targets = recipients(m, kind, involvedNames)
        if (targets.isEmpty()) return
        val built = message()
        targets.forEach { memberId ->
            try {
                sent.saveAndFlush(NotificationLog(memberId = memberId, eventKey = key, sentAt = LocalDateTime.now(clock)))
            } catch (e: DataIntegrityViolationException) {
                return@forEach   // 이 회원에게는 이미 보낸 이벤트
            }
            push.sendToMember(memberId, built)
        }
    }

    // 팔로우 대상(팀·선수) 중 알림이 켜진 것이 하나라도 있고, 알림 종류가 켜져 있으며, 탈퇴 유예 중이 아닌 회원
    private fun recipients(m: MatchEntity, kind: NotificationKind, involvedNames: Set<String>?): Set<Int> {
        val teamIds = listOfNotNull(m.homeTeamId, m.awayTeamId)
        if (teamIds.isEmpty()) return emptySet()
        val teamFollows = follows.findByTargetTypeAndTargetIdIn(FollowType.TEAM.name, teamIds.map { it.toLong() })
        val targetFollows = teamFollows + playerFollows(m, involvedNames)

        val notifyByMember = targetFollows.groupBy { it.memberId }.mapValues { (_, rows) -> rows.any { it.notify } }
        if (notifyByMember.isEmpty()) return emptySet()
        val active = members.findAllById(notifyByMember.keys).filter { it.status == MemberStatus.ACTIVE }.mapNotNull { it.userId }
        val settingsByMember = settings.findAllById(active).associateBy { it.memberId }
        return active.filter { NotificationRules.shouldSend(settingsByMember[it], kind, notifyByMember.getValue(it)) }.toSet()
    }

    // 그 경기 두 팀 소속 선수를 팔로우한 기록. 이벤트 알림이면 그 이벤트에 관련된 선수만.
    private fun playerFollows(m: MatchEntity, involvedNames: Set<String>?): List<com.sscore.follow.Follow> {
        val teamIds = listOfNotNull(m.homeTeamId, m.awayTeamId)
        val squad = players.findByTeamTeamIdIn(teamIds).filter { involvedNames == null || it.name in involvedNames }
        if (squad.isEmpty()) return emptyList()
        return follows.findByTargetTypeAndTargetIdIn(FollowType.PLAYER.name, squad.map { it.playerId })
    }
}
