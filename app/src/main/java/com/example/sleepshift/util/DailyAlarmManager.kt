package com.example.sleepshift.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.sleepshift.feature.alarm.AlarmReceiver
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min

class DailyAlarmManager(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ADJUSTMENT_INTERVAL_MINUTES = 20
        const val BEDTIME_NOTIFICATION_MINUTES = 10
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "bedtime_notification_channel"
            val channelName = "취침 알림"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * ⭐⭐⭐ 핵심 메서드: 알람 설정
     * 일회성 알람 처리 로직 추가
     * @param currentDay 현재 Day
     * @return Boolean - 성공 여부
     */
    fun updateDailyAlarm(currentDay: Int): Boolean {

        Log.d("DailyAlarmManager", "=== updateDailyAlarm 시작 (Day $currentDay) ===")

        // ⭐ 1단계: 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("DailyAlarmManager", "❌ 알람 권한 없음 - 알람 설정 중단")
                showAlarmPermissionDialog()
                return false
            }
        }

        // ⭐⭐⭐ 2단계: 일회성 알람 체크
        val isOneTimeAlarm = sharedPreferences.getBoolean("is_one_time_alarm", false)

        if (isOneTimeAlarm) {
            val oneTimeAlarmTime = sharedPreferences.getString("one_time_alarm_time", null)

            if (oneTimeAlarmTime != null) {
                Log.d("DailyAlarmManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("DailyAlarmManager", "🔔 일회성 알람 감지")
                Log.d("DailyAlarmManager", "일회성 알람 시간: $oneTimeAlarmTime")

                // ⭐⭐⭐ 1. 일회성 알람 시간으로 설정
                val alarmTime = parseTime(oneTimeAlarmTime)
                setSystemAlarm(alarmTime)

                // ⭐⭐⭐ 2. SharedPreferences에 오늘의 알람 시간 저장
                sharedPreferences.edit()
                    .putInt("current_day", currentDay)
                    .putString("today_alarm_time", oneTimeAlarmTime)
                    .apply()

                // ⭐⭐⭐ 3. 일회성 알람 플래그 제거 (다음날부터는 일반 알람으로)
                sharedPreferences.edit()
                    .putBoolean("is_one_time_alarm", false)
                    .remove("one_time_alarm_time")
                    .apply()

                Log.d("DailyAlarmManager", "✅ 일회성 알람 설정 완료: $oneTimeAlarmTime")
                Log.d("DailyAlarmManager", "✅ 일회성 알람 플래그 제거됨")
                Log.d("DailyAlarmManager", "Day $currentDay 유지")
                Log.d("DailyAlarmManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return true
            }
        }

        // ⭐ 3단계: 일반 알람 처리 (일회성 알람이 아닌 경우)
        Log.d("DailyAlarmManager", "📅 일반 알람 모드 - 점진적 조정 계산")

        val currentBedtime = sharedPreferences.getString("avg_bedtime", "04:00") ?: "04:00"
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "06:30") ?: "06:30"
        val targetSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", 420)
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        Log.d("DailyAlarmManager", """
            === 읽은 설정값 ===
            avg_bedtime: $currentBedtime
            target_wake_time: $targetWakeTime
            min_sleep_minutes: $targetSleepMinutes
            alarm_mode_custom: $isCustomMode
        """.trimIndent())

        val wakeTime = parseTime(targetWakeTime)

        val todayBedtime = if (isCustomMode) {
            calculateGradualBedtime(
                currentDay = currentDay,
                currentBedtime = parseTime(currentBedtime),
                targetWakeTime = wakeTime,
                targetSleepMinutes = targetSleepMinutes
            )
        } else {
            wakeTime.minusMinutes(targetSleepMinutes.toLong())
        }

        val bedtimeString = todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val wakeTimeString = wakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        sharedPreferences.edit()
            .putString("today_bedtime", bedtimeString)
            .putString("today_alarm_time", wakeTimeString)
            .putInt("current_day", currentDay)
            .apply()

        setSystemAlarm(wakeTime)
        setBedtimeNotification(todayBedtime)

        Log.d("DailyAlarmManager", """
            ========== Day $currentDay 결과 ==========
            오늘 취침: $bedtimeString
            기상 알람: $wakeTimeString
            ==========================================
        """.trimIndent())

        return true
    }

    /**
     * ⭐ 권한 다이얼로그
     */
    private fun showAlarmPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            try {
                context.startActivity(intent)
                Toast.makeText(context, "알람 권한을 허용해주세요", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("DailyAlarmManager", "권한 설정 화면 열기 실패: ${e.message}")
            }
        }
    }

    /**
     * 점진적 취침 시간 계산
     */
    private fun calculateGradualBedtime(
        currentDay: Int,
        currentBedtime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): LocalTime {
        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        val currentMinutes = currentBedtime.hour * 60 + currentBedtime.minute
        val targetMinutes = targetBedtime.hour * 60 + targetBedtime.minute

        // 자정 넘김 계산
        val diffMinutes = if (currentMinutes >= targetMinutes) {
            currentMinutes - targetMinutes
        } else {
            currentMinutes + (1440 - targetMinutes)
        }

        Log.d("DailyAlarmManager", """
        현재 취침: ${currentBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))} (${currentMinutes}분)
        목표 취침: ${targetBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))} (${targetMinutes}분)
        차이: ${diffMinutes}분
    """.trimIndent())

        if (diffMinutes <= 0) {
            Log.d("DailyAlarmManager", "이미 목표 도달")
            return targetBedtime
        }

        val totalSteps = (diffMinutes.toDouble() / ADJUSTMENT_INTERVAL_MINUTES).toInt()
        val currentStep = currentDay
        val actualStep = min(currentStep, totalSteps)
        val adjustment = actualStep * ADJUSTMENT_INTERVAL_MINUTES

        var newMinutes = currentMinutes - adjustment
        if (newMinutes < 0) {
            newMinutes += 1440
        }

        val todayBedtime = LocalTime.of(newMinutes / 60, newMinutes % 60)

        Log.d("DailyAlarmManager", """
        총 ${totalSteps}단계 필요
        Day ${currentDay} = ${actualStep}단계
        조정량: ${adjustment}분 (첫날부터 ${ADJUSTMENT_INTERVAL_MINUTES}분씩 당김)
        결과 취침: ${todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
    """.trimIndent())

        return todayBedtime
    }

    /**
     * 취침 알림 설정
     */
    private fun setBedtimeNotification(bedtime: LocalTime) {
        val enabled = sharedPreferences.getBoolean("bedtime_notification_enabled", true)
        if (!enabled) {
            cancelBedtimeNotification()
            return
        }

        val notificationTime = bedtime.minusMinutes(BEDTIME_NOTIFICATION_MINUTES.toLong())

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, notificationTime.hour)
            set(Calendar.MINUTE, notificationTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d("DailyAlarmManager", "📢 취침 알림 설정: ${calendar.time}")
    }

    /**
     * 취침 알림 취소
     */
    private fun cancelBedtimeNotification() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("DailyAlarmManager", "취침 알림 취소됨")
    }

    /**
     * ⭐ 시스템 알람 설정
     */
    private fun setSystemAlarm(alarmTime: LocalTime) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 현재 시간보다 과거면 다음날로
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
                Log.d("DailyAlarmManager", "⏰ 설정 시간이 지나서 내일로 설정")
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Log.d("DailyAlarmManager", """
            ========== 알람 설정 완료 ==========
            📅 설정 시간: ${calendar.time}
            🕐 현재 시간: ${Date()}
            🌍 타임존: ${calendar.timeZone.displayName}
            📌 ${if (calendar.get(Calendar.DAY_OF_MONTH) > Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) "내일" else "오늘"}
            =====================================
        """.trimIndent())
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "❌ 알람 설정 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 시간 파싱
     */
    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "⚠️ 시간 파싱 실패: $timeString, 기본값 07:00 사용")
            LocalTime.of(7, 0)
        }
    }

    /**
     * ⭐ 알람 취소
     */
    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("DailyAlarmManager", "🔕 알람 취소됨")
    }
}