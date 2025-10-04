package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.databinding.ActivitySettingsBinding
import com.example.sleepshift.util.DailyAlarmManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var alarmManager: DailyAlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        alarmManager = DailyAlarmManager(this)

        setupUI()
        setupButtons()
        setupAlarmMode()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        updateDisplayedTimes()

        // 취침 알림 스위치
        binding.switchBedtimeNotification.isChecked = sharedPreferences.getBoolean("bedtime_notification_enabled", true)
        binding.switchBedtimeNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("bedtime_notification_enabled", isChecked).apply()

            // 알람 재설정 (취침 알림 포함)
            val currentDay = sharedPreferences.getInt("current_day", 1)
            alarmManager.updateDailyAlarm(currentDay)

            val message = if (isChecked) "취침 10분 전 알림이 활성화되었습니다" else "취침 알림이 비활성화되었습니다"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // 알림 스위치
        binding.switchNotification.isChecked = sharedPreferences.getBoolean("notification_enabled", true)
        binding.switchNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("notification_enabled", isChecked).apply()
        }

        // 진동 스위치
        binding.switchVibration.isChecked = sharedPreferences.getBoolean("vibration_enabled", true)
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("vibration_enabled", isChecked).apply()
        }
    }

    /**
     * ⭐ 알람 모드 설정
     */
    private fun setupAlarmMode() {
        val isCustomMode = sharedPreferences.getBoolean("alarm_mode_custom", true)

        // 저장된 모드 적용
        if (isCustomMode) {
            binding.radioCustomMode.isChecked = true
        } else {
            binding.radioNormalMode.isChecked = true
        }

        // UI 업데이트
        updateButtonsVisibility(isCustomMode)

        // 라디오 버튼 리스너
        binding.radioGroupAlarmMode.setOnCheckedChangeListener { _, checkedId ->
            val newCustomMode = checkedId == binding.radioCustomMode.id

            // 모드 저장
            sharedPreferences.edit().putBoolean("alarm_mode_custom", newCustomMode).apply()

            // UI 업데이트
            updateButtonsVisibility(newCustomMode)

            // 알람 재계산
            val currentDay = sharedPreferences.getInt("current_day", 1)
            alarmManager.updateDailyAlarm(currentDay)
            updateDisplayedTimes()

            val modeName = if (newCustomMode) "커스텀 모드" else "일반 모드"
            Toast.makeText(this, "${modeName}로 변경되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ⭐ 모드에 따라 버튼 활성화/비활성화
     */
    private fun updateButtonsVisibility(isCustomMode: Boolean) {
        if (isCustomMode) {
            // 커스텀 모드: 변경 불가
            binding.btnSetBedtime.isEnabled = false
            binding.btnSetBedtime.alpha = 0.5f
            binding.btnSetTargetWakeTime.isEnabled = false
            binding.btnSetTargetWakeTime.alpha = 0.5f

            binding.tvAlarmModeDescription.text = "점진적 조정이 반영된 오늘의 실제 알람 시간입니다"
        } else {
            // 일반 모드: 변경 가능
            binding.btnSetBedtime.isEnabled = true
            binding.btnSetBedtime.alpha = 1.0f
            binding.btnSetTargetWakeTime.isEnabled = true
            binding.btnSetTargetWakeTime.alpha = 1.0f

            binding.tvAlarmModeDescription.text = "설정한 목표 기상 시간으로 바로 적용됩니다"
        }
    }

    private fun updateDisplayedTimes() {
        val avgBedtime = sharedPreferences.getString("avg_bedtime", "23:00") ?: "23:00"
        binding.tvCurrentBedtime.text = avgBedtime

        val targetWakeTime = sharedPreferences.getString("target_wake_time", "07:00") ?: "07:00"
        binding.tvTargetWakeTime.text = targetWakeTime

        val todayAlarmTime = sharedPreferences.getString("today_alarm_time", targetWakeTime) ?: targetWakeTime
        val currentDay = sharedPreferences.getInt("current_day", 1)
        binding.tvCurrentAlarm.text = "$todayAlarmTime (Day $currentDay)"
    }

    private fun setupButtons() {
        binding.btnSetBedtime.setOnClickListener {
            showTimePicker("avg_bedtime", binding.tvCurrentBedtime)
        }

        binding.btnSetTargetWakeTime.setOnClickListener {
            showTimePicker("target_wake_time", binding.tvTargetWakeTime)
        }
    }

    private fun showTimePicker(key: String, displayTextView: android.widget.TextView) {
        val currentTime = sharedPreferences.getString(key, "07:00") ?: "07:00"
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val newTime = String.format("%02d:%02d", selectedHour, selectedMinute)

                sharedPreferences.edit().putString(key, newTime).apply()
                displayTextView.text = newTime

                val currentDay = sharedPreferences.getInt("current_day", 1)
                alarmManager.updateDailyAlarm(currentDay)

                updateDisplayedTimes()

                val label = if (key == "avg_bedtime") "취침 시간" else "목표 기상 시간"
                Toast.makeText(this, "$label 이 $newTime 으로 변경되었습니다", Toast.LENGTH_SHORT).show()
            },
            hour,
            minute,
            true
        ).show()
    }
}