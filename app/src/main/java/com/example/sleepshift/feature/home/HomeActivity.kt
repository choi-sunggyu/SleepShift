package com.example.sleepshift.feature.home

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityHomeBinding
import com.example.sleepshift.feature.NightRoutineActivity
import com.example.sleepshift.feature.ReportActivity
import com.example.sleepshift.feature.SettingsActivity
import com.example.sleepshift.permission.PermissionManager
import com.example.sleepshift.util.Constants
import com.example.sleepshift.util.DailyAlarmManager

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var alarmManager: DailyAlarmManager
    private lateinit var permissionManager: PermissionManager
    private var floatingAnimator: ObjectAnimator? = null
    private val progressDots = mutableListOf<android.view.View>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionManager.checkAlarmPermission()
        } else {
            permissionManager.showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeManagers()
        setupUI()
        observeViewModel()

        requestIgnoreBatteryOptimization()
        permissionManager.requestAllPermissions(notificationPermissionLauncher)

        viewModel.checkDailyProgress()
    }

    private fun initializeManagers() {
        alarmManager = DailyAlarmManager(this)
        permissionManager = PermissionManager(this) {
            setupDailyAlarmIfNeeded()
        }
    }

    private fun setupUI() {
        setupProgressDots()
        setupClickListeners()
        startFloatingAnimation()
    }

    private fun observeViewModel() {
        viewModel.currentDay.observe(this) { day ->
            binding.tvDayCount.text = "Day $day"
        }

        viewModel.bedtime.observe(this) { bedtime ->
            binding.tvBedtime.text = bedtime
        }

        viewModel.coinCount.observe(this) { count ->
            binding.tvPawCoinCount.text = count.toString()
        }

        viewModel.currentStreak.observe(this) { streak ->
            updateProgressDots(streak)
        }

        viewModel.showStreakCompletion.observe(this) { completions ->
            showStreakCompletionDialog(completions)
        }

        viewModel.showStreakBroken.observe(this) {
            Toast.makeText(this, "연속 기록이 끊겼습니다. 다시 도전하세요!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDailyAlarmIfNeeded() {
        if (!viewModel.isSurveyCompleted()) return

        if (viewModel.shouldSetupAlarm()) {
            val currentDay = viewModel.currentDay.value ?: 1
            alarmManager.updateDailyAlarm(currentDay)
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnGoToBed.setOnClickListener {
            viewModel.recordBedtime()
            startActivity(Intent(this, NightRoutineActivity::class.java))
            animateButton(binding.btnGoToBed)
        }

        binding.btnCalendar.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        binding.imgPawCoin.setOnClickListener {
            showPawCoinInfo()
        }
    }

    private fun updateProgressDots(streak: Int) {
        progressDots.forEach { dot ->
            dot.setBackgroundResource(R.drawable.progress_dot_inactive)
        }

        val activeDots = minOf(streak, Constants.STREAK_COMPLETION_DAYS)
        for (i in 0 until activeDots) {
            progressDots[i].setBackgroundResource(R.drawable.progress_dot_active)
        }
    }

    private fun startFloatingAnimation() {
        floatingAnimator?.cancel()

        floatingAnimator = ObjectAnimator.ofFloat(
            binding.imgPanda,
            "translationY",
            0f,
            Constants.FLOATING_TRANSLATION_Y
        ).apply {
            duration = Constants.FLOATING_ANIMATION_DURATION
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateButton(view: android.view.View) {
        view.animate()
            .scaleX(Constants.CLICK_SCALE)
            .scaleY(Constants.CLICK_SCALE)
            .setDuration(Constants.CLICK_ANIMATION_DURATION)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(Constants.CLICK_ANIMATION_DURATION)
                    .start()
            }
            .start()
    }

    private fun showPawCoinInfo() {
        Toast.makeText(
            this,
            "발바닥 코인: 잠금화면 해제에 사용할 수 있는 토큰입니다!",
            Toast.LENGTH_LONG
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

    private fun showStreakCompletionDialog(totalCompletions: Int) {
        AlertDialog.Builder(this)
            .setTitle("${Constants.STREAK_COMPLETION_DAYS}일 연속 달성!")
            .setMessage(
                "축하합니다!\n" +
                        "${Constants.STREAK_COMPLETION_DAYS}일 연속으로 규칙적인 수면을 유지했습니다.\n\n" +
                        "총 ${totalCompletions}회 달성"
            )
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                viewModel.updateAllData()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("HomeActivity", "배터리 최적화 해제 실패: ${e.message}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateAllData()
        startFloatingAnimation()
    }

    override fun onPause() {
        super.onPause()
        floatingAnimator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingAnimator?.cancel()
        floatingAnimator = null
    }
}