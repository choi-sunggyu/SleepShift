package com.example.sleepshift

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.SleepRepository
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 다크 고정 (원하면 제거 가능)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val repo = SleepRepository(this@SplashActivity)
            val settings = repo.getSettings()
            val next = if (settings == null) {
                Intent(this@SplashActivity, com.example.sleepshift.feature.InitSurveyActivity::class.java)
            } else {
                Intent(this@SplashActivity, com.example.sleepshift.feature.HomeActivity::class.java)
            }
            startActivity(next)
            finish()
        }
    }
}
