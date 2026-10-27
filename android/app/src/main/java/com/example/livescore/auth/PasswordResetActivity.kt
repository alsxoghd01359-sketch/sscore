package com.example.livescore.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.livescore.databinding.ActivityPasswordResetBinding

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetBinding
    private val viewModel: PasswordResetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordResetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener { viewModel.requestCode(binding.etEmail.text.toString()) }
        binding.btnConfirm.setOnClickListener {
            viewModel.confirm(binding.etCode.text.toString(), binding.etNewPassword.text.toString())
        }
        binding.btnResend.setOnClickListener { viewModel.resend() }
        binding.tvChangeEmail.setOnClickListener { viewModel.backToEmail() }
        binding.tvBackToLogin.setOnClickListener { finish() }

        viewModel.step.observe(this) { step ->
            val confirming = step == PasswordResetViewModel.Step.CONFIRM
            binding.groupEmail.visibility = if (confirming) View.GONE else View.VISIBLE
            binding.groupConfirm.visibility = if (confirming) View.VISIBLE else View.GONE
            if (confirming) {
                binding.tvCodeInfo.text = "가입한 이메일이라면 ${viewModel.email}(으)로 6자리 인증코드를 보냈어요. 10분 안에 입력해 주세요."
                binding.etCode.text?.clear()
                binding.etNewPassword.text?.clear()
            }
        }
        viewModel.loading.observe(this) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSendCode.isEnabled = !loading
            binding.btnConfirm.isEnabled = !loading
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
                Toast.makeText(this, "비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
