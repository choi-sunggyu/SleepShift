package com.example.sleepshift.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class ConsecutiveSuccessManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ConsecutiveSuccess"
        const val MAX_STREAK = 3 // 3일 연속
        const val COMPLETION_REWARD = 10 // 3일 달성 보너스
    }

    // 오늘 성공 체크하고 기록
    fun checkAndRecordSuccess() {
        val today = getTodayDateString()

        Log.d(TAG, "==================")
        Log.d(TAG, "연속 성공 체크: $today")

        // 오늘 성공했는지 확인
        val bedtimeOk = checkBedtimeSuccess(today)
        val wakeOk = checkWakeupSuccess(today)
        val todaySuccess = bedtimeOk && wakeOk

        Log.d(TAG, "취침: $bedtimeOk")
        Log.d(TAG, "기상: $wakeOk")
        Log.d(TAG, "오늘 성공: $todaySuccess")

        if (todaySuccess) {
            recordSuccess()
        } else {
            recordFailure()
        }

        Log.d(TAG, "현재 연속: ${getCurrentStreak()}일")
        Log.d(TAG, "==================")
    }

    // 취침 성공 체크
    private fun checkBedtimeSuccess(dateKey: String): Boolean {
        return sharedPreferences.getBoolean("bedtime_success_$dateKey", false)
    }

    // 기상 성공 체크
    private fun checkWakeupSuccess(dateKey: String): Boolean {
        return sharedPreferences.getBoolean("wake_success_$dateKey", false)
    }

    // 연속 성공 기록
    private fun recordSuccess() {
        val currentStreak = getCurrentStreak()
        val today = getTodayDateString()
        val lastSuccessDate = sharedPreferences.getString("last_success_date", "")

        // 오늘 이미 기록했으면 중복 방지
        if (lastSuccessDate == today) {
            Log.d(TAG, "오늘 이미 기록됨 - 중복방지")
            return
        }

        if (currentStreak >= MAX_STREAK) {
            // 3일 달성!
            completeStreak()
        } else {
            // 연속 +1
            sharedPreferences.edit().apply {
                putInt("consecutive_success_days", currentStreak + 1)
                putString("last_success_date", today)
                apply()
            }
            Log.d(TAG, "연속 ${currentStreak + 1}일 기록")
        }
    }

    // 연속 실패 처리
    private fun recordFailure() {
        val currentStreak = getCurrentStreak()

        if (currentStreak > 0) {
            Log.d(TAG, "연속 실패 - ${currentStreak}일 리셋")

            sharedPreferences.edit().apply {
                putInt("consecutive_success_days", 0)
                putString("last_success_date", "")
                apply()
            }
        }
    }

    // 3일 달성 처리
    private fun completeStreak() {
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        val newCoins = currentCoins + COMPLETION_REWARD

        val totalCompletions = sharedPreferences.getInt("total_streak_completions", 0)
        val today = getTodayDateString()

        sharedPreferences.edit().apply {
            putInt("paw_coin_count", newCoins)
            putInt("total_streak_completions", totalCompletions + 1)
            putInt("consecutive_success_days", 0) // 리셋
            putString("last_completion_date", today)
            putString("last_success_date", today)
            apply()
        }

        Log.d(TAG, "🎉 3일 연속 달성! 보너스 ${COMPLETION_REWARD}개")
        Log.d(TAG, "총 달성: ${totalCompletions + 1}회")
    }

    // 현재 연속일
    fun getCurrentStreak(): Int {
        val lastSuccessDate = sharedPreferences.getString("last_success_date", "")
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()

        // 어제도 아니고 오늘도 아니면 리셋
        if (lastSuccessDate != yesterday && lastSuccessDate != today) {
            sharedPreferences.edit().apply {
                putInt("consecutive_success_days", 0)
                apply()
            }
            return 0
        }

        return sharedPreferences.getInt("consecutive_success_days", 0)
    }

    // 알람 해제 기록 (하위호환)
    fun recordAlarmDismissed() {
        val today = getTodayDateString()
        Log.d(TAG, "알람 해제: $today")
    }

    // 오늘 날짜
    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // 어제 날짜
    private fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}