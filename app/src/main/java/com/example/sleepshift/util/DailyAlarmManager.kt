package com.example.sleepshift.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

    fun updateDailyAlarm(currentDay: Int) {
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
    }

    private fun calculateGradualBedtime(
        currentDay: Int,
        currentBedtime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): LocalTime {
        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        val currentMinutes = currentBedtime.hour * 60 + currentBedtime.minute
        val targetMinutes = targetBedtime.hour * 60 + targetBedtime.minute

        // ⭐ 자정 넘김 계산 수정
        val diffMinutes = if (currentMinutes >= targetMinutes) {
            // 같은 날: 4:00 → 2:00 = 120분 앞당기기
            currentMinutes - targetMinutes
        } else {
            // 자정 넘김: 4:00 → 23:30
            // 4:00(240분)에서 0:00까지 = 240분
            // 0:00에서 23:30(1410분)까지 되돌아가기 = 1440 - 1410 = 30분
            // 합계: 240 + 30 = 270분
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
        val currentStep = currentDay - 1  // Day 1 = 0
        val actualStep = min(currentStep, totalSteps)
        val adjustment = actualStep * ADJUSTMENT_INTERVAL_MINUTES

        // 현재에서 adjustment 빼기 (자정 넘김 처리)
        var newMinutes = currentMinutes - adjustment
        if (newMinutes < 0) {
            newMinutes += 1440  // 전날로
        }

        val todayBedtime = LocalTime.of(newMinutes / 60, newMinutes % 60)

        Log.d("DailyAlarmManager", """
        총 ${totalSteps}단계 필요
        Day ${currentDay} = ${actualStep}단계
        조정량: ${adjustment}분
        결과 취침: ${todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
    """.trimIndent())

        return todayBedtime
    }

    /**
     * ⭐ 취침 시간 차이 계산 (자정 넘김 고려)
     * 예: 4:00 → 23:30 = 270분 (4:00에서 23:30으로 앞당기려면 270분 필요)
     */
    private fun calculateBedtimeDifference(current: LocalTime, target: LocalTime): Int {
        val currentMinutes = current.hour * 60 + current.minute
        val targetMinutes = target.hour * 60 + target.minute

        // target이 더 작으면 전날 밤 (예: 23:30 < 4:00)
        return if (targetMinutes < currentMinutes) {
            // 4:00 → 23:30 = 270분
            currentMinutes - targetMinutes
        } else {
            // target이 더 크면 나중에 자는 건데, 이건 앞당기는 게 아니므로 음수
            -(targetMinutes - currentMinutes)
        }
    }

    /**
     * ⭐ 시간에서 분을 빼기 (자정 넘김 처리)
     * 예: 4:00 - 270분 = 23:30
     */
    private fun subtractMinutesWithMidnight(time: LocalTime, minutes: Int): LocalTime {
        val totalMinutes = time.hour * 60 + time.minute
        var newMinutes = totalMinutes - minutes

        // 음수면 전날로
        if (newMinutes < 0) {
            newMinutes += 24 * 60
        }

        return LocalTime.of(newMinutes / 60, newMinutes % 60)
    }

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

        Log.d("DailyAlarmManager", "취침 알림: ${calendar.time}")
    }

    private fun cancelBedtimeNotification() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun setSystemAlarm(alarmTime: LocalTime) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d("DailyAlarmManager", "기상 알람: ${calendar.time}")
    }

    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "파싱 실패: $timeString")
            LocalTime.of(7, 0)
        }
    }
}