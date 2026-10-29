package com.example.livescore.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.livescore.R
import com.example.livescore.RetrofitClient
import com.example.livescore.auth.SessionManager
import com.example.livescore.common.apiCall
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT

data class PushTokenBody(val token: String)

// 로그인한 회원 전용 주소
interface PushApi {
    @PUT("/api/members/me/push-token")
    suspend fun register(@Body body: PushTokenBody): Response<Unit>
}

// 이 기기의 FCM 토큰을 서버에 등록한다. 로그인할 때, 토큰이 새로 발급될 때 부른다.
// (로그아웃하면 서버가 로그인 세션과 함께 이 기기 토큰을 지운다)
object PushRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        SessionManager.member.observeForever { member -> if (member != null) sync() }
    }

    fun sync() {
        if (!SessionManager.isLoggedIn) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> register(token) }
    }

    fun register(token: String) {
        if (!SessionManager.isLoggedIn) return
        scope.launch { apiCall { RetrofitClient.pushApi.register(PushTokenBody(token)) } }
    }
}

const val MATCH_CHANNEL_ID = "match_events"

// 푸시를 받는 서비스. 앱이 켜져 있을 때 온 푸시는 직접 알림으로 띄운다 (꺼져 있을 때는 시스템이 띄운다).
class PushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushRegistrar.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), notification)
    }
}

fun ensureChannel(context: android.content.Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(MATCH_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(MATCH_CHANNEL_ID, "경기 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "팔로우한 팀·선수의 경기 시작, 득점, 결과, 라인업 알림"
            }
        )
    }
}

// 로그인한 회원에게 알림 권한을 한 번 요청한다 (Android 13 이상). Activity의 onCreate에서 부른다.
fun AppCompatActivity.requestNotificationPermissionWhenLoggedIn() {
    ensureChannel(this)
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SessionManager.member.observe(this) { member ->
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (member != null && !granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
