package com.example.sleepshift.feature.onboarding

import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.feature.survey.InitSurveyActivity
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
                // 현재 페이지 로그로 확인
                android.util.Log.d("OnboardingActivity", "Current page: $position")
            }
        })
    }

    // Fragment에서 호출할 메서드들
    fun goToNextPage() {
        val currentItem = viewPager.currentItem
        android.util.Log.d("OnboardingActivity", "goToNextPage called. Current: $currentItem, Total: $totalPages")

        if (currentItem < totalPages - 1) {
            viewPager.currentItem = currentItem + 1
            android.util.Log.d("OnboardingActivity", "Moving to page: ${currentItem + 1}")
        } else {
            android.util.Log.d("OnboardingActivity", "Already at last page")
        }
    }

    fun finishOnboarding() {
        android.util.Log.d("OnboardingActivity", "finishOnboarding called")

        // SharedPreferences에 온보딩 완료 표시
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("onboarding_completed", true)
            apply()
        }

        // 설문조사 완료 여부 확인
        val surveyCompleted = sharedPref.getBoolean("survey_completed", false)

        val intent = if (surveyCompleted) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, InitSurveyActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}