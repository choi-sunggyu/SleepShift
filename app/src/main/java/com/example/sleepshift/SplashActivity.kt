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
        // 다크 모드 고정 (원치 않으면 이 줄 삭제)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 앱 진입 흐름 결정
        lifecycleScope.launch {
            // 스플래시 화면 최소 표시 시간
            delay(SPLASH_DELAY)

            determineStartDestination()
        }
    }

    private suspend fun determineStartDestination() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 온보딩 완료 여부 확인
        val isOnboardingCompleted = sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        if (!isOnboardingCompleted) {
            // 온보딩이 완료되지 않았으면 온보딩으로 이동
            navigateToOnboarding()
            return
        }

        // 2. 초기 설문 완료 여부 확인
        val isSurveyCompleted = sharedPrefs.getBoolean(KEY_SURVEY_COMPLETED, false)

        if (!isSurveyCompleted) {
            // 온보딩은 완료했지만 설문이 완료되지 않았으면 설문으로 이동
            navigateToSurvey()
            return
        }

        // 3. 저장된 수면 설정 존재 여부 확인 (기존 로직 유지)
        try {
            val repo = SleepRepository(this@SplashActivity)
            val hasSettings = repo.getSettings() != null

            if (hasSettings) {
                // 모든 설정이 완료되었으면 홈으로 이동
                navigateToHome()
            } else {
                // 설정이 없으면 설문으로 이동 (혹시 모를 경우를 대비)
                navigateToSurvey()
            }
        } catch (e: Exception) {
            // 오류 발생 시 안전하게 설문으로 이동
            e.printStackTrace()
            navigateToSurvey()
        }
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()

        // 페이드 인/아웃 애니메이션
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

    // 온보딩 완료 시 호출할 수 있는 헬퍼 메소드 (다른 Activity에서 사용)
    companion object {
        private const val SPLASH_DELAY = 1500L // 1.5초 스플래시 화면 표시
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