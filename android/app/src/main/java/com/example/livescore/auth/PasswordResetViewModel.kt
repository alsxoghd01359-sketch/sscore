package com.example.livescore.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
import com.example.livescore.common.InputRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 비밀번호 재설정: 1) 이메일로 인증코드 받기 → 2) 코드와 새 비밀번호 입력
class PasswordResetViewModel(
    private val repository: AuthRepository = AuthRepository(RetrofitClient.authApi)
) : ViewModel() {

    enum class Step { EMAIL, CONFIRM }

    private val _step = MutableLiveData(Step.EMAIL)
    val step: LiveData<Step> = _step

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    private val _resendSeconds = MutableLiveData(0)
    val resendSeconds: LiveData<Int> = _resendSeconds

    private val _completed = MutableLiveData(false)
    val completed: LiveData<Boolean> = _completed

    var email: String = ""
        private set

    private var cooldownJob: Job? = null

    fun requestCode(emailInput: String) {
        val e = emailInput.trim().lowercase()
        if (!InputRules.isEmail(e)) {
            _message.value = InputRules.EMAIL_MESSAGE
            return
        }
        email = e
        launchWithLoading {
            when (val r = repository.requestPasswordReset(e)) {
                is ApiResult.Success -> {
                    _step.value = Step.CONFIRM
                    _message.value = null
                    startCooldown(r.data.resendAfterSeconds.toInt())
                }
                is ApiResult.Failure -> _message.value = r.message
            }
        }
    }

    fun confirm(codeInput: String, newPassword: String) {
        val code = codeInput.trim()
        when {
            !InputRules.isCode(code) -> { _message.value = InputRules.CODE_MESSAGE; return }
            !InputRules.isPassword(newPassword) -> { _message.value = InputRules.PASSWORD_MESSAGE; return }
        }
        launchWithLoading {
            when (val r = repository.confirmPasswordReset(email, code, newPassword)) {
                is ApiResult.Success -> _completed.value = true
                is ApiResult.Failure -> {
                    _message.value = r.message
                    if (r.code == "NO_PENDING_RESET") _step.value = Step.EMAIL
                }
            }
        }
    }

    fun resend() {
        if ((_resendSeconds.value ?: 0) > 0) return
        launchWithLoading {
            when (val r = repository.resendPasswordReset(email)) {
                is ApiResult.Success -> {
                    _message.value = "인증코드를 다시 보냈습니다."
                    startCooldown(r.data.resendAfterSeconds.toInt())
                }
                is ApiResult.Failure -> {
                    _message.value = r.message
                    if (r.code == "RESEND_LIMIT" || r.code == "NO_PENDING_RESET") _step.value = Step.EMAIL
                }
            }
        }
    }

    fun backToEmail() {
        cooldownJob?.cancel()
        _resendSeconds.value = 0
        _message.value = null
        _step.value = Step.EMAIL
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        if (_loading.value == true) return
        _loading.value = true
        viewModelScope.launch {
            try { block() } finally { _loading.value = false }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (s in seconds downTo 0) {
                _resendSeconds.value = s
                if (s > 0) delay(1000)
            }
        }
    }
}
