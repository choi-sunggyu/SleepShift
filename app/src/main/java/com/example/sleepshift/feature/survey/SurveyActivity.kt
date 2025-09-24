package com.example.sleepshift.feature.survey

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.data.SleepSettings
import com.example.sleepshift.data.SleepProgress
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.KstTime
import com.example.sleepshift.util.DailyAlarmManager // 새로 추가
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SurveyActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var surveyAdapter: SurveyAdapter
    private val totalPages = 5
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 설문 데이터
    var avgBedTime: LocalTime = LocalTime.of(4, 0)
    var avgWakeTime: LocalTime = LocalTime.of(13, 0)
    var reasonToChange: String = ""
    var goalSleepDuration: Int = 8 * 60 // 8시간 (분)
    var goalWakeTime: LocalTime = LocalTime.of(7, 0)
    var morningGoal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewPager = ViewPager2(this)
        setContentView(viewPager)

        setupViewPager()
    }

    private fun setupViewPager() {
        surveyAdapter = SurveyAdapter(this)
        viewPager.adapter = surveyAdapter
        viewPager.isUserInputEnabled = false

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                android.util.Log.d("SurveyActivity", "Current page: $position")
            }
        })
    }

    fun moveToNextPage() {
        val currentItem = viewPager.currentItem
        android.util.Log.d("SurveyActivity", "moveToNextPage called. Current: $currentItem, Total: $totalPages")

        if (currentItem < totalPages - 1) {
            viewPager.currentItem = currentItem + 1
            android.util.Log.d("SurveyActivity", "Moving to page: ${currentItem + 1}")
        } else {
            finishSurvey()
        }
    }

    fun moveToPreviousPage() {
        val currentItem = viewPager.currentItem
        if (currentItem > 0) {
            viewPager.currentItem = currentItem - 1
        }
    }

    private fun finishSurvey() {
        android.util.Log.d("SurveyActivity", "finishSurvey called")

        lifecycleScope.launch {
            val repo = SleepRepository(this@SurveyActivity)
            val targetBedtime = goalWakeTime.minusMinutes(goalSleepDuration.toLong())

            // ===== 새로운 알고리즘 적용 =====
            val alarmManager = DailyAlarmManager(this@SurveyActivity)

            // 알고리즘용 SharedPreferences 저장
            val sharedPref = getSharedPreferences("SleepShiftPrefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                // a: 평균 취침시간 (알람시간 계산의 기준이 되는 현재 수면패턴)
                putString("survey_average_bedtime", avgBedTime.format(hhmm))
                // b: 희망 기상시간 (최종 도달하고자 하는 기상시간)
                putString("survey_desired_wake_time", goalWakeTime.format(hhmm))
                // c: 최소 수면시간 (분단위)
                putInt("survey_min_sleep_minutes", goalSleepDuration)
                // x: 목표 기상시간 (= 희망 기상시간과 동일)
                putString("survey_target_wake_time", goalWakeTime.format(hhmm))

                // 앱 시작 날짜 설정 (Day 1 계산용)
                putLong("app_install_date", System.currentTimeMillis())
                putBoolean("survey_completed", true)

                // 알람 활성화 플래그
                putBoolean("alarm_enabled", true)

                apply()
            }

            // 첫 번째 알람 시간 계산 및 설정 (Day 1)
            alarmManager.updateDailyAlarm(1)
            // ===== 알고리즘 적용 완료 =====

            // 기존 Room DB 저장 (기존 코드와 호환성 유지)
            var scheduled = avgBedTime.minusMinutes(20)
            if (isBefore(scheduled, targetBedtime)) {
                scheduled = targetBedtime
            }

            val settings = SleepSettings(
                avgBedTime = avgBedTime.format(hhmm),
                avgWakeTime = avgWakeTime.format(hhmm),
                goalWakeTime = goalWakeTime.format(hhmm),
                goalSleepDuration = minutesToHHmm(goalSleepDuration),
                targetBedtime = targetBedtime.format(hhmm),
                morningGoal = morningGoal,
                reasonToChange = reasonToChange
            )

            val progress = SleepProgress(
                scheduledBedtime = scheduled.format(hhmm),
                consecutiveSuccessDays = 0,
                dailyShiftMinutes = 30,
                lastProgressUpdate = KstTime.todayYmd()
            )

            repo.saveSettings(settings)
            repo.saveProgress(progress)

            Toast.makeText(this@SurveyActivity, "수면 일정이 설정되었습니다!", Toast.LENGTH_SHORT).show()

            // 홈으로 이동
            val intent = Intent(this@SurveyActivity, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // Helper functions
    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    private fun isBefore(a: LocalTime, b: LocalTime): Boolean = a.isBefore(b)
}