package com.example.sleepshift.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager( //권한 관리
    private val activity: AppCompatActivity,
    private val onPermissionsGranted: () -> Unit
) {

    fun requestAllPermissions(notificationLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission(notificationLauncher)
        } else {
            checkAlarmPermission()
        }
    }

    private fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        when {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "알림 권한 이미 승인됨")
                checkAlarmPermission()
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                showNotificationPermissionRationale(launcher)
            }
            else -> {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(AlarmManager::class.java)

            if (alarmManager?.canScheduleExactAlarms() != true) {
                Log.w(TAG, "정확한 알람 권한 없음")
                showAlarmPermissionDialog()
            } else {
                Log.d(TAG, "정확한 알람 권한 있음")
                onPermissionsGranted()
            }
        } else {
            onPermissionsGranted()
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
                checkAlarmPermission()
            }
            .show()
    }

    private fun showAlarmPermissionDialog() {
        AlertDialog.Builder(activity)
            .setTitle("알람 권한 필요")
            .setMessage(
                "정확한 시간에 알람을 울리기 위해서는 '알람 및 리마인더' 권한이 필요합니다.\n\n" +
                        "설정 화면으로 이동하여 권한을 허용해주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                openAlarmPermissionSettings()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
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

    fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("권한 필요")
            .setMessage("알람 앱이 정상적으로 작동하려면 알림 권한이 필요합니다.")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                checkAlarmPermission()
            }
            .show()
    }

    companion object {
        private const val TAG = "PermissionManager"
    }
}