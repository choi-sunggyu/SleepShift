package com.example.sleepshift.feature.home

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // ⭐ 알림 권한 요청 런처 추가
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("HomeActivity", "알림 권한 승인됨")
            checkAlarmPermission() // 알림 승인 후 알람 권한 체크
        } else {
            Log.w("HomeActivity", "알림 권한 거부됨")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        alarmManager = DailyAlarmManager(this)

        // 배터리 최적화 해제 요청
        requestIgnoreBatteryOptimization()

        // 권한 요청 추가
        requestAllPermissions()

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

    }

    private fun requestAllPermissions() {
        // 1단계: 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("HomeActivity", "알림 권한 이미 승인됨")
                    checkAlarmPermission() // 바로 알람 권한 체크
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 권한 설명 표시 후 요청
                    showNotificationPermissionRationale()
                }
                else -> {
                    // 직접 요청
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 이하는 알림 권한 불필요, 바로 알람 권한 체크
            checkAlarmPermission()
        }
    }

    /**
     * ⭐ 알람 권한 체크 및 요청 (Android 12+)
     */
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // ⭐ 변수명 변경 및 안전한 캐스팅
            val systemAlarmManager = getSystemService(AlarmManager::class.java)

            if (systemAlarmManager?.canScheduleExactAlarms() != true) {
                Log.w("HomeActivity", "정확한 알람 권한 없음")
                showAlarmPermissionDialog()
            } else {
                Log.d("HomeActivity", "정확한 알람 권한 있음")
                // 권한 확보 완료 - 알람 설정
                setupDailyAlarm()
            }
        } else {
            // Android 11 이하는 알람 권한 불필요
            setupDailyAlarm()
        }
    }

    /**
     * 알림 권한 설명 다이얼로그
     */
    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("알림 권한 필요")
            .setMessage("알람이 울릴 때 알림을 표시하기 위해 알림 권한이 필요합니다.")
            .setPositiveButton("권한 허용") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                checkAlarmPermission() // 알림은 선택이지만 알람은 체크
            }
            .show()
    }

    /**
     * 알람 권한 요청 다이얼로그
     */
    private fun showAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("알람 권한 필요")
            .setMessage(
                "정확한 시간에 알람을 울리기 위해서는 '알람 및 리마인더' 권한이 필요합니다.\n\n" +
                        "설정 화면으로 이동하여 권한을 허용해주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                openAlarmPermissionSettings()
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "알람 권한이 없으면 알람이 울리지 않습니다",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 알람 권한 설정 화면으로 이동
     */
    private fun openAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("HomeActivity", "알람 설정 화면 열기 실패: ${e.message}")
                Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 권한 거부 시 안내
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("알람 앱이 정상적으로 작동하려면 알림 권한이 필요합니다.")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                checkAlarmPermission() // 알람 권한은 계속 체크
            }
            .show()
    }

    /**
     * 매일 알람 설정
     */
    private fun setupDailyAlarm() {
        val surveyCompleted = sharedPreferences.getBoolean("survey_completed", false)

        // ⭐ 저장된 값 확인
        val avgBedtime = sharedPreferences.getString("avg_bedtime", "없음")
        val targetWakeTime = sharedPreferences.getString("target_wake_time", "없음")
        val minSleepMinutes = sharedPreferences.getInt("min_sleep_minutes", -1)

        android.util.Log.d("HomeActivity", """
            === HomeActivity에서 읽은 값 ===
            survey_completed: $surveyCompleted
            avg_bedtime: $avgBedtime
            target_wake_time: $targetWakeTime
            min_sleep_minutes: $minSleepMinutes
        """.trimIndent())

        if (surveyCompleted) {
            val currentDay = getCurrentDay()
            alarmManager.updateDailyAlarm(currentDay)
        }
    }

    /**
     * 코인 초기화 (최초 실행 시 30개)
     */
    private fun initializePawCoins() {
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)

        if (isFirstRun) {
            with(sharedPreferences.edit()) {
                putInt("paw_coin_count", 30)
                putBoolean("is_first_run", false)
                apply()
            }
            android.util.Log.d("HomeActivity", "초기 코인 30개 설정됨")
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
        // ⭐ 오늘의 취침 시간 표시
        val todayBedtime = sharedPreferences.getString("today_bedtime", null)

        if (todayBedtime != null) {
            binding.tvBedtime.text = todayBedtime
        } else {
            // 설문 미완료 시 기본 취침 시간
            val bedtime = getCurrentBedtime()
            binding.tvBedtime.text = bedtime
        }
    }

    private fun getCurrentBedtime(): String {
        // avg_bedtime 사용
        val avgBedtime = sharedPreferences.getString("avg_bedtime", "23:00") ?: "23:00"
        return avgBedtime
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
        val panda = binding.imgPanda  // bedtimeFloatingBubble → imgPanda로 변경

        val moveUp = ObjectAnimator.ofFloat(panda, "translationY", 0f, -50f)
        moveUp.duration = 2000
        moveUp.interpolator = AccelerateDecelerateInterpolator()

        val moveDown = ObjectAnimator.ofFloat(panda, "translationY", -50f, 0f)
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