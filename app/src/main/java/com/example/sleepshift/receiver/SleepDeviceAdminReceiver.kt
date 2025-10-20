package com.example.sleepshift.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class SleepDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SleepDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "✅ 기기 관리자 권한 활성화됨")
        Toast.makeText(context, "✅ SleepShift 기기 관리자 권한이 활성화되었습니다", Toast.LENGTH_SHORT).show()

        val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("device_admin_enabled", true)
            .apply()

        // Device Owner인 경우 자동 Kiosk 설정
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, SleepDeviceAdminReceiver::class.java)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.setLockTaskPackages(componentName, arrayOf(context.packageName))
            Log.d(TAG, "✅ Device Owner로 Kiosk 모드 설정 완료")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "⚠️ 기기 관리자 권한 비활성화됨")
        Toast.makeText(
            context,
            "⚠️ SleepShift 기기 관리자 권한이 비활성화되었습니다\n잠금 기능이 약해질 수 있습니다",
            Toast.LENGTH_LONG
        ).show()

        val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("device_admin_enabled", false).apply()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "기기 관리자 비활성화 요청됨")
        return "SleepShift의 수면 잠금 기능을 계속 사용하시겠습니까?\n비활성화 시 홈 버튼 차단이 약해집니다."
    }
}
