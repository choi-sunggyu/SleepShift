package com.example.sleepshift.feature.survey

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.SplashActivity
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.data.SleepSettings
import com.example.sleepshift.data.SleepProgress
import com.example.sleepshift.feature.LoadingActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.KstTime
import com.example.sleepshift.util.DailyAlarmManager
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SurveyActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var surveyAdapter: SurveyAdapter
    private val totalPages = 6  // ⭐ 5 → 6으로 변경 (이름 페이지 추가)
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ⭐ 사용자 이름 변수 추가
    var userName: String = ""

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
        lifecycleScope.launch {
            val repo = SleepRepository(this@SurveyActivity)
            val targetBedtime = goalWakeTime.minusMinutes(goalSleepDuration.toLong())

            val sharedPref = getSharedPreferences("SleepShiftPrefs", MODE_PRIVATE)

            with(sharedPref.edit()) {
                putString("user_name", userName)

                // ⭐ 키 수정 (survey_ 접두사 제거)
                putString("avg_bedtime", avgBedTime.format(hhmm))  // "02:00"
                putString("target_wake_time", goalWakeTime.format(hhmm))  // "06:30"
                putString("avg_wake_time", avgWakeTime.format(hhmm))
                putInt("min_sleep_minutes", goalSleepDuration)  // 420

                // 앱 시작 날짜
                putLong("app_install_date", System.currentTimeMillis())

                // 설문 완료 플래그
                putBoolean("survey_completed", true)
                putBoolean("alarm_enabled", true)

                // 코인 초기화
                putInt("paw_coin_count", 30)
                putBoolean("is_first_run", false)

                apply()
            }

            // ⭐ 저장 직후 다시 읽어서 확인
            val savedBedtime = sharedPref.getString("avg_bedtime", "없음")
            val savedWakeTime = sharedPref.getString("target_wake_time", "없음")
            val savedSleepMinutes = sharedPref.getInt("min_sleep_minutes", -1)


            android.util.Log.d("SurveyActivity", """
                === 저장 직후 확인 ===
                입력값:
                  avgBedTime: ${avgBedTime.format(hhmm)}
                  goalWakeTime: ${goalWakeTime.format(hhmm)}
                  goalSleepDuration: $goalSleepDuration
                
                저장된 값 확인:
                  avg_bedtime: $savedBedtime
                  target_wake_time: $savedWakeTime
                  min_sleep_minutes: $savedSleepMinutes
            """.trimIndent())

            SplashActivity.markSurveyCompleted(this@SurveyActivity)

            // 첫 번째 알람 설정
            try {
                val alarmManager = DailyAlarmManager(this@SurveyActivity)
                alarmManager.updateDailyAlarm(1)
                android.util.Log.d("SurveyActivity", "Day 1 알람 설정 완료")
            } catch (e: Exception) {
                android.util.Log.e("SurveyActivity", "알람 설정 실패: ${e.message}")
            }

            // 기존 Room DB 저장
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

            // ⭐⭐⭐ HomeActivity 대신 LoadingActivity로 이동
            val intent = Intent(this@SurveyActivity, LoadingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // Helper functions
    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    private fun isBefore(a: LocalTime, b: LocalTime): Boolean = a.isBefore(b)
}