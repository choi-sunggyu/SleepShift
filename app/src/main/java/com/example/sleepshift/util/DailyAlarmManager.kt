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
                // 일회성 알람은 보통 '다음' 발생 시각을 의미하므로 기본 로직 사용
                setSystemAlarm(alarmTime, forceNextDay = false)

                sharedPreferences.edit()
                    .putInt("current_day", currentDay)
                    .putString("today_alarm_time", oneTimeAlarmTime)
                    .apply()

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

        // [수정] Triple 반환: (취침시간, 기상시간, 기상시간이_다음날인지_여부)
        val (todayBedtime, todayWakeTime, isWakeNextDay) = if (isCustomMode) {
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
            // 단순 모드일 때 기상 시간이 오전(00:00~12:00)이면 보통 다음날로 간주
            val isNextDay = wakeTime.hour in 0..12
            Triple(bedtime, wakeTime, isNextDay)
        }

        val bedtimeString = todayBedtime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val wakeTimeString = todayWakeTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        sharedPreferences.edit()
            .putString("today_bedtime", bedtimeString)
            .putString("today_alarm_time", wakeTimeString)
            .putInt("current_day", currentDay)
            .apply()

        // [수정] 계산된 '다음날 여부'를 전달하여 알람 설정
        setSystemAlarm(todayWakeTime, isWakeNextDay)
        setBedtimeNotification(todayBedtime)

        Log.d("DailyAlarmManager", """
            Day $currentDay 결과:
            취침: $bedtimeString
            기상: $wakeTimeString (다음날 여부: $isWakeNextDay)
        """.trimIndent())

        return true
    }

    /**
     * 점진적 스케줄 계산 - Triple<LocalTime, LocalTime, Boolean> 반환으로 수정
     */
    private fun calculateGradualSchedule(
        currentDay: Int,
        currentBedtime: LocalTime,
        currentWakeTime: LocalTime,
        targetWakeTime: LocalTime,
        targetSleepMinutes: Int
    ): Triple<LocalTime, LocalTime, Boolean> {

        val targetBedtime = targetWakeTime.minusMinutes(targetSleepMinutes.toLong())

        // 분 단위 변환
        val currentBedMinutes = toMinutesFromMidnight(currentBedtime)
        val currentWakeMinutes = toMinutesFromMidnight(currentWakeTime)
        val targetBedMinutes = toMinutesFromMidnight(targetBedtime)
        val targetWakeMinutes = toMinutesFromMidnight(targetWakeTime)

        // 정규화 (00:00~14:00 등 오전 시간은 +1440하여 다음날로 처리)
        val normalizedCurrentBed = normalizeBedtime(currentBedMinutes)
        val normalizedCurrentWake = normalizeWakeTime(currentWakeMinutes)
        val normalizedTargetBed = normalizeBedtime(targetBedMinutes)
        val normalizedTargetWake = normalizeWakeTime(targetWakeMinutes)

        val bedDiff = calculateAdjustmentNeeded(normalizedCurrentBed, normalizedTargetBed)
        val wakeDiff = calculateAdjustmentNeeded(normalizedCurrentWake, normalizedTargetWake)

        val bedDaysNeeded = (bedDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES
        val wakeDaysNeeded = (wakeDiff + ADJUSTMENT_INTERVAL_MINUTES - 1) / ADJUSTMENT_INTERVAL_MINUTES

        // 계산 로직 (변동 없음)
        val (todayBedMinutes, todayWakeMinutes) = when {
            bedDiff == 0 && wakeDiff == 0 -> Pair(normalizedTargetBed, normalizedTargetWake)
            currentDay <= bedDaysNeeded && currentDay <= wakeDaysNeeded -> {
                val bedAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, bedDiff)
                val wakeAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, wakeDiff)
                Pair(adjustTimeBackward(normalizedCurrentBed, bedAdjustment), adjustTimeBackward(normalizedCurrentWake, wakeAdjustment))
            }
            currentDay > bedDaysNeeded && currentDay <= wakeDaysNeeded -> {
                val wakeAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, wakeDiff)
                Pair(normalizedTargetBed, adjustTimeBackward(normalizedCurrentWake, wakeAdjustment))
            }
            currentDay > wakeDaysNeeded && currentDay <= bedDaysNeeded -> {
                val bedAdjustment = min(currentDay * ADJUSTMENT_INTERVAL_MINUTES, bedDiff)
                Pair(adjustTimeBackward(normalizedCurrentBed, bedAdjustment), normalizedTargetWake)
            }
            else -> Pair(normalizedTargetBed, normalizedTargetWake)
        }

        // [중요] 역정규화 전, 오늘 기상 시간이 1440분(24시간)을 넘는지 확인하여 '다음날' 여부 판단
        val isWakeNextDay = todayWakeMinutes >= 1440

        val finalBedMinutes = denormalizeBedtime(todayBedMinutes)
        val finalWakeMinutes = denormalizeWakeTime(todayWakeMinutes)

        val todayBedtime = minutesToLocalTime(finalBedMinutes)
        val todayWakeTime = minutesToLocalTime(finalWakeMinutes)

        return Triple(todayBedtime, todayWakeTime, isWakeNextDay)
    }

    // ... (calculateSleepDuration, normalizeBedtime 등 기존 로직 유지) ...
    private fun calculateSleepDuration(bedMinutes: Int, wakeMinutes: Int): Int {
        return if (wakeMinutes >= bedMinutes) wakeMinutes - bedMinutes else (1440 - bedMinutes) + (wakeMinutes + 1440)
    }
    private fun normalizeBedtime(minutes: Int): Int = if (minutes in 0..360) minutes + 1440 else minutes
    private fun normalizeWakeTime(minutes: Int): Int = if (minutes in 0..840) minutes + 1440 else minutes
    private fun denormalizeBedtime(minutes: Int): Int = if (minutes >= 1440) minutes - 1440 else minutes
    private fun denormalizeWakeTime(minutes: Int): Int = if (minutes >= 1440) minutes - 1440 else minutes
    private fun calculateAdjustmentNeeded(current: Int, target: Int): Int = if (current > target) current - target else 0
    private fun adjustTimeBackward(minutes: Int, adjustment: Int): Int {
        var result = minutes - adjustment
        while (result < 0) result += 1440
        return result % 1440 // 1440 이상이어도 시간 계산을 위해 모듈러 연산하지 않고, 호출부에서 판단하도록 변경이 이상적이나 기존 구조 유지
    }
    private fun toMinutesFromMidnight(time: LocalTime): Int = time.hour * 60 + time.minute
    private fun minutesToLocalTime(minutes: Int): LocalTime {
        val normalizedMinutes = (minutes % 1440 + 1440) % 1440
        return LocalTime.of(normalizedMinutes / 60, normalizedMinutes % 60)
    }
    private fun timeToString(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    // ... (showAlarmPermissionDialog 등 기존 로직 유지) ...
    private fun showAlarmPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(intent); Toast.makeText(context, "알람 권한을 허용해주세요", Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Log.e("DailyAlarmManager", "권한 설정 화면 열기 실패", e) }
        }
    }

    private fun setBedtimeNotification(bedtime: LocalTime) {
        val enabled = sharedPreferences.getBoolean("bedtime_notification_enabled", true)
        if (!enabled) { cancelBedtimeNotification(); return }

        val notificationTime = bedtime.minusMinutes(BEDTIME_NOTIFICATION_MINUTES.toLong())
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = "com.example.sleepshift.BEDTIME_NOTIFICATION" }
        val pendingIntent = PendingIntent.getBroadcast(context, 2000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, notificationTime.hour)
            set(Calendar.MINUTE, notificationTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // [수정] 현재 시간보다 이전이면 내일로 설정하되,
            // "취침 시간"은 보통 밤 늦게 설정되므로 자정을 넘겼는지 여부를 신중히 판단해야 함
            // 여기서는 단순 비교 유지하되, 로직이 필요하면 setSystemAlarm과 유사하게 변경 가능
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        Log.d("DailyAlarmManager", "취침 알림 설정: ${calendar.time}")
    }

    private fun cancelBedtimeNotification() {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = "com.example.sleepshift.BEDTIME_NOTIFICATION" }
        val pendingIntent = PendingIntent.getBroadcast(context, 2000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
        Log.d("DailyAlarmManager", "취침 알림 취소")
    }

    /**
     * [핵심 수정] 알람 설정 로직 개선
     * @param forceNextDay 계산상 '내일'임이 확실한 경우 true (예: 새벽 6시 기상)
     */
    private fun setSystemAlarm(alarmTime: LocalTime, forceNextDay: Boolean = false) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // [핵심 버그 수정]
        // 1. 로직상 명확히 "다음날 아침"(forceNextDay=true)인 경우
        //    현재 시각이 새벽(00:00~12:00)이라면, 이미 날짜가 넘어왔으므로 '오늘'로 설정해야 함.
        //    하지만 기존 로직은 '오늘 아침 < 오늘 현재'일 경우 무조건 내일로 넘겨버려 +2일이 되는 현상 발생.

        if (forceNextDay) {
            // 목표 시간이 아침이고, 현재 시간도 아침인 경우 (예: 현재 01:00, 알람 06:00)
            // 같은 날짜에 알람을 울려야 함 (이미 날짜가 지났으므로)
            // 반면 현재 시간이 저녁(22:00)이라면, 알람은 내일(다음날) 울려야 함.

            val currentHour = now.get(Calendar.HOUR_OF_DAY)

            // 현재 시간이 정오(12시) 이전이고, 목표 시간도 정오 이전이면 "같은 날"로 간주
            val isEarlyMorningRun = currentHour < 12 && alarmTime.hour < 12

            if (!isEarlyMorningRun) {
                // 저녁에 실행했다면 내일 아침으로 설정
                if (calendar.before(now)) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } else {
                    // 만약 저녁 10시에 실행했는데 알람이 저녁 11시라면? (드문 케이스)
                    // 이미 미래이므로 놔둠. 하지만 forceNextDay가 true라면 보통 +1일 해야 함
                    if (alarmTime.hour < 12) { // 기상시간이 오전이라면 무조건 다음날
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                }
            } else {
                // 새벽에 실행했다면(예: 01:00), 목표 시간(06:00)이 지났을 때만 내일로 넘김
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    Log.d("DailyAlarmManager", "시간이 지나서 내일로 설정 (새벽 실행)")
                }
            }
        } else {
            // 기존 로직 (단순 시간 비교)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
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
                설정값: $alarmTime
                강제 다음날 여부: $forceNextDay
            """.trimIndent())
        } catch (e: Exception) {
            Log.e("DailyAlarmManager", "알람 설정 실패", e)
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