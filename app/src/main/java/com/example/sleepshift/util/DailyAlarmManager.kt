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

                // 1. 일회성 알람 시간으로 설정
                val alarmTime = parseTime(oneTimeAlarmTime)
                setSystemAlarm(alarmTime)

                // 2. SharedPreferences에 오늘의 알람 시간 저장
                sharedPreferences.edit()
                    .putInt("current_day", currentDay)
                    .putString("today_alarm_time", oneTimeAlarmTime)
                    .apply()

                // 3. 일회성 알람 플래그 제거
                sharedPreferences.edit()
                    .putBoolean("is_one_time_alarm", false)
                    .remove("one_time_alarm_time")
                    .apply()

                Log.d("DailyAlarmManager", "✅ 일회성 알람 설정 완료: $oneTimeAlarmTime")
                Log.d("DailyAlarmManager", "✅ 일회성 알람 플래그 제거됨")
                Log.d("DailyAlarmManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return true
            }
        }

        // ⭐ 3단계: 일반 알람 처리
        Log.d("DailyAlarmManager", "📅 일반 알람 모드 - 점진적 조정 계산")

        val currentBedtime = sharedPreferences.getString("avg_bedtime", "04:00") ?: "04:00"
        val currentWakeTime = sharedPreferences.getString("avg_wake_time", "13:00") ?: "13:00"
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "06:30") ?: "06:30"
        val targetSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", 420)
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        Log.d("DailyAlarmManager", """
            === 읽은 설정값 ===
            avg_bedtime: $currentBedtime
            avg_wake_time: $currentWakeTime
            target_wake_time: $targetWakeTime
            min_sleep_minutes: $targetSleepMinutes
            alarm_mode_custom: $isCustomMode
        """.trimIndent())

        val (todayBedtime, todayWakeTime) = if (isCustomMode) {
            calculateGradualSchedule(
                currentDay = currentDay,
                currentBedtime = parseTime(currentBedtime),
                currentWakeTime = parseTime(currentWakeTime),
                targetWakeTime = parseTime(targetWakeTime),
                targetSleepMinutes = targetSleepMinutes
            )
        } else {
            val wakeTime = parseTime(targetWakeTime)
            val bedtime = wakeTime.minusMinutes(targetSleepMinutes.toLong())
            Pair(bedtime, wakeTime)
        }

        val bedtimeString = todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val wakeTimeString = todayWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        sharedPreferences.edit()
            .putString("today_bedtime", bedtimeString)
            .putString("today_alarm_time", wakeTimeString)
            .putInt("current_day", currentDay)
            .apply()

        setSystemAlarm(todayWakeTime)
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
     * ⭐⭐⭐ 새로운 점진적 스케줄 계산 로직
     *
     * 로직:
     * 1. 먼저 취침 + 기상 둘 다 20분씩 당김
     * 2. 먼저 목표에 도달한 쪽을 고정
     * 3. 나머지 하나만 계속 20분씩 당김
     * 4. 둘 다 목표 도달 → 완전 고정
     */
    private fun calculateGradualSchedule(
        currentDay: Int,
        currentBedtime: LocalTime,
        currentWakeTime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): Pair<LocalTime, LocalTime> {

        // 목표 취침 시간 계산
        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        Log.d("DailyAlarmManager", """
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            📊 새로운 알람 로직 계산 (Day $currentDay)
            현재 취침: ${currentBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            현재 기상: ${currentWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            목표 취침: ${targetBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            목표 기상: ${targetWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            목표 수면: ${targetSleepMinutes}분 (${targetSleepMinutes / 60}시간 ${targetSleepMinutes % 60}분)
        """.trimIndent())

        // 분 단위로 변환 (자정 기준)
        val currentBedMinutes = toMinutesFromMidnight(currentBedtime)
        val currentWakeMinutes = toMinutesFromMidnight(currentWakeTime)
        val targetBedMinutes = toMinutesFromMidnight(targetBedtime)
        val targetWakeMinutes = toMinutesFromMidnight(targetWakeTime)

        // 취침/기상 시간 차이 계산 (절대값)
        val bedtimeDiff = calculateTimeDifference(currentBedMinutes, targetBedMinutes)
        val waketimeDiff = calculateTimeDifference(currentWakeMinutes, targetWakeMinutes)

        Log.d("DailyAlarmManager", """
            취침 시간 차이: ${bedtimeDiff}분
            기상 시간 차이: ${waketimeDiff}분
        """.trimIndent())

        // 각각 목표 도달까지 필요한 일수 계산
        val daysToReachBedtime = (bedtimeDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES
        val daysToReachWaketime = (waketimeDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES

        Log.d("DailyAlarmManager", """
            취침 목표까지: ${daysToReachBedtime}일 필요
            기상 목표까지: ${daysToReachWaketime}일 필요
        """.trimIndent())

        // 먼저 도달하는 시점 결정
        val firstReachDay = min(daysToReachBedtime, daysToReachWaketime)

        var todayBedMinutes: Int
        var todayWakeMinutes: Int

        when {
            // 케이스 1: 둘 다 아직 목표 미달 → 둘 다 당김
            currentDay < firstReachDay -> {
                Log.d("DailyAlarmManager", "📍 단계 1: 둘 다 20분씩 당김")
                val adjustment = currentDay * ADJUSTMENT_INTERVAL_MINUTES
                todayBedMinutes = adjustTime(currentBedMinutes, -adjustment)
                todayWakeMinutes = adjustTime(currentWakeMinutes, -adjustment)
            }

            // 케이스 2: 한쪽이 목표 도달, 다른쪽만 당김
            currentDay < daysToReachBedtime + daysToReachWaketime -> {
                if (daysToReachBedtime < daysToReachWaketime) {
                    // 취침이 먼저 도달 → 취침 고정, 기상만 당김
                    Log.d("DailyAlarmManager", "📍 단계 2: 취침 고정, 기상만 당김")
                    todayBedMinutes = targetBedMinutes
                    val additionalDays = currentDay - daysToReachBedtime
                    val additionalAdjustment = additionalDays * ADJUSTMENT_INTERVAL_MINUTES
                    todayWakeMinutes = adjustTime(
                        adjustTime(currentWakeMinutes, -daysToReachBedtime * ADJUSTMENT_INTERVAL_MINUTES),
                        -additionalAdjustment
                    )
                    // 목표를 넘지 않도록 제한
                    if (calculateTimeDifference(todayWakeMinutes, targetWakeMinutes) < ADJUSTMENT_INTERVAL_MINUTES) {
                        todayWakeMinutes = targetWakeMinutes
                    }
                } else {
                    // 기상이 먼저 도달 → 기상 고정, 취침만 당김
                    Log.d("DailyAlarmManager", "📍 단계 2: 기상 고정, 취침만 당김")
                    todayWakeMinutes = targetWakeMinutes
                    val additionalDays = currentDay - daysToReachWaketime
                    val additionalAdjustment = additionalDays * ADJUSTMENT_INTERVAL_MINUTES
                    todayBedMinutes = adjustTime(
                        adjustTime(currentBedMinutes, -daysToReachWaketime * ADJUSTMENT_INTERVAL_MINUTES),
                        -additionalAdjustment
                    )
                    // 목표를 넘지 않도록 제한
                    if (calculateTimeDifference(todayBedMinutes, targetBedMinutes) < ADJUSTMENT_INTERVAL_MINUTES) {
                        todayBedMinutes = targetBedMinutes
                    }
                }
            }

            // 케이스 3: 둘 다 목표 도달 → 완전 고정
            else -> {
                Log.d("DailyAlarmManager", "📍 단계 3: 목표 도달! 완전 고정")
                todayBedMinutes = targetBedMinutes
                todayWakeMinutes = targetWakeMinutes
            }
        }

        val todayBedtime = minutesToLocalTime(todayBedMinutes)
        val todayWakeTime = minutesToLocalTime(todayWakeMinutes)

        Log.d("DailyAlarmManager", """
            ✅ 계산 결과:
            오늘 취침: ${todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            오늘 기상: ${todayWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent())

        return Pair(todayBedtime, todayWakeTime)
    }

    /**
     * LocalTime을 자정 이후 분으로 변환
     */
    private fun toMinutesFromMidnight(time: LocalTime): Int {
        return time.hour * 60 + time.minute
    }

    /**
     * 분을 LocalTime으로 변환
     */
    private fun minutesToLocalTime(minutes: Int): LocalTime {
        val normalizedMinutes = (minutes % 1440 + 1440) % 1440
        return LocalTime.of(normalizedMinutes / 60, normalizedMinutes % 60)
    }

    /**
     * 시간 차이 계산 (절대값, 자정 넘김 고려)
     */
    private fun calculateTimeDifference(from: Int, to: Int): Int {
        return if (from > to) {
            from - to
        } else {
            from + (1440 - to)
        }
    }

    /**
     * 시간 조정 (자정 넘김 처리)
     */
    private fun adjustTime(minutes: Int, adjustment: Int): Int {
        var result = minutes + adjustment
        // 자정 넘김 처리
        while (result < 0) result += 1440
        while (result >= 1440) result -= 1440
        return result
    }

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
            =====================================
        """.trimIndent())
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "❌ 알람 설정 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "⚠️ 시간 파싱 실패: $timeString, 기본값 07:00 사용")
            LocalTime.of(7, 0)
        }
    }

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