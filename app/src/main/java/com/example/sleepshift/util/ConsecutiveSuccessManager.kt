package com.example.sleepshift.util

import android.content.Context
import android.content.SharedPreferences

class ConsecutiveSuccessManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

    companion object {
        const val MAX_STREAK = 3 // 최대 3일 연속
        const val COMPLETION_REWARD = 10 // 3일 달성시 보너스 코인
    }

    /**
     * 오늘 성공 여부 체크
     * - 설정된 시간에 잤는지
     * - 알람에 맞춰 일어났는지
     */
    fun checkTodaySuccess(): Boolean {
        val today = getTodayDateString()

        // 이미 오늘 체크했으면 저장된 값 반환
        val lastCheckDate = sharedPreferences.getString("last_success_check_date", "")
        if (lastCheckDate == today) {
            return sharedPreferences.getBoolean("today_success", false)
        }

        // 오늘의 성공 여부 판단
        val wentToBedOnTime = checkBedtimeSuccess()
        val wokeUpOnTime = checkWakeupSuccess()
        val success = wentToBedOnTime && wokeUpOnTime

        // 결과 저장
        with(sharedPreferences.edit()) {
            putBoolean("today_success", success)
            putString("last_success_check_date", today)
            apply()
        }

        return success
    }

    /**
     * 취침 시간 준수 체크
     */
    private fun checkBedtimeSuccess(): Boolean {
        val targetBedtime = sharedPreferences.getString("today_alarm_time", "23:00") ?: "23:00"
        val actualBedtime = sharedPreferences.getLong("actual_bedtime_today", 0L)

        if (actualBedtime == 0L) return false

        // 목표 시간 ± 30분 이내면 성공
        val targetTime = parseTimeToMinutes(targetBedtime)
        val actualTime = getTimeFromTimestamp(actualBedtime)

        val diff = Math.abs(targetTime - actualTime)
        return diff <= 30 // 30분 이내
    }

    /**
     * 기상 시간 준수 체크
     */
    private fun checkWakeupSuccess(): Boolean {
        // 알람 해제 기록이 있으면 성공
        val alarmDismissed = sharedPreferences.getBoolean("alarm_dismissed_today", false)
        return alarmDismissed
    }

    /**
     * 연속 성공 기록
     */
    fun recordSuccess() {
        val currentStreak = getCurrentStreak()

        if (currentStreak >= MAX_STREAK) {
            // 3일 달성 - 보상 지급 및 리셋
            completeStreak()
        } else {
            // 연속 성공 +1
            with(sharedPreferences.edit()) {
                putInt("consecutive_success_days", currentStreak + 1)
                putString("last_success_date", getTodayDateString())
                apply()
            }
        }
    }

    /**
     * 연속 실패 처리
     */
    fun recordFailure() {
        // 연속 성공 초기화
        with(sharedPreferences.edit()) {
            putInt("consecutive_success_days", 0)
            putString("last_success_date", "")
            apply()
        }
    }

    /**
     * 3일 연속 달성 처리
     */
    private fun completeStreak() {
        // 보너스 코인 지급
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        val newCoins = currentCoins + COMPLETION_REWARD

        // 총 달성 횟수 증가
        val totalCompletions = sharedPreferences.getInt("total_streak_completions", 0)

        with(sharedPreferences.edit()) {
            putInt("paw_coin_count", newCoins)
            putInt("total_streak_completions", totalCompletions + 1)
            putInt("consecutive_success_days", 0) // 리셋
            putString("last_completion_date", getTodayDateString())
            apply()
        }

        android.util.Log.d("ConsecutiveSuccess", "3일 연속 달성! 보너스 코인 ${COMPLETION_REWARD}개 지급")
    }

    /**
     * 현재 연속 성공 일수
     */
    fun getCurrentStreak(): Int {
        val lastSuccessDate = sharedPreferences.getString("last_success_date", "")
        val today = getTodayDateString()

        // 마지막 성공이 어제가 아니면 리셋
        if (lastSuccessDate != getYesterdayDateString() && lastSuccessDate != today) {
            with(sharedPreferences.edit()) {
                putInt("consecutive_success_days", 0)
                apply()
            }
            return 0
        }

        return sharedPreferences.getInt("consecutive_success_days", 0)
    }

    /**
     * 알람 해제 기록
     */
    fun recordAlarmDismissed() {
        with(sharedPreferences.edit()) {
            putBoolean("alarm_dismissed_today", true)
            putString("alarm_dismiss_date", getTodayDateString())
            apply()
        }
    }

    /**
     * 취침 기록
     */
    fun recordBedtime(timestamp: Long) {
        with(sharedPreferences.edit()) {
            putLong("actual_bedtime_today", timestamp)
            putString("bedtime_date", getTodayDateString())
            apply()
        }
    }

    /**
     * 일일 데이터 리셋 (자정 또는 새로운 날 시작시)
     */
    fun resetDailyData() {
        with(sharedPreferences.edit()) {
            putBoolean("alarm_dismissed_today", false)
            putLong("actual_bedtime_today", 0L)
            putBoolean("today_success", false)
            apply()
        }
    }

    // Helper 메소드들
    private fun getTodayDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH)+1}-${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun getYesterdayDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH)+1}-${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun getTimeFromTimestamp(timestamp: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(java.util.Calendar.MINUTE)
    }
}