// util/DailyAlarmManager.kt
package com.example.sleepshift.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.sleepshift.feature.alarm.AlarmActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*

class DailyAlarmManager(private val context: Context) {

    companion object {
        const val MIN_SLEEP_MINUTES = 360 // 6시간
        const val ADJUSTMENT_INTERVAL_MINUTES = 20 // 20분 단위 조정
        const val MAX_DIFFERENCE_HOURS = 1 // 1시간 차이 임계값
    }

    private val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 매일 알람 시간 업데이트
     */
    fun updateDailyAlarm(currentDay: Int) {
        // 설문 데이터 로드
        val avgBedtimeStr = sharedPreferences.getString("survey_average_bedtime", "23:30") ?: "23:30"
        val desiredWakeStr = sharedPreferences.getString("survey_desired_wake_time", "07:00") ?: "07:00"
        val minSleepMinutes = sharedPreferences.getInt("survey_min_sleep_minutes", MIN_SLEEP_MINUTES)
        val targetWakeStr = sharedPreferences.getString("survey_target_wake_time", "07:00") ?: "07:00"

        // LocalTime으로 변환
        val avgBedtime = LocalTime.parse(avgBedtimeStr, hhmm)
        val desiredWakeTime = LocalTime.parse(desiredWakeStr, hhmm)
        val targetWakeTime = LocalTime.parse(targetWakeStr, hhmm)

        // 알람 시간 계산
        val todayAlarmTime = calculateDailyAlarmTime(
            avgBedtime, desiredWakeTime, minSleepMinutes, targetWakeTime, currentDay
        )

        // 계산된 시간 저장
        val alarmTimeString = todayAlarmTime.format(hhmm)
        with(sharedPreferences.edit()) {
            putString("today_alarm_time", alarmTimeString)
            putInt("current_day", currentDay)
            apply()
        }

        // 시스템 알람 설정
        setSystemAlarm(todayAlarmTime)

        android.util.Log.d("DailyAlarmManager", "Day $currentDay: 알람 설정 - $alarmTimeString")
    }

    private fun calculateDailyAlarmTime(
        avgBedtime: LocalTime,
        desiredWakeTime: LocalTime,
        minSleepMinutes: Int,
        targetWakeTime: LocalTime,
        currentDay: Int
    ): LocalTime {

        // 평균 취침시간 기준으로 현재 기상시간 계산 (평균 수면시간 8시간 가정)
        val avgSleepDuration = 8 * 60 // 8시간
        val currentWakeTime = avgBedtime.plusMinutes(avgSleepDuration.toLong())

        // 목표와 현재의 차이 계산
        val timeDifferenceMinutes = getTimeDifferenceMinutes(currentWakeTime, targetWakeTime)
        val timeDifferenceHours = timeDifferenceMinutes / 60.0

        // 이미 목표에 도달했는지 확인
        if (hasReachedTarget(currentDay)) {
            return targetWakeTime
        }

        return when {
            // Case 1: 차이가 1시간보다 큰 경우 - 점진적 조정
            timeDifferenceHours > MAX_DIFFERENCE_HOURS -> {
                calculateGradualAdjustment(currentWakeTime, targetWakeTime, avgBedtime, minSleepMinutes, currentDay)
            }

            // Case 2: 차이가 0~1시간 사이 - 목표 시간 사용
            timeDifferenceHours > 0 && timeDifferenceHours <= MAX_DIFFERENCE_HOURS -> {
                targetWakeTime
            }

            // Case 3: 목표가 현재보다 늦은 경우 - 즉시 적용
            timeDifferenceHours <= 0 -> {
                targetWakeTime
            }

            else -> targetWakeTime
        }
    }

    private fun calculateGradualAdjustment(
        currentWakeTime: LocalTime,
        targetWakeTime: LocalTime,
        avgBedtime: LocalTime,
        minSleepMinutes: Int,
        currentDay: Int
    ): LocalTime {

        // 최소 수면시간 보장하는 최이른 기상시간
        val earliestWakeTime = avgBedtime.plusMinutes(minSleepMinutes.toLong())

        // 최소 수면시간 체크
        if (targetWakeTime.isBefore(earliestWakeTime)) {
            return earliestWakeTime
        }

        // 점진적 조정 계산
        val totalAdjustmentMinutes = getTimeDifferenceMinutes(currentWakeTime, targetWakeTime)
        val adjustmentSteps = (totalAdjustmentMinutes / ADJUSTMENT_INTERVAL_MINUTES.toDouble()).toInt()

        // 현재 일차에 따른 조정량
        val currentAdjustment = min(currentDay - 1, adjustmentSteps) * ADJUSTMENT_INTERVAL_MINUTES
        val todayWakeTime = currentWakeTime.minusMinutes(currentAdjustment.toLong())

        // 목표에 거의 도달했으면 목표 시간으로
        return if (getTimeDifferenceMinutes(todayWakeTime, targetWakeTime) <= ADJUSTMENT_INTERVAL_MINUTES) {
            markTargetReached()
            targetWakeTime
        } else {
            todayWakeTime
        }
    }

    private fun getTimeDifferenceMinutes(time1: LocalTime, time2: LocalTime): Int {
        val minutes1 = time1.hour * 60 + time1.minute
        val minutes2 = time2.hour * 60 + time2.minute

        return if (minutes1 >= minutes2) {
            minutes1 - minutes2
        } else {
            (24 * 60) - minutes2 + minutes1
        }
    }

    private fun hasReachedTarget(currentDay: Int): Boolean {
        return sharedPreferences.getBoolean("target_reached", false)
    }

    private fun markTargetReached() {
        with(sharedPreferences.edit()) {
            putBoolean("target_reached", true)
            apply()
        }
    }

    private fun setSystemAlarm(alarmTime: LocalTime) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // AlarmReceiver로 보낼 Intent
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 내일 해당 시간으로 알람 설정
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1) // 내일
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}

// 알람 수신용 BroadcastReceiver (별도 파일로 생성 필요)
class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 알람 화면 시작
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alarmIntent)
    }
}