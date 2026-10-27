package com.example.livescore.member

import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
        binding.btnWithdraw.setOnClickListener { confirmWithdraw() }
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
        viewModel.withdrawNotice.observe(this) { show(binding.tvWithdrawNotice, it) }
        viewModel.withdrawn.observe(this) { withdrawn ->
            if (withdrawn) {
                Toast.makeText(this, "탈퇴를 요청했습니다. 30일 안에 다시 로그인하면 탈퇴를 취소할 수 있어요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        viewModel.passwordChanged.observe(this) { changed ->
            if (changed) {
                binding.etCurrentPassword.text?.clear()
                binding.etNewPassword.text?.clear()
            }
        }
    }

    // 비밀번호를 다시 입력받아 탈퇴를 요청한다
    private fun confirmWithdraw() {
        val input = EditText(this).apply {
            hint = "비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("회원 탈퇴")
            .setMessage("탈퇴하면 모든 기기에서 로그아웃되고, 30일 뒤 회원 정보가 영구 삭제돼요. 30일 안에 다시 로그인하면 취소할 수 있어요.\n\n계속하려면 비밀번호를 입력해 주세요.")
            .setView(input)
            .setPositiveButton("탈퇴") { _, _ -> viewModel.withdraw(input.text.toString()) }
            .setNegativeButton("취소", null)
            .show()
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
