package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.LockScreenActivity

/**
 * 접근성 서비스를 사용해 포그라운드 앱 변화를 감지합니다.
 * - 잠금 모드가 활성화된 상태에서 다른 앱이 포그라운드가 되면 LockScreenActivity를 다시 띄움
 */
class LockAccessibilityService : AccessibilityService() {

    private val TAG = "LockA11y"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            // 잠금 중인지 확인
            val prefs = getSharedPreferences("SleepShiftPrefs", MODE_PRIVATE)
            val locked = prefs.getBoolean("lock_screen_active", false)
            if (!locked) return

            // 현재 포커스가 우리 앱이 아닌 경우 복귀 시도
            val packageName = event.packageName?.toString() ?: return
            if (packageName != applicationContext.packageName) {
                Log.d(TAG, "다른 앱 포그라운드 감지: $packageName -> LockScreen 복귀")
                bringLockScreenFront()
            }
        }
    }

    private fun bringLockScreenFront() {
        try {
            val i = Intent(this, LockScreenActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "LockScreen 복귀 실패: ${e.message}")
        }
    }

    override fun onInterrupt() {
        // no-op
    }
}