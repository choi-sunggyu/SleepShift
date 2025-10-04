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
        android.util.Log.d("SurveyActivity", "finishSurvey called")

        // ⭐ 이름 유효성 체크
        if (userName.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0  // 첫 페이지로 돌아가기
            return
        }

        lifecycleScope.launch {
            val repo = SleepRepository(this@SurveyActivity)
            val targetBedtime = goalWakeTime.minusMinutes(goalSleepDuration.toLong())

            // SharedPreferences 변수 선언
            val sharedPref = getSharedPreferences("SleepShiftPrefs", MODE_PRIVATE)

            // SharedPreferences 저장
            with(sharedPref.edit()) {
                // ⭐ 사용자 이름 저장
                putString("user_name", userName)

                // 알고리즘 관련 데이터
                putString("survey_average_bedtime", avgBedTime.format(hhmm))
                putString("survey_desired_wake_time", goalWakeTime.format(hhmm))
                putInt("survey_min_sleep_minutes", goalSleepDuration)
                putString("survey_target_wake_time", goalWakeTime.format(hhmm))

                // 앱 시작 날짜
                putLong("app_install_date", System.currentTimeMillis())

                // 설문 완료 플래그
                putBoolean("survey_completed", true)

                // 알람 활성화
                putBoolean("alarm_enabled", true)

                // 코인 초기화 (10개로 시작)
                putInt("paw_coin_count", 10)
                putBoolean("is_first_run", false)

                apply()
            }

            android.util.Log.d("SurveyActivity", "설문 완료 플래그 저장됨: survey_completed = true")
            android.util.Log.d("SurveyActivity", "초기 코인 10개 설정됨")
            android.util.Log.d("SurveyActivity", "사용자 이름 저장됨: $userName")

            // SplashActivity의 헬퍼 메소드 사용
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

            // HomeActivity로 이동
            val intent = Intent(this@SurveyActivity, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // Helper functions
    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    private fun isBefore(a: LocalTime, b: LocalTime): Boolean = a.isBefore(b)
}