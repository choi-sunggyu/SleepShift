package com.example.sleepshift.feature

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R

class HomeActivity : AppCompatActivity() {

    private lateinit var btnSettings: TextView
    private lateinit var btnGoToBed: LinearLayout
    private lateinit var btnCalendar: TextView
    private lateinit var tvBedtime: TextView

    // 프로그레스 점들
    private lateinit var day1Dot: View
    private lateinit var day2Dot: View
    private lateinit var day3Dot: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupClickListeners()
        updateUI()
    }

    private fun initViews() {
        // 안전하게 findViewById 처리
        try {
            btnSettings = findViewById(R.id.btnSettings)
            btnGoToBed = findViewById(R.id.btnGoToBed)
            btnCalendar = findViewById(R.id.btnCalendar)
            tvBedtime = findViewById(R.id.tvAlarmTime)

            day1Dot = findViewById(R.id.day1Dot)
            day2Dot = findViewById(R.id.day2Dot)
            day3Dot = findViewById(R.id.day3Dot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        // 설정 버튼
        btnSettings.setOnClickListener {
            openSettings()
        }

        // 자러가기 버튼
        btnGoToBed.setOnClickListener {
            goToBed()
        }

        // 달력 버튼 (리포트)
        btnCalendar.setOnClickListener {
            openReport()
        }
    }

    private fun updateUI() {
        // 취침 시간 업데이트
        updateBedtime()

        // 경험치 바 업데이트
        updateProgressDots()
    }

    private fun updateBedtime() {
        // 현재 설정된 취침 시간을 가져와서 표시
        val bedtime = getCurrentBedtime() // 이 메소드는 구현 필요
        tvBedtime.text = bedtime
    }

    private fun updateProgressDots() {
        // 현재 진행 상황에 따라 점들의 상태 업데이트
        val currentDay = getCurrentDay() // 이 메소드는 구현 필요

        // 모든 점을 비활성화로 초기화
        day1Dot.setBackgroundResource(R.drawable.progress_dot_inactive)
        day2Dot.setBackgroundResource(R.drawable.progress_dot_inactive)
        day3Dot.setBackgroundResource(R.drawable.progress_dot_inactive)

        // 현재 날짜까지 활성화
        when (currentDay) {
            1 -> day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
            2 -> {
                day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
                day2Dot.setBackgroundResource(R.drawable.progress_dot_active)
            }
            3 -> {
                day1Dot.setBackgroundResource(R.drawable.progress_dot_active)
                day2Dot.setBackgroundResource(R.drawable.progress_dot_active)
                day3Dot.setBackgroundResource(R.drawable.progress_dot_active)
            }
        }
    }

    private fun goToBed() {
        // 나이트 루틴 화면으로 이동
        val intent = Intent(this, NightRoutineActivity::class.java)
        startActivity(intent)

        // 애니메이션 효과
        btnGoToBed.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                btnGoToBed.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun openSettings() {
        // 설정 화면으로 이동
        // TODO: SettingsActivity 또는 Fragment 열기
    }

    private fun openReport() {
        // 달력 화면으로 이동
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    // 구현이 필요한 헬퍼 메소드들
    private fun getCurrentBedtime(): String {
        // SharedPreferences 또는 데이터베이스에서 설정된 취침 시간 가져오기
        return "12:30" // 임시 값
    }

    private fun getCurrentDay(): Int {
        // 현재 진행 중인 날짜 반환 (1-3)
        return 1 // 임시 값
    }
}