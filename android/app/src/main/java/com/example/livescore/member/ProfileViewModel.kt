package com.example.livescore.member

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.auth.SessionManager
import com.example.livescore.common.ApiResult
import com.example.livescore.common.InputRules
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: MemberRepository = MemberRepository(RetrofitClient.memberApi)
) : ViewModel() {

    // 화면에 보여 줄 결과 문구. error=true면 빨간색, false면 성공 안내
    data class Notice(val text: String, val error: Boolean)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _nicknameNotice = MutableLiveData<Notice?>(null)
    val nicknameNotice: LiveData<Notice?> = _nicknameNotice

    private val _passwordNotice = MutableLiveData<Notice?>(null)
    val passwordNotice: LiveData<Notice?> = _passwordNotice

    // 비밀번호를 바꾸는 데 성공하면 입력칸을 비우도록 화면에 알린다
    private val _passwordChanged = MutableLiveData(false)
    val passwordChanged: LiveData<Boolean> = _passwordChanged

    private val _withdrawNotice = MutableLiveData<Notice?>(null)
    val withdrawNotice: LiveData<Notice?> = _withdrawNotice

    // 탈퇴 요청이 받아들여지면 true. 화면은 안내 후 닫는다.
    private val _withdrawn = MutableLiveData(false)
    val withdrawn: LiveData<Boolean> = _withdrawn

    fun withdraw(password: String) {
        if (password.isEmpty()) {
            _withdrawNotice.value = Notice("비밀번호를 입력해 주세요.", true)
            return
        }
        launchWithLoading {
            when (val r = repository.withdraw(password)) {
                is ApiResult.Success -> {
                    // 서버가 모든 기기의 로그인을 풀었으므로 이 기기도 게스트로 돌아간다
                    SessionManager.clear()
                    _withdrawn.value = true
                }
                is ApiResult.Failure -> _withdrawNotice.value = Notice(r.message, true)
            }
        }
    }

    fun changeNickname(input: String) {
        val nickname = input.trim()
        if (!InputRules.isNickname(nickname)) {
            _nicknameNotice.value = Notice(InputRules.NICKNAME_MESSAGE, true)
            return
        }
        if (nickname == SessionManager.member.value?.nickname) {
            _nicknameNotice.value = Notice("현재 닉네임과 같습니다.", true)
            return
        }
        launchWithLoading {
            when (val r = repository.changeNickname(nickname)) {
                is ApiResult.Success -> {
                    SessionManager.updateMember(r.data)
                    _nicknameNotice.value = Notice("닉네임을 변경했습니다.", false)
                }
                is ApiResult.Failure -> _nicknameNotice.value = Notice(r.message, true)
            }
        }
    }

    fun changePassword(current: String, next: String) {
        when {
            current.isEmpty() -> { _passwordNotice.value = Notice("현재 비밀번호를 입력해 주세요.", true); return }
            !InputRules.isPassword(next) -> { _passwordNotice.value = Notice(InputRules.PASSWORD_MESSAGE, true); return }
            current == next -> { _passwordNotice.value = Notice("현재 비밀번호와 다른 비밀번호를 입력해 주세요.", true); return }
        }
        launchWithLoading {
            when (val r = repository.changePassword(current, next)) {
                is ApiResult.Success -> {
                    _passwordNotice.value = Notice("비밀번호를 변경했습니다. 다른 기기는 로그아웃됩니다.", false)
                    _passwordChanged.value = true
                    _passwordChanged.value = false
                }
                is ApiResult.Failure -> _passwordNotice.value = Notice(r.message, true)
            }
        }
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        if (_loading.value == true) return
        _loading.value = true
        viewModelScope.launch {
            try { block() } finally { _loading.value = false }
        }
    }
}
