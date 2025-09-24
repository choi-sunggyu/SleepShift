package com.example.sleepshift.feature.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "알람 수신됨")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // 부팅 완료 후 알람 재설정
                Log.d("AlarmReceiver", "부팅 완료 - 알람 재설정")
                restoreAlarms(context)
                return
            }
            "com.example.sleepshift.ALARM_TRIGGER" -> {
                // 실제 알람 트리거
                Log.d("AlarmReceiver", "알람 트리거 수신")
                triggerAlarm(context)
            }
            else -> {
                // 기본 알람 처리
                Log.d("AlarmReceiver", "기본 알람 처리")
                triggerAlarm(context)
            }
        }
    }

    private fun triggerAlarm(context: Context) {
        // 알람이 활성화되어 있는지 확인
        val sharedPref = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val isAlarmEnabled = sharedPref.getBoolean("alarm_enabled", true)

        if (!isAlarmEnabled) {
            Log.d("AlarmReceiver", "알람이 비활성화되어 있음")
            return
        }

        // 현재 시간이 알람 시간인지 확인 (추가 검증)
        val todayAlarmTime = sharedPref.getString("today_alarm_time", "07:00")
        Log.d("AlarmReceiver", "설정된 알람 시간: $todayAlarmTime")

        // 알람 화면 시작
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }

        try {
            context.startActivity(alarmIntent)
            Log.d("AlarmReceiver", "알람 화면 시작 성공")

            // 알람 실행 기록 저장
            with(sharedPref.edit()) {
                putLong("last_alarm_triggered", System.currentTimeMillis())
                apply()
            }

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "알람 화면 시작 실패: ${e.message}")
        }
    }

    private fun restoreAlarms(context: Context) {
        try {
            val sharedPref = context.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            val surveyCompleted = sharedPref.getBoolean("survey_completed", false)

            if (!surveyCompleted) {
                Log.d("AlarmReceiver", "설문조사 미완료 - 알람 재설정 생략")
                return
            }

            // DailyAlarmManager를 사용하여 알람 재설정
            val alarmManager = com.example.sleepshift.util.DailyAlarmManager(context)
            val currentDay = getCurrentDay(sharedPref)

            alarmManager.updateDailyAlarm(currentDay)
            Log.d("AlarmReceiver", "부팅 후 알람 재설정 완료 - Day $currentDay")

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "알람 재설정 중 오류: ${e.message}")
        }
    }

    private fun getCurrentDay(sharedPref: android.content.SharedPreferences): Int {
        val installDate = sharedPref.getLong("app_install_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - installDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            else -> daysDiff
        }
    }
}