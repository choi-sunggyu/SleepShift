package com.example.sleepshift.feature.onboarding

import com.example.sleepshift.SplashActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.feature.survey.SurveyActivity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val totalPages = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewPager2 설정
        viewPager = ViewPager2(this)
        setContentView(viewPager)

        setupViewPager()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter(this)
        viewPager.adapter = onboardingAdapter

        // 스와이프 활성화
        viewPager.isUserInputEnabled = true

        // 페이지 변경 리스너 추가 (디버깅용)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                android.util.Log.d("OnboardingActivity", "Current page: $position")
            }
        })
    }

    // Fragment에서 호출할 메서드
    fun moveToNextPage() {
        val currentItem = viewPager.currentItem
        android.util.Log.d("OnboardingActivity", "moveToNextPage called. Current: $currentItem, Total: $totalPages")

        if (currentItem < totalPages - 1) {
            viewPager.currentItem = currentItem + 1
            android.util.Log.d("OnboardingActivity", "Moving to page: ${currentItem + 1}")
        } else {
            // 마지막 페이지에서 온보딩 완료
            finishOnboarding()
        }
    }

    // 기존 goToNextPage()와 동일한 기능으로 통합
    fun goToNextPage() {
        moveToNextPage()
    }

    fun finishOnboarding() {
        android.util.Log.d("OnboardingActivity", "finishOnboarding called")

        // ⭐⭐⭐ SplashActivity의 헬퍼 메소드 사용 (올바른 SharedPreferences에 저장)
        SplashActivity.markOnboardingCompleted(this)
        android.util.Log.d("OnboardingActivity", "온보딩 완료 플래그 저장됨")

        // 설문조사 완료 여부 확인 (올바른 SharedPreferences 사용)
        val sharedPref = getSharedPreferences("SleepShiftPrefs", MODE_PRIVATE)
        val surveyCompleted = sharedPref.getBoolean("survey_completed", false)

        android.util.Log.d("OnboardingActivity", "설문조사 완료 여부: $surveyCompleted")

        val intent = if (surveyCompleted) {
            // 설문조사까지 완료된 경우 (거의 없음)
            Intent(this, HomeActivity::class.java)
        } else {
            // 일반적으로 설문조사로 이동
            Intent(this, SurveyActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}