// util/DailyAlarmManager.kt
package com.example.sleepshift.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.util.Log
import com.example.sleepshift.feature.alarm.AlarmReceiver
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*

class DailyAlarmManager(private val context: Context) {

    companion object {
        const val MIN_SLEEP_MINUTES = 360 // 6시간
        const val ADJUSTMENT_INTERVAL_MINUTES = 20 // 20분 단위 조정
        const val MAX_DIFFERENCE_HOURS = 1 // 1시간 차이 임계값
        private const val TAG = "DailyAlarmManager"
    }

    private val sharedPreferences = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 정확한 알람 권한 체크 (Android 12+)
     */
    fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    /**
     * 매일 알람 시간 업데이트
     */
    fun updateDailyAlarm(currentDay: Int) {
        // 권한 체크
        if (!checkExactAlarmPermission()) {
            Log.e(TAG, "정확한 알람 권한이 없습니다!")
            return
        }

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

        Log.d(TAG, "Day $currentDay: 알람 설정 완료 - $alarmTimeString")
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

        // AlarmReceiver로 보낼 Intent (⭐ ACTION 추가)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,  // 고정된 REQUEST_CODE 사용
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알람 시간 계산 (오늘 또는 내일)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 현재 시간보다 이전이면 내일로 설정
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
                Log.d(TAG, "알람 시간이 지나서 내일로 설정")
            } else {
                Log.d(TAG, "오늘 알람 설정")
            }
        }

        // 기존 알람 취소 후 재설정
        alarmManager.cancel(pendingIntent)

        // Android 버전별 알람 설정
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "알람 설정 완료 (setExactAndAllowWhileIdle): ${calendar.time}")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "알람 설정 완료 (setExact): ${calendar.time}")
            }

            // 알람 설정 확인용 로그
            val nextAlarmInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.nextAlarmClock?.let {
                    "다음 알람: ${Date(it.triggerTime)}"
                } ?: "시스템 알람 정보 없음"
            } else {
                "시스템 알람 확인 불가 (API < 21)"
            }
            Log.d(TAG, nextAlarmInfo)

        } catch (e: SecurityException) {
            Log.e(TAG, "알람 설정 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "알람 설정 실패: ${e.message}")
        }
    }

    /**
     * 알람 취소
     */
    fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepshift.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "알람 취소됨")
    }

    /**
     * 정확한 알람 권한 체크 및 요청 (Android 12+)
     * @return 권한이 있으면 true, 없으면 설정 화면 띄우고 false 반환
     */
    fun checkAndRequestExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                // 권한이 없으면 설정 화면으로 이동
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)

                    Log.d(TAG, "정확한 알람 권한 요청 화면 표시")

                    // Toast로 사용자에게 안내
                    android.widget.Toast.makeText(
                        context,
                        "정확한 시간에 알람을 울리려면 권한이 필요합니다",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    return false
                } catch (e: Exception) {
                    Log.e(TAG, "권한 요청 화면 열기 실패: ${e.message}")
                    return false
                }
            }

            Log.d(TAG, "정확한 알람 권한 있음")
            return true
        }

        // Android 12 미만은 권한 불필요
        return true
    }
}