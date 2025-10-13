package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.feature.LockScreenActivity

class AccessibilityLockService : AccessibilityService() {

    // ⭐ 너무 자주 복귀하는 것을 방지 (쓰로틀링)
    private var lastBringToFrontTime = 0L
    private val BRING_TO_FRONT_DELAY = 500L // 0.5초

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ⭐ 두 가지 이벤트 타입 모두 처리
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val packageName = event.packageName?.toString()

            // ⭐ 시스템 UI는 제외 (설정 화면, 권한 다이얼로그 등)
            if (packageName != null &&
                packageName != this.packageName &&
                !isSystemPackage(packageName) &&
                isLockActive()) {

                android.util.Log.d("AccessibilityLock", "⚠️ 다른 앱 감지: $packageName")
                bringLockScreenToFront()
            }
        }
    }

    /**
     * ⭐ 시스템 패키지 확인 (설정, 권한 다이얼로그 등은 허용)
     */
    private fun isSystemPackage(packageName: String): Boolean {
        // ⭐ AlarmActivity도 예외 처리
        return packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.android.settings") ||
                packageName.startsWith("com.android.permissioncontroller") ||
                packageName == "android" ||
                packageName.startsWith("com.google.android.packageinstaller") ||
                packageName == this.packageName  // ⭐ 자기 앱의 모든 Activity 허용
    }

    private fun isLockActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    private fun bringLockScreenToFront() {
        // ⭐ 쓰로틀링: 너무 자주 호출되는 것 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBringToFrontTime < BRING_TO_FRONT_DELAY) {
            android.util.Log.d("AccessibilityLock", "복귀 요청 무시 (쓰로틀링)")
            return
        }
        lastBringToFrontTime = currentTime

        try {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT  // ⭐ 추가
            startActivity(intent)
            android.util.Log.d("AccessibilityLock", "✅ LockScreen으로 복귀")
        } catch (e: Exception) {
            android.util.Log.e("AccessibilityLock", "❌ 복귀 실패: ${e.message}")
        }
    }

    override fun onInterrupt() {
        android.util.Log.d("AccessibilityLock", "서비스 중단됨")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("AccessibilityLock", "✅ 접근성 서비스 연결됨")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.d("AccessibilityLock", "⚠️ 접근성 서비스 연결 해제됨")
        return super.onUnbind(intent)
    }
}