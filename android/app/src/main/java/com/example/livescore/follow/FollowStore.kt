package com.example.livescore.follow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.livescore.RetrofitClient
import com.example.livescore.auth.SessionManager
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

data class FollowedTeamData(val id: Int, val name: String, val logoUrl: String? = null)
data class FollowedPlayerData(val id: Long, val name: String, val photoUrl: String? = null)
data class FollowListData(
    val teams: List<FollowedTeamData> = emptyList(),
    val players: List<FollowedPlayerData> = emptyList()
)

// 로그인한 회원 전용 주소
interface FollowApi {
    @GET("/api/members/me/follows")
    suspend fun list(): Response<FollowListData>

    @PUT("/api/members/me/follows/teams/{id}")
    suspend fun followTeam(@Path("id") id: Int): Response<Unit>

    @DELETE("/api/members/me/follows/teams/{id}")
    suspend fun unfollowTeam(@Path("id") id: Int): Response<Unit>

    @PUT("/api/members/me/follows/players/{id}")
    suspend fun followPlayer(@Path("id") id: Long): Response<Unit>

    @DELETE("/api/members/me/follows/players/{id}")
    suspend fun unfollowPlayer(@Path("id") id: Long): Response<Unit>
}

class FollowRepository(private val api: FollowApi) {
    suspend fun list(): ApiResult<FollowListData> = apiCall { api.list() }
    suspend fun setTeam(id: Int, follow: Boolean): ApiResult<Unit> =
        apiCall { if (follow) api.followTeam(id) else api.unfollowTeam(id) }
    suspend fun setPlayer(id: Long, follow: Boolean): ApiResult<Unit> =
        apiCall { if (follow) api.followPlayer(id) else api.unfollowPlayer(id) }
}

// 이 기기에서 로그인한 회원의 팔로우 목록. 화면들이 함께 보고, 로그인하면 서버에서 받아 오고 로그아웃하면 비운다.
// 누르면 먼저 화면에 반영하고(바로 바뀌어 보이게) 서버에 저장하며, 실패하면 서버 내용으로 되돌린다.
object FollowStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { FollowRepository(RetrofitClient.followApi) }

    private val _follows = MutableLiveData(FollowListData())
    val follows: LiveData<FollowListData> = _follows

    fun init() {
        SessionManager.member.observeForever { member ->
            if (member == null) _follows.value = FollowListData() else refresh()
        }
    }

    fun refresh() {
        scope.launch {
            (repository.list() as? ApiResult.Success)?.let { _follows.value = it.data }
        }
    }

    fun isTeamFollowed(id: Int) = _follows.value?.teams?.any { it.id == id } == true
    fun isPlayerFollowed(id: Long) = _follows.value?.players?.any { it.id == id } == true

    fun toggleTeam(team: FollowedTeamData, onError: (String) -> Unit) {
        val current = _follows.value ?: FollowListData()
        val following = !isTeamFollowed(team.id)
        _follows.value = current.copy(
            teams = if (following) current.teams + team else current.teams.filterNot { it.id == team.id }
        )
        scope.launch {
            val result = repository.setTeam(team.id, following)
            if (result is ApiResult.Failure) { refresh(); onError(result.message) }
        }
    }

    fun togglePlayer(player: FollowedPlayerData, onError: (String) -> Unit) {
        val current = _follows.value ?: FollowListData()
        val following = !isPlayerFollowed(player.id)
        _follows.value = current.copy(
            players = if (following) current.players + player else current.players.filterNot { it.id == player.id }
        )
        scope.launch {
            val result = repository.setPlayer(player.id, following)
            if (result is ApiResult.Failure) { refresh(); onError(result.message) }
        }
    }
}
