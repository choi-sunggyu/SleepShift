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
            val channelDescription = "취침 시간을 알려주는 알림입니다"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * ⭐ 새로운 알고리즘: 기상 시간 고정, 취침 시간 점진적 조정
     */
    fun updateDailyAlarm(currentDay: Int) {
        val currentBedtime = sharedPreferences.getString("avg_bedtime", "01:00") ?: "01:00"
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "06:30") ?: "06:30"
        val targetSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", 420) // 7시간
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        val wakeTime = parseTime(targetWakeTime)  // 기상 시간은 항상 고정!

        // 오늘의 취침 시간 계산
        val todayBedtime = if (isCustomMode) {
            calculateGradualBedtime(
                currentDay = currentDay,
                currentBedtime = parseTime(currentBedtime),
                targetWakeTime = wakeTime,
                targetSleepMinutes = targetSleepMinutes
            )
        } else {
            // 일반 모드: 바로 목표 취침 시간
            wakeTime.minusMinutes(targetSleepMinutes.toLong())
        }

        // SharedPreferences 저장
        val bedtimeString = todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val wakeTimeString = wakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        sharedPreferences.edit()
            .putString("today_bedtime", bedtimeString)  // 오늘의 취침 시간
            .putString("today_alarm_time", wakeTimeString)  // 알람 시간 (고정)
            .putInt("current_day", currentDay)
            .apply()

        // 기상 알람 설정 (고정)
        setSystemAlarm(wakeTime)

        // 취침 알림 설정
        setBedtimeNotification(todayBedtime)

        val mode = if (isCustomMode) "커스텀 모드" else "일반 모드"
        Log.d("DailyAlarmManager", """
            ========== Day $currentDay 알람 설정 ($mode) ==========
            현재 취침: $currentBedtime
            오늘 권장 취침: $bedtimeString
            기상 알람: $wakeTimeString (고정)
            목표 수면: ${targetSleepMinutes}분
            ==========================================
        """.trimIndent())
    }

    /**
     * ⭐ 취침 시간 점진적 조정 (20분씩 당기기)
     */
    private fun calculateGradualBedtime(
        currentDay: Int,
        currentBedtime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): LocalTime {
        // 목표 취침 시간
        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        // 현재와 목표 취침 시간 차이 (분)
        val timeDifferenceMinutes = getTimeDifferenceMinutes(currentBedtime, targetBedtime)

        Log.d("DailyAlarmManager", """
            현재 취침: ${currentBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            목표 취침: ${targetBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            차이: ${timeDifferenceMinutes}분
        """.trimIndent())

        // 이미 목표에 도달했거나 목표보다 늦게 자는 경우
        if (timeDifferenceMinutes <= 0) {
            Log.d("DailyAlarmManager", "이미 목표 도달 또는 초과")
            return targetBedtime
        }

        // 조정 단계 수
        val adjustmentSteps = (timeDifferenceMinutes / ADJUSTMENT_INTERVAL_MINUTES.toDouble()).toInt()

        // Day 1부터 시작하므로 currentDay - 1
        val currentStep = min(currentDay - 1, adjustmentSteps)
        val currentAdjustment = currentStep * ADJUSTMENT_INTERVAL_MINUTES

        // 오늘의 취침 시간 = 현재 취침 - 조정량
        var todayBedtime = currentBedtime.minusMinutes(currentAdjustment.toLong())

        Log.d("DailyAlarmManager", """
            조정 단계: ${adjustmentSteps}단계 필요
            현재 단계: Day $currentDay = ${currentStep}단계
            조정량: ${currentAdjustment}분
            계산된 취침: ${todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
        """.trimIndent())

        // 목표를 넘어서면 목표로 고정
        if (isBefore(todayBedtime, targetBedtime)) {
            Log.d("DailyAlarmManager", "✅ 목표 도달!")
            todayBedtime = targetBedtime
        }

        return todayBedtime
    }

    /**
     * 시간 차이 계산 (분) - from에서 to까지 뒤로 가는 시간
     */
    private fun getTimeDifferenceMinutes(from: LocalTime, to: LocalTime): Int {
        val fromMinutes = from.hour * 60 + from.minute
        val toMinutes = to.hour * 60 + to.minute

        return if (toMinutes < fromMinutes) {
            // 예: 01:00 → 23:30 = -90분 → 1350분
            (24 * 60 - fromMinutes) + toMinutes
        } else {
            // 예: 01:00 → 02:00 = 60분 (나중에 자는 건 이상하므로 음수 처리)
            toMinutes - fromMinutes
        }
    }

    /**
     * A가 B보다 이른 시간인지 (자정 넘김 고려)
     */
    private fun isBefore(a: LocalTime, b: LocalTime): Boolean {
        // 예: 23:30이 01:00보다 이른가? → true (자정 넘김 고려)
        val diff = getTimeDifferenceMinutes(a, b)
        return diff < 0 || diff > 12 * 60  // 12시간 이상 차이면 반대로 판단
    }

    private fun setBedtimeNotification(bedtime: LocalTime) {
        val bedtimeNotificationEnabled = sharedPreferences.getBoolean("bedtime_notification_enabled", true)

        if (!bedtimeNotificationEnabled) {
            Log.d("DailyAlarmManager", "취침 알림 비활성화됨")
            cancelBedtimeNotification()
            return
        }

        val notificationTime = bedtime.minusMinutes(BEDTIME_NOTIFICATION_MINUTES.toLong())

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2000,
            intent,
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

        Log.d("DailyAlarmManager", "취침 알림 설정: ${calendar.time}")
    }

    private fun cancelBedtimeNotification() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d("DailyAlarmManager", "취침 알림 취소됨")
    }

    private fun setSystemAlarm(alarmTime: LocalTime) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000,
            intent,
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

        Log.d("DailyAlarmManager", "기상 알람 설정: ${calendar.time}")
    }

    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "시간 파싱 실패: $timeString")
            LocalTime.of(7, 0)
        }
    }
}