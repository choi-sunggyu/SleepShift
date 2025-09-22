package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        setupClickListeners()
        updateCurrentSettings()
    }

    private fun setupClickListeners() {
        // 취침 시간 설정 버튼
        findViewById<android.widget.Button>(R.id.btnSetBedtime).setOnClickListener {
            showBedtimePickerDialog()
        }

        // 알람 시간 설정 버튼
        findViewById<android.widget.Button>(R.id.btnSetAlarm).setOnClickListener {
            showAlarmPickerDialog()
        }

        // 뒤로가기 버튼
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun updateCurrentSettings() {
        val bedtimeHour = sharedPreferences.getInt("bedtime_hour", 23)
        val bedtimeMinute = sharedPreferences.getInt("bedtime_minute", 0)
        val alarmHour = sharedPreferences.getInt("alarm_hour", 7)
        val alarmMinute = sharedPreferences.getInt("alarm_minute", 0)

        findViewById<android.widget.TextView>(R.id.tvCurrentBedtime).text =
            String.format("%02d:%02d", bedtimeHour, bedtimeMinute)

        findViewById<android.widget.TextView>(R.id.tvCurrentAlarm).text =
            String.format("%02d:%02d", alarmHour, alarmMinute)
    }

    private fun showBedtimePickerDialog() {
        val currentHour = sharedPreferences.getInt("bedtime_hour", 23)
        val currentMinute = sharedPreferences.getInt("bedtime_minute", 0)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                // 취침 시간 저장
                with(sharedPreferences.edit()) {
                    putInt("bedtime_hour", hourOfDay)
                    putInt("bedtime_minute", minute)
                    apply()
                }
                updateCurrentSettings()
                Toast.makeText(this, "취침 시간이 설정되었습니다", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            true // 24시간 형식
        )

        timePickerDialog.show()
    }

    private fun showAlarmPickerDialog() {
        val currentHour = sharedPreferences.getInt("alarm_hour", 7)
        val currentMinute = sharedPreferences.getInt("alarm_minute", 0)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                // 알람 시간 저장
                with(sharedPreferences.edit()) {
                    putInt("alarm_hour", hourOfDay)
                    putInt("alarm_minute", minute)
                    apply()
                }
                updateCurrentSettings()
                Toast.makeText(this, "알람 시간이 설정되었습니다", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            true // 24시간 형식
        )

        timePickerDialog.show()
    }
}