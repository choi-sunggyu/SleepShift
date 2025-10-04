package com.example.sleepshift.feature.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.util.DailyAlarmManager
import com.example.sleepshift.databinding.ActivityHomeBinding
import com.example.sleepshift.feature.NightRoutineActivity
import com.example.sleepshift.feature.ReportActivity
import com.example.sleepshift.feature.SettingsActivity
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var consecutiveSuccessManager: ConsecutiveSuccessManager
    private val floatingHandler = Handler(Looper.getMainLooper())
    private var floatingRunnable: Runnable? = null
    private val progressDots = mutableListOf<android.view.View>()
    private lateinit var alarmManager: DailyAlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        alarmManager = DailyAlarmManager(this)

        // 배터리 최적화 해제 요청
        requestIgnoreBatteryOptimization()

        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        // 프로그램 시작일이 설정되지 않았다면 오늘로 설정
        if (sharedPreferences.getLong("app_install_date", 0L) == 0L) {
            setAppInstallDate()
        }

        // 코인 초기화 (앱 최초 실행 시 10개)
        initializePawCoins()

        setupProgressDots()
        setupClickListeners()
        updateUI()
        startFloatingAnimation()

        checkDailyProgress()

        // 매일 알람 설정 (핵심 기능)
        setupDailyAlarm()
    }

    /**
     * 매일 알람 설정
     */
    private fun setupDailyAlarm() {
        val surveyCompleted = sharedPreferences.getBoolean("survey_completed", false)

        if (surveyCompleted) {
            val currentDay = getCurrentDay()
            android.util.Log.d("HomeActivity", "Day $currentDay 알람 설정 시작")

            try {
                alarmManager.updateDailyAlarm(currentDay)
                android.util.Log.d("HomeActivity", "알람 설정 완료")
            } catch (e: Exception) {
                android.util.Log.e("HomeActivity", "알람 설정 실패: ${e.message}")
            }
        } else {
            android.util.Log.d("HomeActivity", "설문조사 미완료 - 알람 설정 생략")
        }
    }

    /**
     * 코인 초기화 (최초 실행 시 10개)
     */
    private fun initializePawCoins() {
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)

        if (isFirstRun) {
            with(sharedPreferences.edit()) {
                putInt("paw_coin_count", 10)
                putBoolean("is_first_run", false)
                apply()
            }
            android.util.Log.d("HomeActivity", "초기 코인 10개 설정됨")
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("HomeActivity", "배터리 최적화 해제 요청 실패: ${e.message}")
                }
            }
        }
    }

    private fun setupProgressDots() {
        progressDots.clear()
        progressDots.addAll(listOf(
            binding.progressDot1,
            binding.progressDot2,
            binding.progressDot3
        ))
    }

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener {
            openSettings()
        }

        binding.btnGoToBed.setOnClickListener {
            goToBed()
        }

        binding.btnCalendar.setOnClickListener {
            openReport()
        }

        binding.imgPawCoin.setOnClickListener {
            showPawCoinInfo()
        }
    }

    private fun updateUI() {
        updateDayCount()
        updateBedtime()
        updatePawCoinCount()
        updateProgressDots()
    }

    private fun updateDayCount() {
        val currentDay = getCurrentDay()
        binding.tvDayCount.text = "Day $currentDay"
    }

    private fun updateBedtime() {
        val todayAlarmTime = sharedPreferences.getString("today_alarm_time", null)

        if (todayAlarmTime != null) {
            binding.tvBedtime.text = todayAlarmTime
        } else {
            val bedtime = getCurrentBedtime()
            binding.tvBedtime.text = bedtime
        }
    }

    private fun updatePawCoinCount() {
        val coinCount = getPawCoinCount()
        binding.tvPawCoinCount.text = coinCount.toString()
    }

    private fun updateProgressDots() {
        val currentStreak = consecutiveSuccessManager.getCurrentStreak()

        progressDots.forEach { dot ->
            dot.setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_inactive)
        }

        val activeDots = minOf(currentStreak, 3)
        for (i in 0 until activeDots) {
            progressDots[i].setBackgroundResource(com.example.sleepshift.R.drawable.progress_dot_active)
        }

        android.util.Log.d("HomeActivity", "연속 성공: ${currentStreak}일")
    }

    private fun checkDailyProgress() {
        val today = getTodayDateString()
        val lastCheckDate = sharedPreferences.getString("last_daily_check", "")

        if (lastCheckDate != today) {
            consecutiveSuccessManager.resetDailyData()

            if (lastCheckDate != null && lastCheckDate.isNotEmpty()) {
                checkYesterdaySuccess()
            }

            with(sharedPreferences.edit()) {
                putString("last_daily_check", today)
                apply()
            }

            setupDailyAlarm()
        }

        updateTodayProgress()
    }

    private fun checkYesterdaySuccess() {
        val success = consecutiveSuccessManager.checkTodaySuccess()

        if (success) {
            val streakBefore = consecutiveSuccessManager.getCurrentStreak()
            consecutiveSuccessManager.recordSuccess()
            val streakAfter = consecutiveSuccessManager.getCurrentStreak()

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

        val moveUp = ObjectAnimator.ofFloat(bubble, "translationY", 0f, -50f)
        moveUp.duration = 2000
        moveUp.interpolator = AccelerateDecelerateInterpolator()

        val moveDown = ObjectAnimator.ofFloat(bubble, "translationY", -50f, 0f)
        moveDown.duration = 2000
        moveDown.interpolator = AccelerateDecelerateInterpolator()

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
        consecutiveSuccessManager.recordBedtime(System.currentTimeMillis())

        val intent = Intent(this, NightRoutineActivity::class.java)
        startActivity(intent)

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
        return sharedPreferences.getInt("paw_coin_count", 10)
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
        return String.format(
            "%d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
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