package com.example.sleepshift.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.sleepshift.LockScreenActivity

class AccessibilityLockService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityLock"
        private const val BRING_TO_FRONT_DELAY = 500L // 0.5초
    }

    // ⭐ 너무 자주 복귀하는 것을 방지 (쓰로틀링)
    private var lastBringToFrontTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ⭐ 잠금 활성화 확인
        if (!isLockActive()) return

        // ⭐ 알람이 울리는 중이면 무시
        if (isAlarmRinging()) {
            android.util.Log.d(TAG, "알람 울림 중 - 복귀 무시")
            return
        }

        // ⭐ 정상 이동 중이면 무시
        if (isNormalTransition()) {
            android.util.Log.d(TAG, "정상 이동 중 - 복귀 무시")
            return
        }

        // ⭐ 두 가지 이벤트 타입 모두 처리
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val packageName = event.packageName?.toString()

            // ⭐ 다른 앱으로 이동한 경우
            if (packageName != null &&
                packageName != this.packageName &&
                !isSystemPackage(packageName)) {

                android.util.Log.d(TAG, "⚠️ 다른 앱 감지: $packageName")
                bringLockScreenToFront()
            }
        }
    }

    /**
     * ⭐⭐⭐ 키 이벤트 가로채기 (홈/최근앱 버튼 차단)
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // ⭐ 잠금 활성화 확인
        if (!isLockActive()) return super.onKeyEvent(event)

        // ⭐ 알람이 울리는 중이면 무시
        if (isAlarmRinging()) return super.onKeyEvent(event)

        // ⭐ 정상 이동 중이면 무시
        if (isNormalTransition()) return super.onKeyEvent(event)

        // ⭐ 키 다운 이벤트만 처리
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    android.util.Log.d(TAG, "🔒 홈 버튼 차단됨")
                    bringLockScreenToFront()
                    return true  // 이벤트 소비 (버튼 동작 막음)
                }
                KeyEvent.KEYCODE_APP_SWITCH -> {
                    android.util.Log.d(TAG, "🔒 최근 앱 버튼 차단됨")
                    bringLockScreenToFront()
                    return true  // 이벤트 소비
                }
                KeyEvent.KEYCODE_BACK -> {
                    android.util.Log.d(TAG, "🔒 뒤로가기 버튼 감지 (Activity에서 처리)")
                    // 뒤로가기는 Activity의 onBackPressed에서 처리
                    return false
                }
            }
        }

        return super.onKeyEvent(event)
    }

    /**
     * ⭐ 시스템 패키지 확인 (설정, 권한 다이얼로그 등은 허용)
     */
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.android.settings") ||
                packageName.startsWith("com.android.permissioncontroller") ||
                packageName == "android" ||
                packageName.startsWith("com.google.android.packageinstaller") ||
                packageName.startsWith("com.google.android.gms") ||  // Google Play 서비스
                packageName == this.packageName  // 자기 앱의 모든 Activity 허용
    }

    /**
     * ⭐ 잠금 활성화 확인
     */
    private fun isLockActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    /**
     * ⭐ 알람이 울리는 중인지 확인
     */
    private fun isAlarmRinging(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_alarm_ringing", false)
    }

    /**
     * ⭐ 정상 이동 중인지 확인 (나이트 루틴 → 락스크린)
     */
    private fun isNormalTransition(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_going_to_lockscreen", false)
    }

    /**
     * ⭐ 잠금 해제 중인지 확인
     */
    private fun isUnlocking(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_unlocking", false)
    }

    /**
     * ⭐ LockScreen으로 복귀
     */
    private fun bringLockScreenToFront() {
        // ⭐ 잠금 해제 중이면 복귀하지 않음
        if (isUnlocking()) {
            android.util.Log.d(TAG, "잠금 해제 중 - 복귀 무시")
            return
        }

        // ⭐ 쓰로틀링: 너무 자주 호출되는 것 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBringToFrontTime < BRING_TO_FRONT_DELAY) {
            android.util.Log.d(TAG, "복귀 요청 무시 (쓰로틀링)")
            return
        }
        lastBringToFrontTime = currentTime

        try {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            android.util.Log.d(TAG, "✅ LockScreen으로 복귀")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 복귀 실패: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        android.util.Log.d(TAG, "⚠️ 서비스 중단됨")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d(TAG, "✅ 접근성 서비스 연결됨")
        android.util.Log.d(TAG, "  - 홈/최근앱 버튼 차단 활성화")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.d(TAG, "⚠️ 접근성 서비스 연결 해제됨")
        return super.onUnbind(intent)
    }
}