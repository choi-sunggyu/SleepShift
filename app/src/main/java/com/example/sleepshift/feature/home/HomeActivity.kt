package com.example.sleepshift.feature.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.databinding.ActivityHomeBinding
import com.example.sleepshift.feature.NightRoutineActivity
import com.example.sleepshift.feature.ReportActivity
import com.example.sleepshift.feature.SettingsActivity
import java.util.*
import kotlin.random.Random


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val floatingHandler = Handler(Looper.getMainLooper())
    private var floatingRunnable: Runnable? = null
    private val progressDots = mutableListOf<android.view.View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // 프로그램 시작일이 설정되지 않았다면 오늘로 설정
        if (sharedPreferences.getLong("app_install_date", 0L) == 0L) {
            setAppInstallDate()
        }

        setupProgressDots()
        setupClickListeners()
        updateUI()
        startFloatingAnimation()
    }

    private fun setupProgressDots() {
        // 진행도 점들을 리스트에 추가
        progressDots.clear()
        progressDots.addAll(listOf(
            binding.progressDot1, binding.progressDot2, binding.progressDot3,
            binding.progressDot4, binding.progressDot5, binding.progressDot6,
            binding.progressDot7, binding.progressDot8, binding.progressDot9,
            binding.progressDot10, binding.progressDot11, binding.progressDot12,
            binding.progressDot13, binding.progressDot14, binding.progressDot15
        ))
    }

    private fun setupClickListeners() {
        // 설정 버튼
        binding.btnSettings.setOnClickListener {
            openSettings()
        }

        // 자러가기 버튼
        binding.btnGoToBed.setOnClickListener {
            goToBed()
        }

        // 달력 버튼 (리포트)
        binding.btnCalendar.setOnClickListener {
            openReport()
        }

        // 발바닥 코인 클릭 (코인 정보 표시)
        binding.imgPawCoin.setOnClickListener {
            showPawCoinInfo()
        }
    }

    private fun updateUI() {
        // Day 카운트 업데이트
        updateDayCount()

        // 취침 시간 업데이트
        updateBedtime()

        // 발바닥 코인 개수 업데이트
        updatePawCoinCount()

        // 경험치 바 업데이트
        updateProgressDots()
    }

    private fun updateDayCount() {
        val currentDay = getCurrentDay()
        binding.tvDayCount.text = "Day $currentDay"
    }

    private fun updateBedtime() {
        // 현재 설정된 취침 시간을 가져와서 표시
        val bedtime = getCurrentBedtime()
        binding.tvBedtime.text = bedtime
    }

    private fun updatePawCoinCount() {
        val coinCount = getPawCoinCount()
        binding.tvPawCoinCount.text = coinCount.toString()
    }

    private fun updateProgressDots() {
        val currentDay = getCurrentDay()

        // 모든 점을 비활성화로 초기화
        progressDots.forEach { dot ->
            dot.setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_inactive)
        }

        // 현재 날짜까지 활성화 (최대 15개)
        val activeDots = minOf(currentDay, 15)
        for (i in 0 until activeDots) {
            progressDots[i].setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_active)
        }
    }

    private fun startFloatingAnimation() {
        val bubble: View = findViewById(com.example.sleepshift.R.id.bedtimeFloatingBubble)


        // 위아래로 부드럽게 이동하는 애니메이션
        val moveUp = ObjectAnimator.ofFloat(bubble, "translationY", 0f, -50f)
        moveUp.setDuration(2000) // 2초 동안 위로 이동
        moveUp.interpolator = AccelerateDecelerateInterpolator()

        val moveDown = ObjectAnimator.ofFloat(bubble, "translationY", -50f, 0f)
        moveDown.setDuration(2000) // 2초 동안 아래로 이동
        moveDown.interpolator = AccelerateDecelerateInterpolator()


        // 애니메이션 시퀀스 생성
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(moveUp, moveDown)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 애니메이션이 끝나면 다시 시작 (무한 반복)
                animation.start()
            }
        })


        // 애니메이션 시작
        animatorSet.start()
    }

    private fun animateFloatingBubble() {
        val bubble = binding.bedtimeFloatingBubble
        val container = binding.pandaContainer

        // 현재 위치를 기준으로 상대적 이동
        val currentX = bubble.translationX
        val currentY = bubble.translationY

        // 위아래로 부드럽게 움직이는 범위 (-30dp ~ +30dp)
        val moveRange = 80f // 이동 범위 (dp 단위)

        // 현재 위치에서 상대적으로 이동할 거리 계산
        val deltaY = Random.nextFloat() * moveRange - (moveRange / 2) // -40 ~ +40 범위
        val deltaX = Random.nextFloat() * (moveRange / 2) - (moveRange / 4) // -20 ~ +20 범위

        // 새로운 위치 계산
        val newY = currentY + deltaY
        val newX = currentX + deltaX

        // 컨테이너 경계를 벗어나지 않도록 제한
        val maxY = 100f
        val minY = -100f
        val maxX = 150f
        val minX = -150f

        val clampedY = newY.coerceIn(minY, maxY)
        val clampedX = newX.coerceIn(minX, maxX)

        // 부드러운 애니메이션으로 이동
        val animatorX = ObjectAnimator.ofFloat(bubble, "translationX", clampedX)
        val animatorY = ObjectAnimator.ofFloat(bubble, "translationY", clampedY)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY)
        animatorSet.duration = 3000 // 3초간 부드럽게 이동
        animatorSet.start()
    }

    private fun goToBed() {
        // 나이트 루틴 화면으로 이동
        val intent = Intent(this, NightRoutineActivity::class.java)
        startActivity(intent)

        // 버튼 클릭 애니메이션
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
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openReport() {
        // 달력 화면으로 이동
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    private fun showPawCoinInfo() {
        // 발바닥 코인 정보를 보여주는 다이얼로그나 토스트
        android.widget.Toast.makeText(
            this,
            "발바닥 코인: 잠금화면 해제에 사용할 수 있는 토큰입니다!",
            android.widget.Toast.LENGTH_LONG
        ).show()

        // 코인 클릭 애니메이션
        binding.imgPawCoin.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(150)
            .withEndAction {
                binding.imgPawCoin.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    // 헬퍼 메소드들
    private fun getCurrentBedtime(): String {
        // SharedPreferences에서 설정된 취침 시간 가져오기
        val bedtimeHour = sharedPreferences.getInt("bedtime_hour", 23)
        val bedtimeMinute = sharedPreferences.getInt("bedtime_minute", 0)
        return String.format("%02d:%02d", bedtimeHour, bedtimeMinute)
    }

    private fun getCurrentDay(): Int {
        // 앱 설치일로부터 며칠이 지났는지 계산
        val installDate = sharedPreferences.getLong("app_install_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - installDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            else -> daysDiff
        }
    }

    private fun getPawCoinCount(): Int {
        // SharedPreferences에서 발바닥 코인 개수 가져오기
        return sharedPreferences.getInt("paw_coin_count", 130) // 기본값 130
    }

    // 발바닥 코인 추가 메소드
    fun addPawCoins(amount: Int) {
        val currentCount = getPawCoinCount()
        val newCount = currentCount + amount

        with(sharedPreferences.edit()) {
            putInt("paw_coin_count", newCount)
            apply()
        }

        updatePawCoinCount()

        // 코인 획득 애니메이션
        showCoinEarnedAnimation(amount)
    }

    // 발바닥 코인 사용 메소드
    fun usePawCoins(amount: Int): Boolean {
        val currentCount = getPawCoinCount()

        if (currentCount >= amount) {
            val newCount = currentCount - amount

            with(sharedPreferences.edit()) {
                putInt("paw_coin_count", newCount)
                apply()
            }

            updatePawCoinCount()
            return true
        }
        return false
    }

    private fun showCoinEarnedAnimation(amount: Int) {
        // TODO: 코인 획득 애니메이션 구현
        android.widget.Toast.makeText(
            this,
            "+$amount 발바닥 코인 획득!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // 취침 시간 설정 메소드
    fun setBedtime(hour: Int, minute: Int) {
        with(sharedPreferences.edit()) {
            putInt("bedtime_hour", hour)
            putInt("bedtime_minute", minute)
            apply()
        }
        updateBedtime()
    }

    // 앱 설치 날짜 설정
    private fun setAppInstallDate() {
        with(sharedPreferences.edit()) {
            putLong("app_install_date", System.currentTimeMillis())
            apply()
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 UI 업데이트
        updateUI()

        // floating animation 재시작
        if (floatingRunnable == null) {
            startFloatingAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        // floating animation 일시정지
        floatingRunnable?.let {
            floatingHandler.removeCallbacks(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 애니메이션 정리
        floatingRunnable?.let {
            floatingHandler.removeCallbacks(it)
        }
        floatingRunnable = null
    }
}