package com.example.sleepshift

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.feature.onboarding.OnboardingActivity
import com.example.sleepshift.feature.survey.SurveyActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 다크 모드 고정
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 앱 진입 흐름 결정
        lifecycleScope.launch {
            delay(SPLASH_DELAY)
            determineStartDestination()
        }
    }

    private suspend fun determineStartDestination() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 온보딩 완료 여부 확인
        val isOnboardingCompleted = sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        if (!isOnboardingCompleted) {
            // 첫 실행 -> 온보딩
            navigateToOnboarding()
            return
        }

        // 2. 설문조사 완료 여부 확인
        val isSurveyCompleted = sharedPrefs.getBoolean(KEY_SURVEY_COMPLETED, false)

        if (!isSurveyCompleted) {
            // 온보딩 완료했지만 설문 미완료 -> 설문조사
            navigateToSurvey()
            return
        }

        // 3. 모든 설정 완료 -> 홈 화면
        navigateToHome()
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun navigateToSurvey() {
        val intent = Intent(this, SurveyActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val SPLASH_DELAY = 1500L
        private const val PREFS_NAME = "SleepShiftPrefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SURVEY_COMPLETED = "survey_completed"

        fun markOnboardingCompleted(context: Context) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }

        fun markSurveyCompleted(context: Context) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean(KEY_SURVEY_COMPLETED, true).apply()
        }

        fun isOnboardingCompleted(context: Context): Boolean {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }

        fun isSurveyCompleted(context: Context): Boolean {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean(KEY_SURVEY_COMPLETED, false)
        }

        // 개발/테스트용: 모든 설정 초기화
        fun resetAllSettings(context: Context) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()
        }
    }
}