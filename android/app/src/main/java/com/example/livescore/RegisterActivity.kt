package com.example.livescore

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvBackToLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etEmail = findViewById(R.id.etRegEmail)
        etPassword = findViewById(R.id.etRegPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        // 가입 완료 버튼 클릭
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                performRegister(email, password)
            }
        }

        // 로그인 화면으로 돌아가기
        tvBackToLogin.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
    }

    private fun performRegister(email: String, password: String) {
        val registerData = mapOf("email" to email, "password" to password)

        RetrofitClient.apiService.register(registerData).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    val message = response.body() ?: ""
                    Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()

                    if (message == "회원가입이 완료되었습니다.") {
                        // 성공 시 로그인 화면으로 이동
                        finish()
                    }
                } else {
                    Toast.makeText(this@RegisterActivity, "서버 오류 발생", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e("RegisterActivity", "통신 실패: ${t.message}")
                Toast.makeText(this@RegisterActivity, "네트워크 연결을 확인하세요.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}