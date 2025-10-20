package com.example.sleepshift.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sleepshift.receiver.SleepDeviceAdminReceiver

class DeviceAdminHelper(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName = ComponentName(context, SleepDeviceAdminReceiver::class.java)

    companion object {
        const val REQUEST_CODE_ENABLE_ADMIN = 101
        private const val TAG = "DeviceAdminHelper"
    }

    fun isAdminActive(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "관리자 활성화 확인 실패", e)
            false
        }
    }

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun requestAdminPermission(activity: Activity) {
        if (isAdminActive()) {
            Log.d(TAG, "✅ 이미 기기 관리자 권한 활성화됨")
            return
        }
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "수면 잠금 기능이 홈 버튼을 완전히 차단하려면 기기 관리자 권한이 필요합니다."
            )
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            Log.d(TAG, "기기 관리자 권한 요청 시작")
        } catch (e: Exception) {
            Log.e(TAG, "권한 요청 실패", e)
        }
    }

    fun prepareKioskMode() {
        if (isDeviceOwner()) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(context.packageName))
            Log.d(TAG, "✅ Kiosk 모드 설정 완료 (Device Owner)")
        } else {
            Log.w(TAG, "⚠️ Device Owner 아님 - 홈 버튼 차단 불가")
        }
    }

    fun startKioskMode(activity: Activity) {
        try {
            activity.startLockTask()
            Log.d(TAG, "✅ 잠금 모드 시작됨")
        } catch (e: Exception) {
            Log.e(TAG, "잠금 모드 시작 실패", e)
        }
    }

    fun stopKioskMode(activity: Activity) {
        try {
            activity.stopLockTask()
            Log.d(TAG, "잠금 모드 해제됨")
        } catch (e: Exception) {
            Log.e(TAG, "잠금 모드 해제 실패", e)
        }
    }

    fun lockScreenNow() {
        if (!isAdminActive()) {
            Log.w(TAG, "기기 관리자 권한 없음 - 화면 잠금 불가")
            return
        }
        try {
            devicePolicyManager.lockNow()
            Log.d(TAG, "✅ 화면 잠금 실행")
        } catch (e: Exception) {
            Log.e(TAG, "화면 잠금 실패", e)
        }
    }

    fun removeAdmin() {
        if (!isAdminActive()) {
            Log.d(TAG, "이미 비활성화됨")
            return
        }
        try {
            devicePolicyManager.removeActiveAdmin(componentName)
            Log.d(TAG, "✅ 기기 관리자 권한 제거됨")
        } catch (e: Exception) {
            Log.e(TAG, "권한 제거 실패", e)
        }
    }

    fun openDeviceAdminSettings(activity: Activity) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "설정 화면 열기 실패", e)
        }
    }
}
