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
        const val MAX_DIFFERENCE_HOURS = 1.0
        const val DEFAULT_SLEEP_HOURS = 8
        const val BEDTIME_NOTIFICATION_MINUTES = 10  // 취침 10분 전
    }

    init {
        createNotificationChannel()
    }

    /**
     * 알림 채널 생성 (Android 8.0 이상)
     */
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
     * 현재 Day의 알람 업데이트
     */
    fun updateDailyAlarm(currentDay: Int) {
        val avgBedtime = sharedPreferences.getString("avg_bedtime", "23:00") ?: "23:00"
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "07:00") ?: "07:00"
        val minSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", 360)
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        // ⭐ 모드에 따라 알람 시간 계산
        val todayAlarmTime = if (isCustomMode) {
            // 커스텀 모드: 점진적 조정
            calculateDailyAlarmTime(
                currentDay = currentDay,
                avgBedtime = parseTime(avgBedtime),
                targetWakeTime = parseTime(targetWakeTime),
                minSleepMinutes = minSleepMinutes
            )
        } else {
            // 일반 모드: 바로 목표 시간 적용
            parseTime(targetWakeTime)
        }

        val alarmTimeString = todayAlarmTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        sharedPreferences.edit()
            .putString("today_alarm_time", alarmTimeString)
            .putInt("current_day", currentDay)
            .apply()

        // 기상 알람 설정
        setSystemAlarm(todayAlarmTime)

        // ⭐ 취침 알림 설정
        setBedtimeNotification(parseTime(avgBedtime))

        val mode = if (isCustomMode) "커스텀 모드" else "일반 모드"
        Log.d("DailyAlarmManager", """
            ========== Day $currentDay 알람 계산 ($mode) ==========
            평균 취침: $avgBedtime
            목표 기상: $targetWakeTime
            최소 수면: $minSleepMinutes
            계산된 알람: $alarmTimeString
            ==========================================
        """.trimIndent())
    }

    /**
     * ⭐ 취침 10분 전 알림 설정
     */
    private fun setBedtimeNotification(bedtime: LocalTime) {
        val bedtimeNotificationEnabled = sharedPreferences.getBoolean("bedtime_notification_enabled", true)

        if (!bedtimeNotificationEnabled) {
            Log.d("DailyAlarmManager", "취침 알림 비활성화됨")
            cancelBedtimeNotification()
            return
        }

        // 취침 10분 전 시간 계산
        val notificationTime = bedtime.minusMinutes(BEDTIME_NOTIFICATION_MINUTES.toLong())

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.BEDTIME_NOTIFICATION"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2000,  // 다른 ID 사용
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 오늘 또는 내일의 취침 알림 시간 설정
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, notificationTime.hour)
            set(Calendar.MINUTE, notificationTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 이미 지난 시간이면 내일로 설정
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d("DailyAlarmManager", "취침 알림 설정 완료: ${calendar.time} (취침 10분 전)")
    }

    /**
     * 취침 알림 취소
     */
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

    /**
     * 알람 시간 계산 메인 로직 (커스텀 모드용)
     */
    private fun calculateDailyAlarmTime(
        currentDay: Int,
        avgBedtime: LocalTime,
        targetWakeTime: LocalTime,
        minSleepMinutes: Int
    ): LocalTime {
        val currentWakeTime = avgBedtime.plusMinutes((DEFAULT_SLEEP_HOURS * 60).toLong())
        val timeDifferenceMinutes = getTimeDifferenceMinutes(currentWakeTime, targetWakeTime)
        val timeDifferenceHours = timeDifferenceMinutes / 60.0

        Log.d("DailyAlarmManager", """
            현재 기상시간(추정): ${currentWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            목표 기상시간: ${targetWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            차이: ${timeDifferenceMinutes}분 (${String.format("%.1f", timeDifferenceHours)}시간)
        """.trimIndent())

        return when {
            timeDifferenceHours > MAX_DIFFERENCE_HOURS -> {
                val adjustedTime = calculateGradualAdjustment(
                    currentDay = currentDay,
                    currentWakeTime = currentWakeTime,
                    targetWakeTime = targetWakeTime,
                    avgBedtime = avgBedtime,
                    minSleepMinutes = minSleepMinutes
                )
                Log.d("DailyAlarmManager", "케이스 1: 점진적 조정 (20분씩)")
                adjustedTime
            }
            timeDifferenceHours > 0 && timeDifferenceHours <= MAX_DIFFERENCE_HOURS -> {
                Log.d("DailyAlarmManager", "케이스 2: 1시간 이내 - 바로 목표 적용")
                targetWakeTime
            }
            else -> {
                Log.d("DailyAlarmManager", "케이스 3: 목표가 늦음 - 즉시 적용")
                targetWakeTime
            }
        }
    }

    private fun calculateGradualAdjustment(
        currentDay: Int,
        currentWakeTime: LocalTime,
        targetWakeTime: LocalTime,
        avgBedtime: LocalTime,
        minSleepMinutes: Int
    ): LocalTime {
        val totalAdjustmentMinutes = getTimeDifferenceMinutes(currentWakeTime, targetWakeTime)
        val adjustmentSteps = (totalAdjustmentMinutes / ADJUSTMENT_INTERVAL_MINUTES.toDouble()).toInt()

        Log.d("DailyAlarmManager", """
            총 조정 필요: ${totalAdjustmentMinutes}분
            조정 단계: ${adjustmentSteps}단계 (20분 x ${adjustmentSteps})
        """.trimIndent())

        val currentStep = min(currentDay - 1, adjustmentSteps)
        val currentAdjustment = currentStep * ADJUSTMENT_INTERVAL_MINUTES
        var todayWakeTime = currentWakeTime.minusMinutes(currentAdjustment.toLong())

        Log.d("DailyAlarmManager", """
            현재 단계: Day ${currentDay} = ${currentStep}단계
            조정량: ${currentAdjustment}분
            조정 전: ${currentWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            조정 후: ${todayWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
        """.trimIndent())

        val earliestWakeTime = avgBedtime.plusMinutes(minSleepMinutes.toLong())

        if (todayWakeTime.isBefore(earliestWakeTime)) {
            Log.d("DailyAlarmManager", "⚠️ 최소 수면시간 미달 - ${earliestWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}로 조정")
            todayWakeTime = earliestWakeTime
        }

        if (getTimeDifferenceMinutes(todayWakeTime, targetWakeTime) <= ADJUSTMENT_INTERVAL_MINUTES) {
            Log.d("DailyAlarmManager", "✅ 목표 도달 - 목표 시간으로 설정")
            return targetWakeTime
        }

        return todayWakeTime
    }

    private fun getTimeDifferenceMinutes(from: LocalTime, to: LocalTime): Int {
        val fromMinutes = from.hour * 60 + from.minute
        val toMinutes = to.hour * 60 + to.minute

        return if (toMinutes < fromMinutes) {
            (24 * 60 - fromMinutes) + toMinutes
        } else {
            toMinutes - fromMinutes
        }
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

        Log.d("DailyAlarmManager", "기상 알람 설정 완료: ${calendar.time}")
    }

    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "시간 파싱 실패: $timeString, 기본값 사용")
            LocalTime.of(7, 0)
        }
    }
}