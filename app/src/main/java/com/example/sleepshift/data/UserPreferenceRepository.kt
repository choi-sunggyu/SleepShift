package com.example.sleepshift.data

import android.content.Context
import android.content.SharedPreferences
import com.example.sleepshift.util.Constants
import java.util.*

class UserPreferenceRepository(context: Context) { //데이터 접근 계층

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // 앱 설치 날짜
    fun getAppInstallDate(): Long = prefs.getLong("app_install_date", 0L)

    fun setAppInstallDate(timestamp: Long) {
        prefs.edit().putLong("app_install_date", timestamp).apply()
    }

    // 코인 관련
    fun getPawCoinCount(): Int = prefs.getInt("paw_coin_count", Constants.DEFAULT_COIN_COUNT)

    fun setPawCoinCount(count: Int) {
        prefs.edit().putInt("paw_coin_count", count).apply()
    }

    fun addPawCoins(amount: Int) {
        val newCount = getPawCoinCount() + amount
        setPawCoinCount(newCount)
    }

    fun usePawCoins(amount: Int): Boolean {
        val current = getPawCoinCount()
        return if (current >= amount) {
            setPawCoinCount(current - amount)
            true
        } else {
            false
        }
    }

    // 첫 실행 여부
    fun isFirstRun(): Boolean = prefs.getBoolean("is_first_run", true)

    fun setFirstRunCompleted() {
        prefs.edit().putBoolean("is_first_run", false).apply()
    }

    // 설문 완료 여부
    fun isSurveyCompleted(): Boolean = prefs.getBoolean("survey_completed", false)

    // 취침 시간 관련
    fun getAvgBedtime(): String = prefs.getString("avg_bedtime", Constants.DEFAULT_BEDTIME) ?: Constants.DEFAULT_BEDTIME

    fun getTodayBedtime(): String? = prefs.getString("today_bedtime", null)

    fun getTargetWakeTime(): String? = prefs.getString("target_wake_time", null)

    fun getMinSleepMinutes(): Int = prefs.getInt("min_sleep_minutes", -1)

    // 일일 체크
    fun getLastDailyCheck(): String = prefs.getString("last_daily_check", "") ?: ""

    fun setLastDailyCheck(date: String) {
        prefs.edit().putString("last_daily_check", date).apply()
    }

    // 취침 체크인
    fun getLastSleepCheckinDate(): String = prefs.getString("last_sleep_checkin_date", "") ?: ""

    // 연속 완료 횟수
    fun getTotalStreakCompletions(): Int = prefs.getInt("total_streak_completions", 0)

    fun UserPreferenceRepository.setTodayAlarmTime(time: String) {
        prefs.edit().putString("today_alarm_time", time).apply()
    }

    /**
     * 오늘의 알람 시간 설정
     */
    fun setTodayAlarmTime(time: String) {
        prefs.edit().putString("today_alarm_time", time).apply()
    }

    /**
     * 일회성 알람 여부 설정
     */
    fun setIsOneTimeAlarm(isOneTime: Boolean) {
        prefs.edit().putBoolean("is_one_time_alarm", isOneTime).apply()
        android.util.Log.d("Repository", "✅ is_one_time_alarm 설정: $isOneTime")
    }

    /**
     * 일회성 알람 여부 확인
     */
    fun getIsOneTimeAlarm(): Boolean {
        return prefs.getBoolean("is_one_time_alarm", false)
    }

    /**
     * 일회성 알람 시간 설정
     */
    fun setOneTimeAlarmTime(time: String) {
        prefs.edit().putString("one_time_alarm_time", time).apply()
        android.util.Log.d("Repository", "✅ one_time_alarm_time 설정: $time")
    }

    /**
     * 일회성 알람 시간 가져오기
     */
    fun getOneTimeAlarmTime(): String? {
        return prefs.getString("one_time_alarm_time", null)
    }

    /**
     * 일회성 알람 플래그 제거 (알람이 울린 후 호출)
     */
    fun clearOneTimeAlarm() {
        prefs.edit()
            .putBoolean("is_one_time_alarm", false)
            .remove("one_time_alarm_time")
            .apply()
        android.util.Log.d("Repository", "✅ 일회성 알람 플래그 제거됨")
    }

    /**
     * 오늘의 알람 시간 가져오기
     */
    fun getTodayAlarmTime(): String? {
        return prefs.getString("today_alarm_time", null)
    }

    /**
     * 오늘 취침 보상 획득 여부 설정
     */
    fun setEarnedBedtimeRewardToday(earned: Boolean) {
        prefs.edit().putBoolean("earned_bedtime_reward_today", earned).apply()
    }

    /**
     * 감정 데이터 저장
     */
    fun saveMoodData(moodName: String, moodPosition: Int) {
        prefs.edit()
            .putString("today_mood", moodName)
            .putInt("today_mood_position", moodPosition)
            .putLong("sleep_checkin_time", System.currentTimeMillis())
            .apply()
    }
}