package com.example.sleepshift.feature.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleepshift.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "수신됨: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("AlarmReceiver", "부팅 완료 - 알람 재설정")
                restoreAlarms(context)
                return
            }
            "com.example.sleepshift.ALARM_TRIGGER" -> {
                Log.d("AlarmReceiver", "기상 알람 트리거")
                triggerAlarm(context)
            }
            "com.example.sleepshift.BEDTIME_NOTIFICATION" -> {
                Log.d("AlarmReceiver", "취침 알림 트리거")
                showBedtimeNotification(context)
            }
            else -> {
                Log.d("AlarmReceiver", "기본 알람 처리")
                triggerAlarm(context)
            }
        }
    }

    /**
     * ⭐ 취침 알림 표시
     */
    private fun showBedtimeNotification(context: Context) {
        val sharedPref = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val bedtimeNotificationEnabled = sharedPref.getBoolean("bedtime_notification_enabled", true)

        if (!bedtimeNotificationEnabled) {
            Log.d("AlarmReceiver", "취침 알림 비활성화됨")
            return
        }

        val avgBedtime = sharedPref.getString("avg_bedtime", "23:00") ?: "23:00"

        // 홈 화면으로 이동하는 Intent
        val intent = Intent(context, com.example.sleepshift.feature.home.HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 생성
        val notification = NotificationCompat.Builder(context, "bedtime_notification_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🌙 취침 시간이 다가옵니다")
            .setContentText("${avgBedtime}에 잠자리에 들 시간입니다. 준비해주세요!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("곧 ${avgBedtime}에 잠자리에 들 시간입니다.\n\n지금부터 조명을 줄이고 화면 사용을 줄여보세요."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2001, notification)

        Log.d("AlarmReceiver", "취침 알림 표시됨")

        // ⭐ 다음 날 취침 알림 재설정
        val alarmManager = com.example.sleepshift.util.DailyAlarmManager(context)
        val currentDay = getCurrentDay(sharedPref)
        alarmManager.updateDailyAlarm(currentDay)
    }

    private fun triggerAlarm(context: Context) {
        val sharedPref = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val isAlarmEnabled = sharedPref.getBoolean("alarm_enabled", true)

        if (!isAlarmEnabled) {
            Log.d("AlarmReceiver", "알람이 비활성화되어 있음")
            return
        }

        val todayAlarmTime = sharedPref.getString("today_alarm_time", "07:00")
        Log.d("AlarmReceiver", "설정된 알람 시간: $todayAlarmTime")

        // ⭐ 알람 시간 기록 제거 (더 이상 필요 없음)
        sharedPref.edit()
            .putLong("last_alarm_triggered", System.currentTimeMillis())
            .apply()

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }

        try {
            context.startActivity(alarmIntent)
            Log.d("AlarmReceiver", "알람 화면 시작 성공")

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "알람 화면 시작 실패: ${e.message}")
        }
    }

    private fun restoreAlarms(context: Context) {
        try {
            val sharedPref = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            val surveyCompleted = sharedPref.getBoolean("survey_completed", false)

            if (!surveyCompleted) {
                Log.d("AlarmReceiver", "설문조사 미완료 - 알람 재설정 생략")
                return
            }

            val alarmManager = com.example.sleepshift.util.DailyAlarmManager(context)
            val currentDay = getCurrentDay(sharedPref)

            alarmManager.updateDailyAlarm(currentDay)
            Log.d("AlarmReceiver", "부팅 후 알람 재설정 완료 - Day $currentDay")

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "알람 재설정 중 오류: ${e.message}")
        }
    }

    private fun getCurrentDay(sharedPref: android.content.SharedPreferences): Int {
        val installDate = sharedPref.getLong("app_install_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - installDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            else -> daysDiff
        }
    }
}