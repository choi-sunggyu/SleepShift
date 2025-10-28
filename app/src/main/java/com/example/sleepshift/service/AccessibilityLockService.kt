package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.feature.LockScreenActivity

class AccessibilityLockService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityLockSvc"
    }

    // 허용할 패키지 목록
    private val allowedPackages = setOf(
        "com.example.sleepshift",  // 우리 앱
        "com.android.systemui"      // 시스템 UI (알림, 상태바 등)
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        // ⭐ 서비스 설정 강화
        serviceInfo = serviceInfo.apply {
            flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS  // 키 이벤트 가로채기
        }

        Log.d(TAG, "✅ AccessibilityLockService 연결됨 (강화 모드)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 잠금 상태 확인 (통일된 플래그 사용)
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        val isLocked = lockPrefs.getBoolean("isLocked", false)

        if (!isLocked) return

        // ⭐ 알람 시간 예외
        val isAlarmTime = lockPrefs.getBoolean("is_alarm_time", false)
        if (isAlarmTime) {
            Log.d(TAG, "⏰ 알람 시간 - 차단 안 함")
            return
        }

        when (event.eventType) {
            // 앱/창 전환 감지
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(event)
            }

            // 시스템 UI 이벤트 감지
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handleWindowsChanged(event)
            }
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        Log.d(TAG, "앱 전환: $packageName")

        // AlarmActivity는 항상 허용
        if (className.contains("AlarmActivity")) {
            Log.d(TAG, "✅ AlarmActivity 허용")
            return
        }

        // 허용되지 않은 앱이면 LockScreen으로 복귀
        if (!isPackageAllowed(packageName)) {
            Log.w(TAG, "⚠️ 차단: $packageName")
            showLockScreen()
        }
    }

    private fun handleWindowsChanged(event: AccessibilityEvent) {
        // 최근 앱, 알림창 등이 열리는 것 감지
        val packageName = event.packageName?.toString() ?: return

        if (!isPackageAllowed(packageName)) {
            Log.w(TAG, "⚠️ 시스템 UI 차단 시도: $packageName")
            showLockScreen()
        }
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        // 우리 앱과 시스템 UI만 허용
        return allowedPackages.any { packageName.contains(it) }
    }

    private fun showLockScreen() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)  // 부드러운 전환
            }
            startActivity(intent)
            Log.d(TAG, "✅ LockScreen 복귀")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LockScreen 복귀 실패", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "서비스 중단됨")
    }

    // ⭐ 키 이벤트 가로채기 (홈 버튼 등)
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        val isLocked = lockPrefs.getBoolean("isLocked", false)

        if (!isLocked) return false

        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_HOME,           // 홈 버튼
            android.view.KeyEvent.KEYCODE_APP_SWITCH,     // 최근 앱 버튼
            android.view.KeyEvent.KEYCODE_BACK -> {       // 뒤로가기
                Log.w(TAG, "⚠️ 시스템 버튼 차단: ${event.keyCode}")
                showLockScreen()
                return true  // 이벤트 소비 (차단)
            }
        }

        return false  // 다른 키는 허용
    }
}