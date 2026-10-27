package com.example.livescore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2초

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({

            // 로그인 여부와 관계없이 메인으로 간다. 저장된 로그인이 없으면 게스트로 시작한다.
            startActivity(Intent(this, MainActivity::class.java))
            finish()

        }, SPLASH_DELAY)
    }
}