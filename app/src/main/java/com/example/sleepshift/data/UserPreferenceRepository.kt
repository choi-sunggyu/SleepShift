package com.example.sleepshift.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.sleepshift.util.Constants
import java.util.*

class UserPreferenceRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // 앱 설치 날짜
    fun getAppInstallDate(): Long {
        return prefs.getLong("app_install_date", 0L)
    }

    fun setAppInstallDate(timestamp: Long) {
        prefs.edit().putLong("app_install_date", timestamp).apply()
        Log.d("Repository", "앱 설치일 저장: $timestamp")
    }

    // 코인 관련
    fun getPawCoinCount(): Int {
        return prefs.getInt("paw_coin_count", Constants.DEFAULT_COIN_COUNT)
    }

    fun setPawCoinCount(count: Int) {
        prefs.edit().putInt("paw_coin_count", count).apply()
        Log.d("Repository", "코인 설정: $count")
    }

    // 코인 즉시 저장 (동기식)
    fun setPawCoinCountSync(count: Int): Boolean {
        return try {
            val success = prefs.edit().putInt("paw_coin_count", count).commit()
            Log.d("Repository", "코인 즉시 저장: $count (성공: $success)")
            success
        } catch (e: Exception) {
            Log.e("Repository", "코인 저장 실패", e)
            false
        }
    }

    fun addPawCoins(amount: Int) {
        val newCount = getPawCoinCount() + amount
        setPawCoinCount(newCount)
        Log.d("Repository", "코인 추가 +$amount (총: $newCount)")
    }

    fun usePawCoins(amount: Int): Boolean {
        val current = getPawCoinCount()
        return if (current >= amount) {
            setPawCoinCount(current - amount)
            Log.d("Repository", "코인 사용 -$amount (잔액: ${current - amount})")
            true
        } else {
            Log.d("Repository", "코인 부족 (필요: $amount, 보유: $current)")
            false
        }
    }

    // 첫 실행 여부
    fun isFirstRun(): Boolean {
        return prefs.getBoolean("is_first_run", true)
    }

    fun setFirstRunCompleted() {
        prefs.edit().putBoolean("is_first_run", false).apply()
        Log.d("Repository", "첫 실행 완료 처리")
    }

    // 설문 완료 여부
    fun isSurveyCompleted(): Boolean {
        return prefs.getBoolean("survey_completed", false)
    }

    // 취침 시간 관련
    fun getAvgBedtime(): String {
        return prefs.getString("avg_bedtime", Constants.DEFAULT_BEDTIME) ?: Constants.DEFAULT_BEDTIME
    }

    fun getTodayBedtime(): String? {
        return prefs.getString("today_bedtime", null)
    }

    fun getTargetWakeTime(): String? {
        return prefs.getString("target_wake_time", null)
    }

    fun getMinSleepMinutes(): Int {
        return prefs.getInt("min_sleep_minutes", -1)
    }

    // 일일 체크
    fun getLastDailyCheck(): String {
        return prefs.getString("last_daily_check", "") ?: ""
    }

    fun setLastDailyCheck(date: String) {
        prefs.edit().putString("last_daily_check", date).apply()
        Log.d("Repository", "일일 체크 날짜 저장: $date")
    }

    // 취침 체크인
    fun getLastSleepCheckinDate(): String {
        return prefs.getString("last_sleep_checkin_date", "") ?: ""
    }

    // 연속 완료 횟수
    fun getTotalStreakCompletions(): Int {
        return prefs.getInt("total_streak_completions", 0)
    }

    // 특정 날짜의 취침 성공 여부
    fun getBedtimeSuccess(dateKey: String): Boolean {
        val success = prefs.getBoolean("bedtime_success_$dateKey", false)
        Log.d("Repository", "취침 성공 조회 ($dateKey): $success")
        return success
    }

    // 특정 날짜의 기상 성공 여부
    fun getWakeSuccess(dateKey: String): Boolean {
        val success = prefs.getBoolean("wake_success_$dateKey", false)
        Log.d("Repository", "기상 성공 조회 ($dateKey): $success")
        return success
    }

    // 오늘의 알람 시간
    fun getTodayAlarmTime(): String? {
        return prefs.getString("today_alarm_time", null)
    }

    fun setTodayAlarmTime(time: String) {
        prefs.edit().putString("today_alarm_time", time).apply()
        Log.d("Repository", "오늘 알람 시간 설정: $time")
    }

    // 일회성 알람 여부
    fun getIsOneTimeAlarm(): Boolean {
        return prefs.getBoolean("is_one_time_alarm", false)
    }

    fun setIsOneTimeAlarm(isOneTime: Boolean) {
        prefs.edit().putBoolean("is_one_time_alarm", isOneTime).apply()
        Log.d("Repository", "일회성 알람 설정: $isOneTime")
    }

    // 일회성 알람 시간
    fun getOneTimeAlarmTime(): String? {
        return prefs.getString("one_time_alarm_time", null)
    }

    fun setOneTimeAlarmTime(time: String) {
        prefs.edit().putString("one_time_alarm_time", time).apply()
        Log.d("Repository", "일회성 알람 시간: $time")
    }

    // 일회성 알람 플래그 제거
    fun clearOneTimeAlarm() {
        prefs.edit()
            .putBoolean("is_one_time_alarm", false)
            .remove("one_time_alarm_time")
            .apply()
        Log.d("Repository", "일회성 알람 플래그 제거")
    }

    // 오늘 취침 보상 획득 여부
    fun setEarnedBedtimeRewardToday(earned: Boolean) {
        prefs.edit().putBoolean("earned_bedtime_reward_today", earned).apply()
        Log.d("Repository", "취침 보상 획득: $earned")
    }

    // 감정 데이터 저장
    fun saveMoodData(moodName: String, moodPosition: Int) {
        prefs.edit()
            .putString("today_mood", moodName)
            .putInt("today_mood_position", moodPosition)
            .putLong("sleep_checkin_time", System.currentTimeMillis())
            .apply()
        Log.d("Repository", "감정 저장: $moodName (위치: $moodPosition)")
    }
}