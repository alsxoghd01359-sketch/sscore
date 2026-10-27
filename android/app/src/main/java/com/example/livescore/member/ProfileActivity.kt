package com.example.livescore.member

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.livescore.R
import com.example.livescore.auth.SessionManager
import com.example.livescore.auth.logout
import com.example.livescore.databinding.ActivityProfileBinding

// 내 정보: 닉네임 변경, 비밀번호 변경, 로그아웃
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSaveNickname.setOnClickListener { viewModel.changeNickname(binding.etNickname.text.toString()) }
        binding.btnChangePassword.setOnClickListener {
            viewModel.changePassword(binding.etCurrentPassword.text.toString(), binding.etNewPassword.text.toString())
        }
        binding.btnLogout.setOnClickListener {
            logout()
            Toast.makeText(this, "로그아웃했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 로그아웃·로그인 만료로 회원이 사라지면 화면을 닫는다
        SessionManager.member.observe(this) { member ->
            if (member == null) {
                finish()
                return@observe
            }
            binding.tvEmail.text = member.email
            if (!binding.etNickname.hasFocus()) binding.etNickname.setText(member.nickname)
        }
        viewModel.loading.observe(this) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSaveNickname.isEnabled = !loading
            binding.btnChangePassword.isEnabled = !loading
        }
        viewModel.nicknameNotice.observe(this) { show(binding.tvNicknameNotice, it) }
        viewModel.passwordNotice.observe(this) { show(binding.tvPasswordNotice, it) }
        viewModel.passwordChanged.observe(this) { changed ->
            if (changed) {
                binding.etCurrentPassword.text?.clear()
                binding.etNewPassword.text?.clear()
            }
        }
    }

    private fun show(view: TextView, notice: ProfileViewModel.Notice?) {
        if (notice == null) {
            view.visibility = View.GONE
            return
        }
        view.text = notice.text
        view.setTextColor(ContextCompat.getColor(this, if (notice.error) R.color.negative else R.color.positive))
        view.visibility = View.VISIBLE
    }
}
