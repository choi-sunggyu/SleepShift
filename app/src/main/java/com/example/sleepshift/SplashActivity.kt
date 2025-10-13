package com.example.sleepshift

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.feature.PermissionActivity
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

    private fun determineStartDestination() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ⭐ 1단계: 권한 체크
        if (!allPermissionsGranted()) {
            navigateToPermissions()
            return
        }

        // ⭐ 2단계: 온보딩 완료 여부 확인
        val isOnboardingCompleted = sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        if (!isOnboardingCompleted) {
            navigateToOnboarding()
            return
        }

        // ⭐ 3단계: 설문조사 완료 여부 확인
        val isSurveyCompleted = sharedPrefs.getBoolean(KEY_SURVEY_COMPLETED, false)
        if (!isSurveyCompleted) {
            navigateToSurvey()
            return
        }

        // ⭐ 4단계: 모든 설정 완료 -> 홈 화면
        navigateToHome()
    }

    /**
     * ⭐ 모든 필수 권한이 허용되었는지 확인
     */
    private fun allPermissionsGranted(): Boolean {
        // ⭐ 접근성 제거! UsageStats와 알람만 체크
        return hasUsageStatsPermission() && hasExactAlarmPermission()
    }

    /**
     * ⭐ UsageStats 권한 확인
     */
    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true
        }

        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * ⭐ Accessibility 서비스 활성화 확인
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/.service.AccessibilityLockService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }

    /**
     * ⭐ 정확한 알람 권한 확인
     */
    private fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun navigateToPermissions() {
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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