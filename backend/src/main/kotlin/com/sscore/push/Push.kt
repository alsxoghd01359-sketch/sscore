package com.sscore.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.sscore.auth.AuthSessionRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileInputStream
import java.time.Clock
import java.time.LocalDateTime

// 보낼 푸시 한 건. data는 앱이 알림을 눌렀을 때 쓰는 값(예: 경기 번호)이다.
data class PushMessage(val title: String, val body: String, val data: Map<String, String> = emptyMap())

// 푸시 발송 경계. 운영은 FCM, 키가 없는 개발 환경은 로그, 테스트는 가짜로 바뀐다.
// 더 이상 쓸 수 없는 토큰(앱 삭제 등)은 돌려줘서 서버가 정리하게 한다.
interface PushPort {
    fun send(tokens: List<String>, message: PushMessage): List<String>
}

class FcmPushPort(private val messaging: FirebaseMessaging) : PushPort {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun send(tokens: List<String>, message: PushMessage): List<String> {
        val invalid = mutableListOf<String>()
        tokens.forEach { token ->
            val built = com.google.firebase.messaging.Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(message.title).setBody(message.body).build())
                .putAllData(message.data)
                .build()
            try {
                messaging.send(built)
            } catch (e: FirebaseMessagingException) {
                if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED || e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalid += token
                } else {
                    log.warn("푸시 발송 실패 code={}: {}", e.messagingErrorCode, e.message)
                }
            }
        }
        return invalid
    }
}

// Firebase 키가 없는 개발 환경에서는 푸시 대신 서버 로그에 남긴다.
class LoggingPushPort : PushPort {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun send(tokens: List<String>, message: PushMessage): List<String> {
        log.info("[개발용 푸시] 기기 {}대 제목={} 내용={} data={}", tokens.size, message.title, message.body, message.data)
        return emptyList()
    }
}

@Configuration
class PushConfig {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun pushPort(@Value("\${sscore.push.credentials-file:}") credentialsFile: String): PushPort {
        val file = File(credentialsFile)
        if (credentialsFile.isBlank() || !file.isFile) {
            log.info("Firebase 키 파일이 없어 푸시는 로그로만 남깁니다. (docs/firebase-setup.md 참고)")
            return LoggingPushPort()
        }
        val app = FirebaseApp.getApps().firstOrNull() ?: FileInputStream(file).use {
            FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(it)).build())
        }
        log.info("FCM으로 푸시를 보냅니다.")
        return FcmPushPort(FirebaseMessaging.getInstance(app))
    }
}

// 로그인 세션 하나에 붙은 기기. 세션이 지워지면 DB가 함께 지운다.
@Entity
@Table(name = "push_devices")
class PushDevice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "member_id", nullable = false)
    var memberId: Int,

    @Column(name = "session_id", nullable = false, length = 36)
    var sessionId: String,

    @Column(nullable = false, length = 512)
    var token: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime
)

@Repository
interface PushDeviceRepository : JpaRepository<PushDevice, Long> {
    fun findByMemberId(memberId: Int): List<PushDevice>
    fun findBySessionId(sessionId: String): PushDevice?
    fun findByToken(token: String): PushDevice?

    @Modifying
    @Query("delete from PushDevice d where d.token in :tokens")
    fun deleteByTokenIn(tokens: Collection<String>): Int
}

data class RegisterPushTokenRequest(val token: String? = null)

@Service
class PushService(
    private val devices: PushDeviceRepository,
    private val port: PushPort,
    private val clock: Clock
) {
    // 이 로그인 기기의 토큰을 등록(갱신)한다. 같은 토큰이 다른 세션/회원에 붙어 있었다면(같은 폰에서 계정을 바꾼 경우) 옮겨 온다.
    @Transactional
    fun register(memberId: Int, sessionId: String, token: String) {
        val now = LocalDateTime.now(clock)
        val sameSession = devices.findBySessionId(sessionId)
        val sameToken = devices.findByToken(token)
        if (sameToken != null && sameToken.sessionId != sessionId) {
            devices.delete(sameToken)
            devices.flush()
        }
        val device = sameSession ?: PushDevice(memberId = memberId, sessionId = sessionId, token = token, updatedAt = now)
        device.memberId = memberId
        device.token = token
        device.updatedAt = now
        devices.save(device)
    }

    @Transactional
    fun unregister(sessionId: String) {
        devices.findBySessionId(sessionId)?.let { devices.delete(it) }
    }

    // 회원의 모든 기기로 보낸다. 쓸 수 없다고 돌아온 토큰은 지운다. 보낸 기기 수를 돌려준다.
    @Transactional
    fun sendToMember(memberId: Int, message: PushMessage): Int {
        val tokens = devices.findByMemberId(memberId).map { it.token }
        if (tokens.isEmpty()) return 0
        val invalid = port.send(tokens, message)
        if (invalid.isNotEmpty()) devices.deleteByTokenIn(invalid)
        return tokens.size - invalid.size
    }
}

@RestController
@RequestMapping("/api/members/me/push-token")
class PushTokenController(private val push: PushService) {

    @PutMapping
    fun register(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: RegisterPushTokenRequest): ResponseEntity<Void> {
        val token = request.token?.trim().orEmpty()
        if (token.isEmpty() || token.length > 512) {
            throw com.sscore.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "푸시 토큰이 올바르지 않습니다.")
        }
        push.register(jwt.subject.toInt(), jwt.getClaimAsString("sid"), token)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun unregister(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        push.unregister(jwt.getClaimAsString("sid"))
        return ResponseEntity.noContent().build()
    }
}
