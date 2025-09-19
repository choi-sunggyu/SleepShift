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

        // ViewPager2 설정
        viewPager = ViewPager2(this)
        setContentView(viewPager)

        setupViewPager()
    }

    private fun setupViewPager() {
        surveyAdapter = SurveyAdapter(this)
        viewPager.adapter = surveyAdapter

        // 스와이프 비활성화 (버튼으로만 이동)
        viewPager.isUserInputEnabled = false

        // 페이지 변경 리스너
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                android.util.Log.d("SurveyActivity", "Current page: $position")
            }
        })
    }

    // Fragment에서 호출할 메서드
    fun moveToNextPage() {
        val currentItem = viewPager.currentItem
        android.util.Log.d("SurveyActivity", "moveToNextPage called. Current: $currentItem, Total: $totalPages")

        if (currentItem < totalPages - 1) {
            viewPager.currentItem = currentItem + 1
            android.util.Log.d("SurveyActivity", "Moving to page: ${currentItem + 1}")
        } else {
            // 마지막 페이지에서 설문 완료
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

            // 초기 scheduled = max(target, avgBed - 20분)
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

            // SharedPreferences에 설문 완료 표시
            val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("survey_completed", true)
                apply()
            }

            Toast.makeText(this@SurveyActivity, "설정이 저장되었습니다", Toast.LENGTH_SHORT).show()

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