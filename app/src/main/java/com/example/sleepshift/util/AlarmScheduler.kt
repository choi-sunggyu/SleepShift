package com.example.sleepshift.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 알람 권한 확인
     */
    fun hasAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 알람 권한 설정 화면으로 이동
     */
    fun openAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "알람 권한 설정 화면 열기 실패: ${e.message}")
                Toast.makeText(context, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 즉시 알람 설정
     */
    fun scheduleAlarm(hour: Int, minute: Int): Boolean {
        if (!hasAlarmPermission()) {
            Toast.makeText(
                context,
                "알람 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
            openAlarmPermissionSettings()
            return false
        }

        return try {
            val intent = Intent(context, com.example.sleepshift.feature.alarm.AlarmReceiver::class.java).apply {
                action = "com.example.sleepshift.ALARM_TRIGGER"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // 현재 시간보다 과거면 다음날로
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "알람 설정 완료: ${alarmTime.time}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "알람 설정 실패: ${e.message}")
            Toast.makeText(context, "알람 설정 실패", Toast.LENGTH_SHORT).show()
            false
        }
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE_ALARM = 1000
    }
}