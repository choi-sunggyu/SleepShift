package com.example.sleepshift.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver
 * - 기기 관리자 권한 관리
 */
class SleepDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SleepDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "✅ 기기 관리자 권한 활성화됨")
        Toast.makeText(
            context,
            "✅ SleepShift 기기 관리자 권한이 활성화되었습니다",
            Toast.LENGTH_SHORT
        ).show()

        // SharedPreferences에 기록
        val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("device_admin_enabled", true)
            .putLong("device_admin_enabled_time", System.currentTimeMillis())
            .apply()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "⚠️ 기기 관리자 권한 비활성화됨")
        Toast.makeText(
            context,
            "⚠️ SleepShift 기기 관리자 권한이 비활성화되었습니다\n잠금 기능이 약해질 수 있습니다",
            Toast.LENGTH_LONG
        ).show()

        // SharedPreferences 업데이트
        val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("device_admin_enabled", false)
            .apply()
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "비밀번호 변경됨")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "비밀번호 입력 실패")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "비밀번호 입력 성공")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "기기 관리자 비활성화 요청됨")
        return "SleepShift의 수면 잠금 기능을 계속 사용하시겠습니까?\n\n" +
                "비활성화하면 홈 버튼 차단이 약해집니다."
    }
}