package com.example.livescore.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository = AuthRepository(RetrofitClient.authApi)
) : ViewModel() {

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    private val _loggedIn = MutableLiveData(false)
    val loggedIn: LiveData<Boolean> = _loggedIn

    // 탈퇴 유예 중인 계정으로 로그인했을 때의 안내 문구. 화면이 탈퇴 취소를 물어본 뒤 consume한다.
    private val _withdrawnPrompt = MutableLiveData<String?>(null)
    val withdrawnPrompt: LiveData<String?> = _withdrawnPrompt

    private var lastEmail = ""
    private var lastPassword = ""

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _message.value = "이메일과 비밀번호를 입력해 주세요."
            return
        }
        lastEmail = email.trim()
        lastPassword = password
        launchWithLoading {
            when (val r = repository.login(lastEmail, lastPassword)) {
                is ApiResult.Success -> onLoggedIn(r.data)
                is ApiResult.Failure ->
                    if (r.code == "ACCOUNT_WITHDRAWN") _withdrawnPrompt.value = r.message else _message.value = r.message
            }
        }
    }

    // 방금 입력한 이메일·비밀번호로 탈퇴를 취소하고 로그인한다
    fun restoreAccount() {
        launchWithLoading {
            when (val r = repository.restore(lastEmail, lastPassword)) {
                is ApiResult.Success -> onLoggedIn(r.data)
                is ApiResult.Failure -> _message.value = r.message
            }
        }
    }

    fun consumeWithdrawnPrompt() {
        _withdrawnPrompt.value = null
    }

    private fun onLoggedIn(data: LoginResponseData) {
        SessionManager.save(data)
        _loggedIn.value = true
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        if (_loading.value == true) return
        _loading.value = true
        _message.value = null
        viewModelScope.launch {
            try { block() } finally { _loading.value = false }
        }
    }
}
