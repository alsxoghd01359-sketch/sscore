package com.example.livescore.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.livescore.databinding.ActivityRegisterBinding

// 가입: 1) 이메일·닉네임·비밀번호 입력 → 2) 이메일로 받은 6자리 인증코드 입력
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: SignupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            viewModel.submit(
                binding.etRegEmail.text.toString(),
                binding.etRegPassword.text.toString(),
                binding.etRegNickname.text.toString()
            )
        }
        binding.btnVerify.setOnClickListener { viewModel.verify(binding.etCode.text.toString()) }
        binding.btnResend.setOnClickListener { viewModel.resend() }
        binding.tvChangeEmail.setOnClickListener { viewModel.backToForm() }
        binding.tvBackToLogin.setOnClickListener { finish() }

        viewModel.step.observe(this) { step ->
            val inCode = step == SignupViewModel.Step.CODE
            binding.groupForm.visibility = if (inCode) View.GONE else View.VISIBLE
            binding.groupCode.visibility = if (inCode) View.VISIBLE else View.GONE
            if (inCode) {
                binding.tvCodeInfo.text = "${viewModel.email}(으)로 보낸 6자리 인증코드를 입력해 주세요. 10분 안에 입력해야 해요."
                binding.etCode.text?.clear()
            }
        }
        viewModel.loading.observe(this) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = !loading
            binding.btnVerify.isEnabled = !loading
        }
        viewModel.message.observe(this) { message ->
            binding.tvMessage.text = message.orEmpty()
            binding.tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.resendSeconds.observe(this) { seconds ->
            binding.btnResend.isEnabled = seconds == 0
            binding.btnResend.text = if (seconds > 0) "인증코드 재발송 (${seconds}초 후)" else "인증코드 재발송"
        }
        viewModel.completed.observe(this) { completed ->
            if (completed) {
                Toast.makeText(this, "가입이 완료되었습니다. 로그인해 주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
