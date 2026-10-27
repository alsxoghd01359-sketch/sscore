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

class SignupViewModel(
    private val repository: AuthRepository = AuthRepository(RetrofitClient.authApi)
) : ViewModel() {

    enum class Step { FORM, CODE }

    private val _step = MutableLiveData(Step.FORM)
    val step: LiveData<Step> = _step

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    // 화면에 보여 줄 오류/안내 문구 (null이면 숨김)
    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    // 코드 재발송까지 남은 초 (0이면 재발송 가능)
    private val _resendSeconds = MutableLiveData(0)
    val resendSeconds: LiveData<Int> = _resendSeconds

    private val _completed = MutableLiveData(false)
    val completed: LiveData<Boolean> = _completed

    var email: String = ""
        private set

    private var password: String = ""
    private var nickname: String = ""
    private var cooldownJob: Job? = null

    fun submit(emailInput: String, passwordInput: String, nicknameInput: String) {
        val e = emailInput.trim().lowercase()
        val n = nicknameInput.trim()
        validate(e, passwordInput, n)?.let { _message.value = it; return }

        email = e; password = passwordInput; nickname = n
        launchWithLoading {
            when (val r = repository.signup(e, passwordInput, n)) {
                is ApiResult.Success -> {
                    _step.value = Step.CODE
                    _message.value = null
                    startCooldown(r.data.resendAfterSeconds.toInt())
                }
                is ApiResult.Failure -> _message.value = r.message
            }
        }
    }

    fun verify(codeInput: String) {
        val code = codeInput.trim()
        if (!InputRules.isCode(code)) {
            _message.value = InputRules.CODE_MESSAGE
            return
        }
        launchWithLoading {
            when (val r = repository.verify(email, code)) {
                is ApiResult.Success -> _completed.value = true
                is ApiResult.Failure -> {
                    _message.value = r.message
                    // 진행 중인 가입이 없거나 이메일/닉네임이 이미 쓰이면 처음 화면으로 돌려보낸다
                    if (r.code in RESTART_CODES) _step.value = Step.FORM
                }
            }
        }
    }

    fun resend() {
        if ((_resendSeconds.value ?: 0) > 0) return
        launchWithLoading {
            when (val r = repository.resend(email)) {
                is ApiResult.Success -> {
                    _message.value = "인증코드를 다시 보냈습니다."
                    startCooldown(r.data.resendAfterSeconds.toInt())
                }
                is ApiResult.Failure -> {
                    _message.value = r.message
                    if (r.code == "RESEND_LIMIT" || r.code == "NO_PENDING_SIGNUP") _step.value = Step.FORM
                }
            }
        }
    }

    fun backToForm() {
        cooldownJob?.cancel()
        _resendSeconds.value = 0
        _message.value = null
        _step.value = Step.FORM
    }

    fun clearMessage() { _message.value = null }

    private fun validate(email: String, password: String, nickname: String): String? = when {
        !InputRules.isEmail(email) -> InputRules.EMAIL_MESSAGE
        !InputRules.isNickname(nickname) -> InputRules.NICKNAME_MESSAGE
        !InputRules.isPassword(password) -> InputRules.PASSWORD_MESSAGE
        else -> null
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

    companion object {
        // 코드 만료·시도 초과는 같은 화면에서 재발송하면 되므로 처음으로 돌아가지 않는다
        private val RESTART_CODES = setOf("NO_PENDING_SIGNUP", "EMAIL_TAKEN", "NICKNAME_TAKEN")
    }
}
