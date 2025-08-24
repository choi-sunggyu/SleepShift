package com.example.sleepshift

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.NotificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 알림 채널은 초기에 한 번만 만들어두면 안전
        NotificationUtils.ensureChannel(this)

        // 온보딩 여부 체크 후 다음 화면으로 라우팅
        lifecycleScope.launch {
            // 살짝 스플래시 시간을 줘도 되고(없애도 됨)
            delay(600)

            val completed = DataStoreManager(this@SplashActivity)
                .onboardingCompleted.first()

            val next = if (completed) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, OnboardingActivity::class.java)
            }

            startActivity(next)
            finish()
        }
    }
}
