package com.example.livescore.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// 로그인 상태(토큰·회원 정보)를 암호화 저장소에 보관하고 앱 전체에 알린다.
// 로그인하지 않은 상태(member == null)가 게스트다.
object SessionManager {

    private const val FILE = "sscore_session"
    private const val KEY_ACCESS = "access"
    private const val KEY_REFRESH = "refresh"
    private const val KEY_MEMBER_ID = "member_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_ROLE = "role"

    private var prefs: SharedPreferences? = null

    private val _member = MutableLiveData<MemberData?>(null)
    val member: LiveData<MemberData?> = _member

    // 갱신 토큰까지 만료되어 로그인이 풀렸을 때 한 번 true가 된다 (화면에서 안내 후 consume)
    private val _expired = MutableLiveData(false)
    val expired: LiveData<Boolean> = _expired

    @Volatile var accessToken: String? = null
        private set
    @Volatile var refreshToken: String? = null
        private set

    val isLoggedIn: Boolean get() = _member.value != null

    fun init(context: Context) {
        if (prefs != null) return
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val p = try {
            open(context, key)
        } catch (e: Exception) {
            // 키가 손상된 기기에서는 저장된 로그인을 버리고 게스트로 시작한다
            context.deleteSharedPreferences(FILE)
            open(context, key)
        }
        prefs = p
        accessToken = p.getString(KEY_ACCESS, null)
        refreshToken = p.getString(KEY_REFRESH, null)
        val id = p.getInt(KEY_MEMBER_ID, -1)
        if (id != -1 && accessToken != null && refreshToken != null) {
            _member.value = MemberData(
                id, p.getString(KEY_EMAIL, "").orEmpty(), p.getString(KEY_NICKNAME, "").orEmpty(),
                p.getString(KEY_ROLE, "USER").orEmpty()
            )
        }
    }

    private fun open(context: Context, key: MasterKey) = EncryptedSharedPreferences.create(
        context, FILE, key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(login: LoginResponseData) {
        accessToken = login.accessToken
        refreshToken = login.refreshToken
        prefs?.edit()
            ?.putString(KEY_ACCESS, login.accessToken)
            ?.putString(KEY_REFRESH, login.refreshToken)
            ?.putInt(KEY_MEMBER_ID, login.member.memberId)
            ?.putString(KEY_EMAIL, login.member.email)
            ?.putString(KEY_NICKNAME, login.member.nickname)
            ?.putString(KEY_ROLE, login.member.role)
            ?.apply()
        _member.postValue(login.member)
    }

    // 토큰 갱신 결과만 반영한다 (회원 정보는 그대로)
    fun updateTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        prefs?.edit()?.putString(KEY_ACCESS, access)?.putString(KEY_REFRESH, refresh)?.apply()
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        prefs?.edit()?.clear()?.apply()
        _member.postValue(null)
    }

    // 갱신 토큰이 만료/폐기되어 로그인이 풀렸다
    fun expire() {
        clear()
        _expired.postValue(true)
    }

    fun consumeExpired() {
        _expired.postValue(false)
    }
}
