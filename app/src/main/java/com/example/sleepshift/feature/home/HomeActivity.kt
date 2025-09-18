package com.example.sleepshift.feature.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityHomeBinding
import com.example.sleepshift.feature.NightRoutineActivity
import com.example.sleepshift.feature.ReportActivity
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        setupClickListeners()
        updateUI()
    }

    private fun setupClickListeners() {
        // 설정 버튼 (View Binding 사용)
        binding.btnSettings.setOnClickListener {
            openSettings()
        }

        // 자러가기 버튼 (View Binding 사용)
        binding.btnGoToBed.setOnClickListener {
            goToBed()
        }

        // 달력 버튼 (리포트) (View Binding 사용)
        binding.btnCalendar.setOnClickListener {
            openReport()
        }
    }

    private fun updateUI() {
        // 취침 시간 업데이트
        updateBedtime()

        // 경험치 바 업데이트
        updateProgressDots()

        // 현재 날짜 표시
        updateCurrentDate()
    }

    private fun updateBedtime() {
        // 현재 설정된 취침 시간을 가져와서 표시
        val bedtime = getCurrentBedtime()
        binding.tvAlarmTime.text = bedtime
    }

    private fun updateProgressDots() {
        // 현재 진행 상황에 따라 점들의 상태 업데이트
        val currentDay = getCurrentDay()

        // 모든 점을 비활성화로 초기화
        binding.day1Dot.setBackgroundResource(R.drawable.progress_dot_inactive)
        binding.day2Dot.setBackgroundResource(R.drawable.progress_dot_inactive)
        binding.day3Dot.setBackgroundResource(R.drawable.progress_dot_inactive)

        // 현재 날짜까지 활성화
        when {
            currentDay >= 1 -> {
                binding.day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
            }
            currentDay >= 2 -> {
                binding.day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
                binding.day2Dot.setBackgroundResource(R.drawable.progress_dot_active)
            }
            currentDay >= 3 -> {
                binding.day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
                binding.day2Dot.setBackgroundResource(R.drawable.progress_dot_active)
                binding.day3Dot.setBackgroundResource(R.drawable.progress_dot_active)
            }
        }
    }

    private fun updateCurrentDate() {
        // 현재 날짜 표시
        val currentDate = SimpleDateFormat("MM월 dd일", Locale.KOREA).format(Date())
        // binding에 날짜 TextView가 있다면 업데이트
        // binding.tvCurrentDate.text = currentDate
    }

    private fun goToBed() {
        // 나이트 루틴 화면으로 이동
        val intent = Intent(this, NightRoutineActivity::class.java)
        startActivity(intent)

        // 애니메이션 효과
        binding.btnGoToBed.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                binding.btnGoToBed.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun openSettings() {
        // 설정 화면으로 이동
        // TODO: SettingsActivity 생성 후 아래 주석 해제
        // val intent = Intent(this, SettingsActivity::class.java)
        // startActivity(intent)

        // 임시로 토스트 메시지 표시
        android.widget.Toast.makeText(this, "설정 기능 준비 중입니다", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun openReport() {
        // 달력 화면으로 이동
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    // 헬퍼 메소드들
    private fun getCurrentBedtime(): String {
        // SharedPreferences에서 설정된 취침 시간 가져오기
        val bedtimeHour = sharedPreferences.getInt("bedtime_hour", 23)
        val bedtimeMinute = sharedPreferences.getInt("bedtime_minute", 0)

        return String.format("%02d:%02d", bedtimeHour, bedtimeMinute)
    }

    private fun getCurrentDay(): Int {
        // SharedPreferences에서 현재 진행 중인 날짜 반환 (1-3)
        val startDate = sharedPreferences.getLong("program_start_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - startDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            daysDiff > 3 -> 3
            else -> daysDiff
        }
    }

    // 취침 시간 설정을 위한 메소드 (다른 Activity에서 호출 가능)
    fun setBedtime(hour: Int, minute: Int) {
        with(sharedPreferences.edit()) {
            putInt("bedtime_hour", hour)
            putInt("bedtime_minute", minute)
            apply()
        }
        updateBedtime()
    }

    // 프로그램 시작 날짜 설정
    fun setProgramStartDate() {
        with(sharedPreferences.edit()) {
            putLong("program_start_date", System.currentTimeMillis())
            apply()
        }
        updateProgressDots()
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 UI 업데이트
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 필요한 정리 작업이 있다면 여기에 추가
    }
}