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
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.util.*
import kotlin.random.Random

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var consecutiveSuccessManager: ConsecutiveSuccessManager
    private val floatingHandler = Handler(Looper.getMainLooper())
    private var floatingRunnable: Runnable? = null
    private val progressDots = mutableListOf<android.view.View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // 연속 성공 매니저 초기화
        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        // 프로그램 시작일이 설정되지 않았다면 오늘로 설정
        if (sharedPreferences.getLong("app_install_date", 0L) == 0L) {
            setAppInstallDate()
        }

        setupProgressDots()
        setupClickListeners()
        updateUI()
        startFloatingAnimation()

        // 일일 체크
        checkDailyProgress()
    }

    private fun setupProgressDots() {
        // 3개의 진행도 점만 사용
        progressDots.clear()
        progressDots.addAll(listOf(
            binding.progressDot1,
            binding.progressDot2,
            binding.progressDot3
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

        // 경험치 바 업데이트 (3개 점)
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
        val currentStreak = consecutiveSuccessManager.getCurrentStreak()

        // 모든 점을 비활성화로 초기화
        progressDots.forEach { dot ->
            dot.setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_inactive)
        }

        // 현재 연속 성공만큼 활성화 (최대 3개)
        val activeDots = minOf(currentStreak, 3)
        for (i in 0 until activeDots) {
            progressDots[i].setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_active)
        }

        android.util.Log.d("HomeActivity", "연속 성공: ${currentStreak}일")
    }

    private fun checkDailyProgress() {
        val today = getTodayDateString()
        val lastCheckDate = sharedPreferences.getString("last_daily_check", "")

        // 날짜가 바뀌었으면 일일 데이터 리셋
        if (lastCheckDate != today) {
            consecutiveSuccessManager.resetDailyData()

            // 어제의 성공 여부 체크
            if (lastCheckDate != null) {
                if (lastCheckDate.isNotEmpty()) {
                    checkYesterdaySuccess()
                }
            }

            with(sharedPreferences.edit()) {
                putString("last_daily_check", today)
                apply()
            }
        }

        // 오늘의 진행 상황 체크
        updateTodayProgress()
    }

    private fun checkYesterdaySuccess() {
        val success = consecutiveSuccessManager.checkTodaySuccess()

        if (success) {
            val streakBefore = consecutiveSuccessManager.getCurrentStreak()
            consecutiveSuccessManager.recordSuccess()
            val streakAfter = consecutiveSuccessManager.getCurrentStreak()

            // 3일 달성시 축하 메시지 (리셋되어 0이 됨)
            if (streakBefore == 3 && streakAfter == 0) {
                showStreakCompletionDialog()
            }
        } else {
            consecutiveSuccessManager.recordFailure()
            android.widget.Toast.makeText(
                this,
                "연속 기록이 끊겼습니다. 다시 도전하세요!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        updateProgressDots()
    }

    private fun updateTodayProgress() {
        // 오늘의 진행 상황 표시
        val todaySuccess = consecutiveSuccessManager.checkTodaySuccess()

        if (todaySuccess) {
            android.util.Log.d("HomeActivity", "오늘 성공! 내일 점이 채워집니다")
        }
    }

    private fun showStreakCompletionDialog() {
        val totalCompletions = sharedPreferences.getInt("total_streak_completions", 0)

        android.app.AlertDialog.Builder(this)
            .setTitle("3일 연속 달성!")
            .setMessage(
                "축하합니다!\n" +
                        "3일 연속으로 규칙적인 수면을 유지했습니다.\n\n" +
                        "보너스 코인 ${ConsecutiveSuccessManager.COMPLETION_REWARD}개 획득!\n" +
                        "총 ${totalCompletions}회 달성"
            )
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                updateUI()
            }
            .setCancelable(false)
            .show()
    }

    private fun startFloatingAnimation() {
        val bubble: View = findViewById(com.example.sleepshift.R.id.bedtimeFloatingBubble)

        // 위아래로 부드럽게 이동하는 애니메이션
        val moveUp = ObjectAnimator.ofFloat(bubble, "translationY", 0f, -50f)
        moveUp.setDuration(2000)
        moveUp.interpolator = AccelerateDecelerateInterpolator()

        val moveDown = ObjectAnimator.ofFloat(bubble, "translationY", -50f, 0f)
        moveDown.setDuration(2000)
        moveDown.interpolator = AccelerateDecelerateInterpolator()

        // 애니메이션 시퀀스 생성
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(moveUp, moveDown)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animation.start()
            }
        })

        animatorSet.start()
    }

    private fun goToBed() {
        // 취침 시간 기록
        consecutiveSuccessManager.recordBedtime(System.currentTimeMillis())

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
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openReport() {
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    private fun showPawCoinInfo() {
        android.widget.Toast.makeText(
            this,
            "발바닥 코인: 잠금화면 해제에 사용할 수 있는 토큰입니다!",
            android.widget.Toast.LENGTH_LONG
        ).show()

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

    private fun getCurrentBedtime(): String {
        val bedtimeHour = sharedPreferences.getInt("bedtime_hour", 23)
        val bedtimeMinute = sharedPreferences.getInt("bedtime_minute", 0)
        return String.format("%02d:%02d", bedtimeHour, bedtimeMinute)
    }

    private fun getCurrentDay(): Int {
        val installDate = sharedPreferences.getLong("app_install_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - installDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            else -> daysDiff
        }
    }

    private fun getPawCoinCount(): Int {
        return sharedPreferences.getInt("paw_coin_count", 130)
    }

    fun addPawCoins(amount: Int) {
        val currentCount = getPawCoinCount()
        val newCount = currentCount + amount

        with(sharedPreferences.edit()) {
            putInt("paw_coin_count", newCount)
            apply()
        }

        updatePawCoinCount()
        showCoinEarnedAnimation(amount)
    }

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
        android.widget.Toast.makeText(
            this,
            "+$amount 발바닥 코인 획득!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun setBedtime(hour: Int, minute: Int) {
        with(sharedPreferences.edit()) {
            putInt("bedtime_hour", hour)
            putInt("bedtime_minute", minute)
            apply()
        }
        updateBedtime()
    }

    private fun setAppInstallDate() {
        with(sharedPreferences.edit()) {
            putLong("app_install_date", System.currentTimeMillis())
            apply()
        }
    }

    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)+1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        updateProgressDots()

        if (floatingRunnable == null) {
            startFloatingAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        floatingRunnable?.let {
            floatingHandler.removeCallbacks(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingRunnable?.let {
            floatingHandler.removeCallbacks(it)
        }
        floatingRunnable = null
    }
}