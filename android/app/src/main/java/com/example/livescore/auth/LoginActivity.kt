package com.example.livescore.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.livescore.databinding.ActivityLoginBinding

// 로그인. 개인화 기능에서 로그인을 요구해 열렸다면 성공 후 돌아가고(RESULT_OK), 아니면 닫힌다.
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            viewModel.login(binding.etEmail.text.toString(), binding.etPassword.text.toString())
        }
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.tvBrowse.setOnClickListener { finish() }

        viewModel.loading.observe(this) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !loading
        }
        viewModel.message.observe(this) { message ->
            binding.tvMessage.text = message.orEmpty()
            binding.tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.loggedIn.observe(this) { loggedIn ->
            if (loggedIn) {
                Toast.makeText(this, "${SessionManager.member.value?.nickname}님, 환영합니다.", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
