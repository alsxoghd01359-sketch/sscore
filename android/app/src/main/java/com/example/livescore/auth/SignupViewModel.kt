package com.example.livescore.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.common.ApiResult
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
        if (!CODE_REGEX.matches(code)) {
            _message.value = "6자리 숫자 인증코드를 입력해 주세요."
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
        !EMAIL_REGEX.matches(email) -> "올바른 이메일 형식이 아닙니다."
        !NICKNAME_REGEX.matches(nickname) -> "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다."
        !PASSWORD_REGEX.matches(password) -> "비밀번호는 8~20자이며 특수문자를 포함해야 합니다."
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
        // 서버 규칙과 같다 (서버가 최종 검증)
        private val EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
        private val NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,12}$".toRegex()
        private val PASSWORD_REGEX = "^(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,20}$".toRegex()
        private val CODE_REGEX = "^\\d{6}$".toRegex()
        // 코드 만료·시도 초과는 같은 화면에서 재발송하면 되므로 처음으로 돌아가지 않는다
        private val RESTART_CODES = setOf("NO_PENDING_SIGNUP", "EMAIL_TAKEN", "NICKNAME_TAKEN")
    }
}
