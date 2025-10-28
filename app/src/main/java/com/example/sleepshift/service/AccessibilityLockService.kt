package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.feature.LockScreenActivity

class AccessibilityLockService : AccessibilityService() {

    private val allowedPackages = setOf(
        "com.example.sleepshift",
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.android.settings"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""

            val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
            val isLocked = lockPrefs.getBoolean("isLocked", false)

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "현재 앱: $packageName")
            Log.d(TAG, "현재 클래스: $className")
            Log.d(TAG, "잠금 상태: $isLocked")

            // ⭐ 알람 시간일 때는 잠금 해제 (알람 울릴 때 자동 해제됨)
            val isAlarmTime = lockPrefs.getBoolean("is_alarm_time", false)
            if (isAlarmTime) {
                Log.d(TAG, "⏰ 알람 시간 - 차단하지 않음")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return
            }

            if (!isLocked) {
                Log.d(TAG, "잠금 상태 아님 - 감시 안 함")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return
            }

            // ⭐ AlarmActivity는 항상 허용
            if (className.contains("AlarmActivity")) {
                Log.d(TAG, "✅ AlarmActivity 감지 - 허용")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return
            }

            // 허용되지 않은 앱인 경우
            if (!isPackageAllowed(packageName)) {
                Log.w(TAG, "⚠️ 차단된 앱 감지: $packageName")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
                showLockScreen()
            } else {
                Log.d(TAG, "✅ 허용된 앱")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
        }
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        if (allowedPackages.contains(packageName)) {
            return true
        }

        if (packageName.contains("launcher", ignoreCase = true)) {
            return true
        }

        return false
    }

    private fun showLockScreen() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            Log.d(TAG, "✅ LockScreenActivity 실행")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LockScreenActivity 실행 실패", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService 중단됨")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ AccessibilityLockService 연결됨")

        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("accessibility_ever_enabled", true).apply()
        Log.d(TAG, "✅ Accessibility 서비스 연결 기록 저장")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityLockService 종료됨")
    }

    companion object {
        private const val TAG = "AccessibilityLockSvc"

        fun isEnabled(context: Context): Boolean {
            try {
                val service = "${context.packageName}/${AccessibilityLockService::class.java.name}"

                var accessibilityEnabled = 0
                try {
                    accessibilityEnabled = Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED
                    )
                } catch (e: Settings.SettingNotFoundException) {
                    Log.e(TAG, "접근성 설정을 찾을 수 없음")
                }

                if (accessibilityEnabled == 1) {
                    val settingValue = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )

                    if (!settingValue.isNullOrEmpty()) {
                        val colonSplitter = TextUtils.SimpleStringSplitter(':')
                        colonSplitter.setString(settingValue)

                        while (colonSplitter.hasNext()) {
                            val accessibilityService = colonSplitter.next()
                            if (accessibilityService.equals(service, ignoreCase = true)) {
                                Log.d(TAG, "✅ Accessibility 서비스 활성화 확인됨")
                                return true
                            }
                        }
                    }
                }

                Log.d(TAG, "❌ Accessibility 서비스 비활성화됨")
                return false

            } catch (e: Exception) {
                Log.e(TAG, "Accessibility 체크 중 오류", e)
                return false
            }
        }
    }
}