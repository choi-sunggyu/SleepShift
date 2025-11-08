package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.databinding.ActivityNightRoutineBinding
import com.example.sleepshift.util.DailyAlarmManager
import java.text.SimpleDateFormat
import java.util.*

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightRoutineBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNightRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        setupUI()

        Log.d("NightRoutine", "나이트 루틴 시작")
    }

    private fun setupUI() {
        // 목표 취침 시간 표시
        val targetBedtime = sharedPreferences.getString("today_bedtime", "23:00") ?: "23:00"
        binding.tvTargetBedtime?.text = "목표 취침 시간: $targetBedtime"

        // 수면 체크인 버튼
        binding.btnSleepCheckin.setOnClickListener {
            performSleepCheckin()
        }
    }

    // 수면 체크인 수행
    private fun performSleepCheckin() {
        Log.d("NightRoutine", "==================")
        Log.d("NightRoutine", "수면 체크인 시작")

        val currentTime = System.currentTimeMillis()
        val today = getTodayDateString()
        val timeString = getCurrentTimeString()

        Log.d("NightRoutine", "체크인 시간: $timeString")

        // 취침 성공 여부 판정
        val bedtimeSuccess = checkBedtimeSuccess(timeString)

        if (bedtimeSuccess) {
            Log.d("NightRoutine", "✅ 목표 시간 내 체크인 - 취침 성공!")
        } else {
            Log.d("NightRoutine", "⚠️ 목표 시간 초과 - 취침 늦음")
        }

        // 취침 기록 저장
        sharedPreferences.edit().apply {
            putBoolean("bedtime_success_$today", bedtimeSuccess)
            putString("actual_bedtime_$today", timeString)
            putLong("bedtime_checkin_time", currentTime)
            apply()
        }

        // 알람 설정
        setupAlarm()

        // 알람 볼륨 최대로
        setMaxAlarmVolume()

        // 락스크린으로 이동
        goToLockScreen()
    }

    // 취침 성공 판정 (목표 시간보다 일찍 체크인했는지)
    private fun checkBedtimeSuccess(currentTimeString: String): Boolean {
        val targetBedtime = sharedPreferences.getString("today_bedtime", "23:00") ?: "23:00"

        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTime = sdf.parse(currentTimeString)
            val targetTime = sdf.parse(targetBedtime)

            if (currentTime != null && targetTime != null) {
                // 목표 시간 이전이거나 같으면 성공
                val success = currentTime.time <= targetTime.time
                Log.d("NightRoutine", "목표: $targetBedtime vs 실제: $currentTimeString = $success")
                success
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("NightRoutine", "시간 비교 실패", e)
            false
        }
    }

    // 알람 설정
    private fun setupAlarm() {
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val alarmManager = DailyAlarmManager(this)

        val success = alarmManager.updateDailyAlarm(currentDay)

        if (success) {
            Log.d("NightRoutine", "알람 설정 완료 (Day $currentDay)")
            Toast.makeText(this, "알람이 설정되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("NightRoutine", "알람 설정 실패 - 권한 확인 필요")
            Toast.makeText(this, "알람 설정 실패\n권한을 확인해주세요", Toast.LENGTH_LONG).show()
        }
    }

    // 알람 볼륨 최대로 설정
    private fun setMaxAlarmVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            // 원래 볼륨 저장 (한번만)
            if (!sharedPreferences.contains("original_alarm_volume")) {
                sharedPreferences.edit().putInt("original_alarm_volume", currentVolume).apply()
                Log.d("NightRoutine", "원래 볼륨 저장: $currentVolume")
            }

            // 볼륨을 최대로
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            Log.d("NightRoutine", "알람 볼륨 최대로 설정: $currentVolume -> $maxVolume")
        } catch (e: Exception) {
            Log.e("NightRoutine", "볼륨 설정 실패", e)
        }
    }

    // 락스크린으로 이동
    private fun goToLockScreen() {
        // 잠금 플래그 설정
        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().apply {
            putBoolean("isLocked", true)
            putBoolean("is_sleep_mode", true)
            apply()
        }

        Log.d("NightRoutine", "락스크린으로 이동 (수면 모드)")

        val intent = Intent(this, LockScreenActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // 오늘 날짜 (yyyy-MM-dd)
    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // 현재 시간 (HH:mm)
    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}