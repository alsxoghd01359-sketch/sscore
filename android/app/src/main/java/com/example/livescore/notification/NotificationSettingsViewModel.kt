package com.example.livescore.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.UiState
import com.example.livescore.common.apiCall
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

data class KindSettingsData(
    val matchStart: Boolean = true,
    val goalsEvents: Boolean = true,
    val matchEnd: Boolean = true,
    val lineup: Boolean = true
)

// effective: 이 대상에서 종류별로 실제 알림이 나가는지 (종류 설정 AND 대상 설정)
data class TargetSettingsData(
    val id: Long,
    val name: String,
    val imageUrl: String? = null,
    val notify: Boolean = true,
    val effective: KindSettingsData = KindSettingsData()
)

data class NotificationSettingsData(
    val kinds: KindSettingsData = KindSettingsData(),
    val teams: List<TargetSettingsData> = emptyList(),
    val players: List<TargetSettingsData> = emptyList()
)

data class TargetBody(val enabled: Boolean)

// 로그인한 회원 전용 주소
interface NotificationApi {
    @GET("/api/members/me/notification-settings")
    suspend fun get(): Response<NotificationSettingsData>

    @PATCH("/api/members/me/notification-settings/kinds")
    suspend fun kinds(@Body body: Map<String, Boolean>): Response<NotificationSettingsData>

    @PATCH("/api/members/me/notification-settings/teams/{id}")
    suspend fun team(@Path("id") id: Long, @Body body: TargetBody): Response<NotificationSettingsData>

    @PATCH("/api/members/me/notification-settings/players/{id}")
    suspend fun player(@Path("id") id: Long, @Body body: TargetBody): Response<NotificationSettingsData>
}

class NotificationRepository(private val api: NotificationApi) {
    suspend fun get(): ApiResult<NotificationSettingsData> = apiCall { api.get() }
    suspend fun setKind(key: String, on: Boolean): ApiResult<NotificationSettingsData> = apiCall { api.kinds(mapOf(key to on)) }
    suspend fun setTarget(isTeam: Boolean, id: Long, on: Boolean): ApiResult<NotificationSettingsData> =
        apiCall { if (isTeam) api.team(id, TargetBody(on)) else api.player(id, TargetBody(on)) }
}

// 알림 설정 화면: 종류별 + 팔로우 대상별 토글. 바꾸면 서버에 저장하고, 서버가 돌려준 최신 설정으로 화면을 맞춘다.
class NotificationSettingsViewModel(
    private val repository: NotificationRepository = NotificationRepository(RetrofitClient.notificationApi)
) : ViewModel() {

    private val _state = MutableLiveData<UiState<NotificationSettingsData>>(UiState.Loading)
    val state: LiveData<UiState<NotificationSettingsData>> = _state

    // 저장에 실패했을 때 한 번 보여 줄 안내
    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch { publish(repository.get()) }
    }

    fun setKind(key: String, on: Boolean) = save { repository.setKind(key, on) }

    fun setTarget(isTeam: Boolean, id: Long, on: Boolean) = save { repository.setTarget(isTeam, id, on) }

    fun messageShown() { _message.value = null }

    private fun save(call: suspend () -> ApiResult<NotificationSettingsData>) {
        viewModelScope.launch {
            val result = call()
            if (result is ApiResult.Failure) {
                _message.value = result.message
                // 화면의 스위치를 서버 값으로 되돌린다
                (_state.value as? UiState.Success)?.let { _state.value = UiState.Success(it.data.copy()) }
            } else publish(result)
        }
    }

    private fun publish(result: ApiResult<NotificationSettingsData>) {
        _state.value = when (result) {
            is ApiResult.Success -> UiState.Success(result.data)
            is ApiResult.Failure -> UiState.Error(result.message)
        }
    }
}
