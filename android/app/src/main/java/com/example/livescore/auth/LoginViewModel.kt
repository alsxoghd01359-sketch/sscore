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

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _message.value = "이메일과 비밀번호를 입력해 주세요."
            return
        }
        if (_loading.value == true) return
        _loading.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                when (val r = repository.login(email.trim(), password)) {
                    is ApiResult.Success -> {
                        SessionManager.save(r.data)
                        _loggedIn.value = true
                    }
                    is ApiResult.Failure -> _message.value = r.message
                }
            } finally {
                _loading.value = false
            }
        }
    }
}
