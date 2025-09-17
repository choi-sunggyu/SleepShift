package com.example.sleepshift

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.feature.InitSurveyActivity
import com.google.android.ads.mediationtestsuite.activities.HomeActivity
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 다크 모드 고정 (원치 않으면 이 줄 삭제)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 저장된 설정 존재 여부에 따라 초기 진입 화면 분기
        lifecycleScope.launch {
            val repo = SleepRepository(this@SplashActivity)
            val hasSettings = repo.getSettings() != null

            val next = if (hasSettings) {
                Intent(this@SplashActivity, HomeActivity::class.java)
            } else {
                Intent(this@SplashActivity, InitSurveyActivity::class.java)
            }
            startActivity(next)
            finish()
        }
    }
}
