package com.example.sleepshift.feature.home

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.example.sleepshift.feature.lockscreen.LockScreenActivity
import com.example.sleepshift.permission.PermissionManager
import com.example.sleepshift.util.Constants
import com.example.sleepshift.util.DailyAlarmManager
import java.util.*

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

        createAlarmNotificationChannel()
        initializeManagers()
        setupUI()
        observeViewModel()

        requestIgnoreBatteryOptimization()
        requestUsageStatsPermission()
        permissionManager.requestAllPermissions(notificationPermissionLauncher)
        viewModel.checkDailyProgress()

        // ✅ 잠금 상태 확인 후 필요시에만 LockScreenActivity 실행
        checkAndShowLockScreen()
    }

    /** 잠금 상태 확인 */
    private fun checkAndShowLockScreen() {
        val prefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        val isLocked = prefs.getBoolean("isLocked", false)

        if (isLocked) {
            // 잠금 상태일 경우 LockScreenActivity 실행
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    /** 사용 통계 권한 요청 */
    private fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )

                if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                    AlertDialog.Builder(this)
                        .setTitle("사용 통계 권한 필요")
                        .setMessage("다른 앱 차단 기능을 위해 사용 통계 권한이 필요합니다.")
                        .setPositiveButton("설정하기") { _, _ ->
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            startActivity(intent)
                        }
                        .setNegativeButton("나중에", null)
                        .show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeActivity", "사용 통계 권한 확인 실패: ${e.message}")
            }
        }
    }

    /** 알람 채널 생성 */
    private fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_channel",
                "알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "알람 알림 채널"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
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
        viewModel.showStreakCompletion.observe(this) {
            showStreakCompletionDialog(it)
        }
        viewModel.showStreakBroken.observe(this) {
            Toast.makeText(this, "연속 기록이 끊겼습니다. 다시 도전하세요!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDailyAlarmIfNeeded() {
        if (!viewModel.isSurveyCompleted()) return

        if (viewModel.shouldSetupAlarm()) {
            val currentDay = viewModel.currentDay.value ?: 1
            val success = alarmManager.updateDailyAlarm(currentDay)
            if (success) {
                android.util.Log.d("HomeActivity", "✅ 알람 설정 성공")
            } else {
                android.util.Log.e("HomeActivity", "❌ 알람 설정 실패 - 권한 확인 필요")
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnGoToBed.setOnClickListener {
            viewModel.recordBedtime()
            animateButton(binding.btnGoToBed)

            // ✅ 나이트 루틴 화면으로 이동
            val intent = Intent(this, NightRoutineActivity::class.java)
            startActivity(intent)
        }

        binding.btnCalendar.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        binding.imgPawCoin.setOnClickListener {
            showPawCoinInfo()
        }
    }

    private fun updateProgressDots(streak: Int) {
        progressDots.forEach { it.setBackgroundResource(R.drawable.progress_dot_inactive) }
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
            }.start()
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
            }.start()
    }

    private fun showStreakCompletionDialog(totalCompletions: Int) {
        AlertDialog.Builder(this)
            .setTitle("${Constants.STREAK_COMPLETION_DAYS}일 연속 달성!")
            .setMessage("축하합니다!\n${Constants.STREAK_COMPLETION_DAYS}일 연속으로 규칙적인 수면을 유지했습니다.\n\n총 ${totalCompletions}회 달성")
            .setPositiveButton("확인") { d, _ -> d.dismiss(); viewModel.updateAllData() }
            .setCancelable(false)
            .show()
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
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

        // ✅ 잠금 상태 재확인
        checkAndShowLockScreen()
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