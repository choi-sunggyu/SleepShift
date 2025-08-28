package com.example.sleepshift

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.NotificationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        NotificationUtils.ensureChannel(this)

        lifecycleScope.launch {
            val completed = DataStoreManager(this@SplashActivity).onboardingCompleted.first()
            val next = if (completed) Intent(this@SplashActivity, MainActivity::class.java)
            else Intent(this@SplashActivity, OnboardingActivity::class.java)
            startActivity(next)
            finish()
        }
    }
}
