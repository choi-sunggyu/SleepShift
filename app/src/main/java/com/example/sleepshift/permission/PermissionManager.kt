package com.example.sleepshift.permission

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager(
    private val activity: AppCompatActivity,
    private val onPermissionsGranted: () -> Unit
) {

    companion object {
        private const val TAG = "PermissionManager"
    }

    /**
     * ⭐⭐⭐ 모든 권한 순차적으로 요청
     * 순서: 알림 → UsageStats → 오버레이 → 정확한 알람
     */
    fun requestAllPermissions(notificationLauncher: ActivityResultLauncher<String>) {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "권한 요청 시작")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission(notificationLauncher)
        } else {
            checkUsageStatsPermission()
        }
    }

    /**
     * 1️⃣ 알림 권한 (Android 13+)
     */
    private fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            checkUsageStatsPermission()
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "✅ 알림 권한 이미 승인됨")
                checkUsageStatsPermission()
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                showNotificationPermissionRationale(launcher)
            }
            else -> {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showNotificationPermissionRationale(launcher: ActivityResultLauncher<String>) {
        AlertDialog.Builder(activity)
            .setTitle("알림 권한 필요")
            .setMessage("알람이 울릴 때 알림을 표시하기 위해 알림 권한이 필요합니다.")
            .setPositiveButton("권한 허용") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                checkUsageStatsPermission()
            }
            .show()
    }

    /**
     * 2️⃣ 사용 정보 접근 권한 (UsageStats)
     */
    fun checkUsageStatsPermission() {
        if (hasUsageStatsPermission()) {
            Log.d(TAG, "✅ 사용 정보 접근 권한 있음")
            checkOverlayPermission()
        } else {
            Log.w(TAG, "⚠️ 사용 정보 접근 권한 없음")
            showUsageStatsPermissionDialog()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true
        }

        val appOpsManager = activity.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                activity.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                activity.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsageStatsPermissionDialog() {
        AlertDialog.Builder(activity)
            .setTitle("사용 정보 접근 권한 필요")
            .setMessage(
                "다른 앱 전환을 감지하여 잠금 화면으로 복귀하기 위해\n" +
                        "사용 정보 접근 권한이 필요합니다.\n\n" +
                        "설정 화면에서 'Dozeo' 또는 'SleepShift'를 찾아 활성화해주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                openUsageStatsSettings()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                checkOverlayPermission()  // 다음 권한으로
            }
            .setCancelable(false)
            .show()
    }

    private fun openUsageStatsSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            activity.startActivity(intent)
            Toast.makeText(
                activity,
                "'Dozeo' 또는 'SleepShift'를 찾아서 활성화해주세요",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "사용 정보 접근 설정 화면 열기 실패", e)
            Toast.makeText(activity, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 3️⃣ 다른 앱 위에 표시 권한 (Overlay)
     */
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                Log.w(TAG, "⚠️ 오버레이 권한 없음")
                showOverlayPermissionDialog()
            } else {
                Log.d(TAG, "✅ 오버레이 권한 있음")
                checkAlarmPermission()
            }
        } else {
            checkAlarmPermission()
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(activity)
            .setTitle("다른 앱 위에 표시 권한 필요")
            .setMessage(
                "수면 잠금 기능을 위해\n" +
                        "'다른 앱 위에 표시' 권한이 필요합니다.\n\n" +
                        "설정 화면으로 이동하여 권한을 허용해주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                openOverlaySettings()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                checkAlarmPermission()  // 다음 권한으로
            }
            .setCancelable(false)
            .show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
                Toast.makeText(
                    activity,
                    "다른 앱 위에 표시 권한을 허용해주세요",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "오버레이 설정 화면 열기 실패", e)
                Toast.makeText(activity, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 4️⃣ 정확한 알람 권한 (Android 12+)
     */
    fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(AlarmManager::class.java)

            if (alarmManager?.canScheduleExactAlarms() != true) {
                Log.w(TAG, "⚠️ 정확한 알람 권한 없음")
                showAlarmPermissionDialog()
            } else {
                Log.d(TAG, "✅ 정확한 알람 권한 있음")
                allPermissionsGranted()
            }
        } else {
            allPermissionsGranted()
        }
    }

    private fun showAlarmPermissionDialog() {
        AlertDialog.Builder(activity)
            .setTitle("알람 권한 필요")
            .setMessage(
                "정확한 시간에 알람을 울리기 위해서는\n" +
                        "'알람 및 리마인더' 권한이 필요합니다.\n\n" +
                        "설정 화면으로 이동하여 권한을 허용해주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                openAlarmPermissionSettings()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                allPermissionsGranted()  // 일단 진행
            }
            .setCancelable(false)
            .show()
    }

    private fun openAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "알람 설정 화면 열기 실패: ${e.message}")
            }
        }
    }

    /**
     * ⭐ 모든 권한 완료
     */
    private fun allPermissionsGranted() {
        Log.d(TAG, "✅ 모든 권한 확인 완료")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        onPermissionsGranted()
    }

    /**
     * 권한 거부 시 다이얼로그
     */
    fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("권한 필요")
            .setMessage("앱이 정상적으로 작동하려면 필수 권한이 필요합니다.")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                checkUsageStatsPermission()
            }
            .show()
    }
}