package com.example.sleepshift.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sleepshift.receiver.SleepDeviceAdminReceiver

/**
 * Device Admin 관리 헬퍼 클래스
 * - 기기 관리자 권한으로 홈 버튼 차단 강화
 */
class DeviceAdminHelper(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName = ComponentName(context, SleepDeviceAdminReceiver::class.java)

    companion object {
        const val REQUEST_CODE_ENABLE_ADMIN = 101
        private const val TAG = "DeviceAdminHelper"
    }

    /**
     * Device Admin 활성화 여부 확인
     */
    fun isAdminActive(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "관리자 활성화 확인 실패", e)
            false
        }
    }

    /**
     * Device Admin 권한 요청
     */
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
                "수면 잠금 기능이 홈 버튼을 완전히 차단하려면 기기 관리자 권한이 필요합니다.\n\n" +
                        "걱정하지 마세요! 이 권한은:\n" +
                        "• 수면 시간에만 사용됩니다\n" +
                        "• 언제든지 설정에서 해제할 수 있습니다\n" +
                        "• 개인정보에 접근하지 않습니다"
            )
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            Log.d(TAG, "기기 관리자 권한 요청 시작")
        } catch (e: Exception) {
            Log.e(TAG, "권한 요청 실패", e)
        }
    }

    /**
     * 화면 즉시 잠금 (필요시 사용)
     */
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

    /**
     * Device Admin 권한 제거 (설정에서 사용)
     */
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

    /**
     * Device Admin 설정 화면으로 이동
     */
    fun openDeviceAdminSettings(activity: Activity) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "설정 화면 열기 실패", e)
        }
    }
}