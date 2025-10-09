package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.feature.LockScreenActivity

class AccessibilityLockService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()

            // LockScreen이 아닌 다른 앱이 실행되면 LockScreen으로 복귀
            if (packageName != null &&
                packageName != this.packageName &&
                isLockActive()) {
                bringLockScreenToFront()
            }
        }
    }

    private fun isLockActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    private fun bringLockScreenToFront() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            android.util.Log.d("AccessibilityLock", "LockScreen으로 복귀")
        } catch (e: Exception) {
            android.util.Log.e("AccessibilityLock", "복귀 실패: ${e.message}")
        }
    }

    override fun onInterrupt() {
        // 필수 오버라이드
        android.util.Log.d("AccessibilityLock", "서비스 중단됨")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("AccessibilityLock", "서비스 연결됨")
    }
}