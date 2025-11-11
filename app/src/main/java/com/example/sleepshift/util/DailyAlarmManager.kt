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
     * 알람 설정
     */
    fun updateDailyAlarm(currentDay: Int): Boolean {
        Log.d("DailyAlarmManager", "========== 알람 설정 (Day $currentDay) ==========")

        // 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("DailyAlarmManager", "알람 권한 없음")
                showAlarmPermissionDialog()
                return false
            }
        }

        // 일회성 알람 체크
        val isOneTimeAlarm = sharedPreferences.getBoolean("is_one_time_alarm", false)

        if (isOneTimeAlarm) {
            val oneTimeAlarmTime = sharedPreferences.getString("one_time_alarm_time", null)

            if (oneTimeAlarmTime != null) {
                Log.d("DailyAlarmManager", "일회성 알람: $oneTimeAlarmTime")

                val alarmTime = parseTime(oneTimeAlarmTime)
                setSystemAlarm(alarmTime)

                sharedPreferences.edit()
                    .putInt("current_day", currentDay)
                    .putString("today_alarm_time", oneTimeAlarmTime)
                    .apply()

                // 일회성 알람 플래그 제거
                sharedPreferences.edit()
                    .putBoolean("is_one_time_alarm", false)
                    .remove("one_time_alarm_time")
                    .apply()

                Log.d("DailyAlarmManager", "일회성 알람 설정 완료")
                return true
            }
        }

        // 일반 알람 처리
        Log.d("DailyAlarmManager", "일반 알람 모드 - 점진적 조정")

        val currentBedtime = sharedPreferences.getString("avg_bedtime", "04:00") ?: "04:00"
        val currentWakeTime = sharedPreferences.getString("avg_wake_time", "13:00") ?: "13:00"
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "06:30") ?: "06:30"
        val targetSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", 420)
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        Log.d("DailyAlarmManager", """
            설정값:
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
            Day $currentDay 결과:
            취침: $bedtimeString
            기상: $wakeTimeString
        """.trimIndent())

        return true
    }

    /**
     * 점진적 스케줄 계산 - 동시 조정 방식
     *
     * 1. 취침·기상 시간을 동시에 20분씩 당김
     * 2. 둘 중 하나가 목표에 먼저 도달하면 그쪽을 고정
     * 3. 나머지 하나만 계속 20분씩 당김
     * 4. 둘 다 목표에 도달하면 완료
     */
    private fun calculateGradualSchedule(
        currentDay: Int,
        currentBedtime: LocalTime,
        currentWakeTime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): Pair<LocalTime, LocalTime> {

        // 목표 취침시간 계산
        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        Log.d("DailyAlarmManager", """
        ==================
        계산 시작 (Day $currentDay)
        현재 취침: ${timeToString(currentBedtime)}
        현재 기상: ${timeToString(currentWakeTime)}
        목표 취침: ${timeToString(targetBedtime)}
        목표 기상: ${timeToString(targetWakeTime)}
        목표 수면: ${targetSleepMinutes}분 (${targetSleepMinutes/60}시간 ${targetSleepMinutes%60}분)
        """.trimIndent())

        // 분 단위 변환
        val currentBedMinutes = toMinutesFromMidnight(currentBedtime)
        val currentWakeMinutes = toMinutesFromMidnight(currentWakeTime)
        val targetBedMinutes = toMinutesFromMidnight(targetBedtime)
        val targetWakeMinutes = toMinutesFromMidnight(targetWakeTime)

        // 취침/기상 시간 정규화
        val normalizedCurrentBed = normalizeBedtime(currentBedMinutes)
        val normalizedCurrentWake = normalizeWakeTime(currentWakeMinutes)
        val normalizedTargetBed = normalizeBedtime(targetBedMinutes)
        val normalizedTargetWake = normalizeWakeTime(targetWakeMinutes)

        // 각각의 차이 계산
        val bedDiff = calculateAdjustmentNeeded(normalizedCurrentBed, normalizedTargetBed)
        val wakeDiff = calculateAdjustmentNeeded(normalizedCurrentWake, normalizedTargetWake)

        Log.d("DailyAlarmManager", """
        차이 계산:
        취침 차이: ${bedDiff}분 (${bedDiff/60}시간 ${bedDiff%60}분) → ${(bedDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES}일 필요
        기상 차이: ${wakeDiff}분 (${wakeDiff/60}시간 ${wakeDiff%60}분) → ${(wakeDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES}일 필요
        """.trimIndent())

        // 어느 쪽이 먼저 목표에 도달하는지 계산
        val bedDaysNeeded = (bedDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES
        val wakeDaysNeeded = (wakeDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES

        val (todayBedMinutes, todayWakeMinutes) = when {
            // 둘 다 이미 목표에 도달
            bedDiff == 0 && wakeDiff == 0 -> {
                Log.d("DailyAlarmManager", "[완료] 이미 목표에 도달")
                Pair(normalizedTargetBed, normalizedTargetWake)
            }

            // 둘 다 아직 조정 중 (동시에 20분씩 당김)
            currentDay <= bedDaysNeeded && currentDay <= wakeDaysNeeded -> {
                val bedAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, bedDiff)
                val wakeAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, wakeDiff)

                val todayBed = adjustTimeBackward(normalizedCurrentBed, bedAdjustment)
                val todayWake = adjustTimeBackward(normalizedCurrentWake, wakeAdjustment)

                Log.d("DailyAlarmManager", "[동시 조정] Day $currentDay - 취침 ${bedAdjustment}분, 기상 ${wakeAdjustment}분 당김")
                Pair(todayBed, todayWake)
            }

            // 취침시간 먼저 도달 → 취침 고정, 기상만 조정
            currentDay > bedDaysNeeded && currentDay <= wakeDaysNeeded -> {
                val wakeAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, wakeDiff)
                val todayWake = adjustTimeBackward(normalizedCurrentWake, wakeAdjustment)

                Log.d("DailyAlarmManager", "[취침 고정] Day $currentDay - 취침 고정 🔒, 기상만 ${wakeAdjustment}분 당김")
                Pair(normalizedTargetBed, todayWake)
            }

            // 기상시간 먼저 도달 → 기상 고정, 취침만 조정
            currentDay > wakeDaysNeeded && currentDay <= bedDaysNeeded -> {
                val bedAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, bedDiff)
                val todayBed = adjustTimeBackward(normalizedCurrentBed, bedAdjustment)

                Log.d("DailyAlarmManager", "[기상 고정] Day $currentDay - 취침만 ${bedAdjustment}분 당김, 기상 고정 🔒")
                Pair(todayBed, normalizedTargetWake)
            }

            // 둘 다 목표 도달
            else -> {
                Log.d("DailyAlarmManager", "[완료] 목표 도달")
                Pair(normalizedTargetBed, normalizedTargetWake)
            }
        }

        // 역정규화
        val finalBedMinutes = denormalizeBedtime(todayBedMinutes)
        val finalWakeMinutes = denormalizeWakeTime(todayWakeMinutes)

        val todayBedtime = minutesToLocalTime(finalBedMinutes)
        val todayWakeTime = minutesToLocalTime(finalWakeMinutes)

        val sleepDuration = calculateSleepDuration(todayBedMinutes, todayWakeMinutes)

        Log.d("DailyAlarmManager", """
        ==================
        최종 결과 (Day $currentDay):
        취침: ${timeToString(todayBedtime)}
        기상: ${timeToString(todayWakeTime)}
        수면: ${sleepDuration}분 (${sleepDuration/60}시간 ${sleepDuration%60}분)
        ==================
        """.trimIndent())

        return Pair(todayBedtime, todayWakeTime)
    }

    /**
     * 수면 시간 계산 (정규화된 값 기준)
     */
    private fun calculateSleepDuration(bedMinutes: Int, wakeMinutes: Int): Int {
        return if (wakeMinutes >= bedMinutes) {
            wakeMinutes - bedMinutes
        } else {
            // 자정을 넘어가는 경우
            (1440 - bedMinutes) + (wakeMinutes + 1440)
        }
    }

    /**
     * 취침 시간 정규화 (저녁 기준)
     * 18:00~23:59: 그대로
     * 00:00~06:00: +1440 (다음날로 간주)
     */
    private fun normalizeBedtime(minutes: Int): Int {
        return if (minutes in 0..360) {  // 00:00~06:00
            minutes + 1440
        } else {
            minutes
        }
    }

    /**
     * 기상 시간 정규화 (아침 기준)
     * 00:00~14:00: +1440 (다음날로 간주)
     * 14:00~23:59: 그대로 (저녁)
     */
    private fun normalizeWakeTime(minutes: Int): Int {
        return if (minutes in 0..840) {  // 00:00~14:00
            minutes + 1440
        } else {
            minutes
        }
    }

    /**
     * 취침 시간 역정규화
     */
    private fun denormalizeBedtime(minutes: Int): Int {
        return if (minutes >= 1440) {
            minutes - 1440
        } else {
            minutes
        }
    }

    /**
     * 기상 시간 역정규화
     */
    private fun denormalizeWakeTime(minutes: Int): Int {
        return if (minutes >= 1440) {
            minutes - 1440
        } else {
            minutes
        }
    }

    /**
     * 조정이 필요한 분 계산
     * current > target: 당겨야 함 (양수 반환)
     * current <= target: 이미 도달 (0 반환)
     */
    private fun calculateAdjustmentNeeded(current: Int, target: Int): Int {
        return if (current > target) {
            current - target
        } else {
            0
        }
    }

    /**
     * 시간을 뒤로 조정 (일찍으로)
     */
    private fun adjustTimeBackward(minutes: Int, adjustment: Int): Int {
        var result = minutes - adjustment
        while (result < 0) result += 1440
        return result % 1440
    }

    /**
     * LocalTime을 자정 기준 분으로 변환
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
     * LocalTime을 문자열로
     */
    private fun timeToString(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"))
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
                Log.e("DailyAlarmManager", "권한 설정 화면 열기 실패", e)
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

        Log.d("DailyAlarmManager", "취침 알림 설정: ${calendar.time}")
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
        Log.d("DailyAlarmManager", "취침 알림 취소")
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
                Log.d("DailyAlarmManager", "시간이 지나서 내일로 설정")
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Log.d("DailyAlarmManager", """
                알람 설정 완료:
                시간: ${calendar.time}
                현재: ${Date()}
            """.trimIndent())
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "알람 설정 실패", e)
            e.printStackTrace()
        }
    }

    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "시간 파싱 실패: $timeString", e)
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
        Log.d("DailyAlarmManager", "알람 취소")
    }
}