package com.example.sleepshift.feature

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.sleepshift.R
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.service.LockMonitoringService
import com.example.sleepshift.util.DailyAlarmManager
import java.text.SimpleDateFormat
import java.util.*

class LockScreenActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // Views
    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvCoinCount: TextView
    private lateinit var btnUnlock: LinearLayout
    private lateinit var imgLockIcon: ImageView
    private lateinit var tvUnlockText: TextView
    private lateinit var tvUnlockHint: TextView
    private lateinit var countdownSection: LinearLayout
    private lateinit var tvCountdown: TextView

    // 카운트다운 관련
    private var countDownTimer: CountDownTimer? = null
    private var isLongPressing = false
    private val handler = Handler(Looper.getMainLooper())

    // 코인 사용 관련
    private val UNLOCK_COST = 15 // 잠금 해제에 필요한 코인

    // ⭐ 배경 이미지 변경용
    private lateinit var backgroundImage: ImageView  // 또는 배경으로 사용 중인 View
    private val earlyWakeCheckHandler = Handler(Looper.getMainLooper())
    private var earlyWakeCheckRunnable: Runnable? = null
    private var isEarlyWakeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ⭐ 코드에서 화면 방향 설정 (Android 16 미만에서만 적용)
        if (Build.VERSION.SDK_INT < 35) { // UPSIDE_DOWN_CAKE = 35
            @Suppress("DEPRECATION")
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // ⭐ 잠금 화면 설정 (OS 레벨)
        setupLockScreenMode()

        setContentView(R.layout.activity_lock_screen)

        // 화면을 항상 켜진 상태로 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        initViews()
        setupUI()
        setupUnlockButton()

        // ⭐ Immersive Mode 설정
        enableImmersiveMode()

        // ⭐ 잠금 모드 시작
        startLockMode()

        // ⭐ 수면 체크인 기록 및 다음날 알람 설정
        recordSleepCheckInAndScheduleNextAlarm()

        // ⭐ 배경 이미지 뷰 초기화
        backgroundImage = findViewById(R.id.lockScreenBackground)  // layout의 실제 ID로 변경

        // ⭐ 조기 기상 체크 시작
        startEarlyWakeBackgroundCheck()
    }

    /**
     * ⭐ 잠금 화면 모드 설정
     */
    private fun setupLockScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Android 8.1 이상: 잠금 화면 위에 표시
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    /**
     * ⭐ Immersive Sticky Mode - 네비게이션 바 숨김
     */
    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 이상
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(android.view.WindowInsets.Type.systemBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 이하
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    /**
     * ⭐ 잠금 모드 시작 (Screen Pinning 제거)
     */
    private fun startLockMode() {
        // lock_screen_active 플래그 설정
        sharedPreferences.edit {
            putBoolean("lock_screen_active", true)
        }

        // ❌ Screen Pinning 완전 제거 (알림 반복 문제 해결)
        // startLockTask() 호출하지 않음

        // ✅ Foreground Service + Accessibility만 사용
        startLockMonitoringService()

        android.util.Log.d("LockScreen", "✅ 잠금 모드 시작 (Accessibility + Service)")
    }


    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true // Android 5.1 미만은 필요 없음
        }

        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /**
     * ⭐ UsageStats 권한 요청 다이얼로그
     */
    private fun showUsageStatsPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("사용 정보 접근 권한 필요")
            .setMessage("잠금 화면을 유지하려면\n사용 정보 접근 권한이 필요합니다.\n\n설정 → 특수 앱 접근 → 사용 정보 접근\n에서 SleepShift를 활성화해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    /**
     * ⭐ Accessibility 서비스 활성화 확인
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/.service.AccessibilityLockService"
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }

    /**
     * ⭐ Accessibility 권한 요청 다이얼로그
     */
    private fun showAccessibilityPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("접근성 권한 필요")
            .setMessage("더 강력한 잠금을 위해\n접근성 서비스 권한을 활성화해주세요.\n\n설정 → 접근성 → 설치된 서비스\n에서 SleepShift를 활성화해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("건너뛰기", null)
            .show()
    }

    /**
     * ⭐ 잠금 감시 서비스 시작
     */
    private fun startLockMonitoringService() {
        val intent = Intent(this, LockMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        android.util.Log.d("LockScreen", "Lock Monitoring Service 시작됨")
    }

    /**
     * ⭐ 수면 체크인 기록 및 다음날 알람 설정
     */
    private fun recordSleepCheckInAndScheduleNextAlarm() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        sharedPreferences.edit {
            putString("last_sleep_checkin_date", today)
            putLong("last_sleep_checkin_time", System.currentTimeMillis())
        }

        android.util.Log.d("LockScreen", "수면 체크인 기록됨: $today")

        // ⭐ 다음날 알람 설정
        scheduleNextDayAlarm()
    }

    /**
     * ⭐ 다음날 알람 설정 (AlarmActivity와 동일한 방식)
     */
    private fun scheduleNextDayAlarm() {
        val alarmManager = DailyAlarmManager(this)
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val nextDay = currentDay + 1

        alarmManager.updateDailyAlarm(nextDay)

        android.util.Log.d("LockScreen", "다음날(Day $nextDay) 알람 설정 완료")
    }

    private fun initViews() {
        tvGoodNightMessage = findViewById(R.id.tvGoodNightMessage)
        tvCoinCount = findViewById(R.id.tvCoinCount)
        btnUnlock = findViewById(R.id.btnUnlock)
        imgLockIcon = findViewById(R.id.imgLockIcon)
        tvUnlockText = findViewById(R.id.tvUnlockText)
        tvUnlockHint = findViewById(R.id.tvUnlockHint)
        countdownSection = findViewById(R.id.countdownSection)
        tvCountdown = findViewById(R.id.tvCountdown)
    }

    private fun setupUI() {
        val userName = sharedPreferences.getString("user_name", "사용자")
        tvGoodNightMessage.text = "${userName}님 잘자요!"

        updateCoinDisplay()

        val alarmTime = sharedPreferences.getString("today_alarm_time", null)
            ?: sharedPreferences.getString("target_wake_time", "07:00")
            ?: "07:00"

        val wakeTimeText = findViewById<TextView>(R.id.tvWakeTimeMessage)
        wakeTimeText?.text = "${alarmTime}에 깨워드릴게요"

        checkUnlockAvailability()
    }

    private fun updateCoinDisplay() {
        val coinCount = getCurrentCoins()
        tvCoinCount.text = coinCount.toString()
    }

    private fun getCurrentCoins(): Int {
        return sharedPreferences.getInt("paw_coin_count", 0)
    }

    private fun checkUnlockAvailability() {
        val currentCoins = getCurrentCoins()

        if (currentCoins < UNLOCK_COST) {
            btnUnlock.alpha = 0.5f
            tvUnlockHint.text = "곰젤리 (${UNLOCK_COST}개 필요)"
        } else {
            btnUnlock.alpha = 1.0f
            tvUnlockHint.text = "해제를 원하시면 3초간 누르세요"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUnlockButton() {
        btnUnlock.setOnTouchListener { _, event ->
            if (getCurrentCoins() < UNLOCK_COST) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showInsufficientCoinsMessage()
                }
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startLongPressCountdown()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPressCountdown()
                    true
                }
                else -> false
            }
        }
    }

    private fun showInsufficientCoinsMessage() {
        val currentCoins = getCurrentCoins()
        val needed = UNLOCK_COST - currentCoins
        Toast.makeText(
            this,
            "발바닥 코인이 부족합니다!\n현재: ${currentCoins}개, 필요: ${UNLOCK_COST}개\n(${needed}개 더 필요)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startLongPressCountdown() {
        if (isLongPressing) return

        isLongPressing = true
        countdownSection.visibility = View.VISIBLE
        tvUnlockHint.visibility = View.GONE

        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000 + 1).toInt()
                tvCountdown.text = secondsRemaining.toString()
                updateLockIcon(secondsRemaining)

                btnUnlock.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(200)
                    .start()
            }

            override fun onFinish() {
                unlockScreen()
            }
        }.start()
    }

    private fun cancelLongPressCountdown() {
        if (!isLongPressing) return

        isLongPressing = false
        countDownTimer?.cancel()
        countDownTimer = null

        countdownSection.visibility = View.GONE
        tvUnlockHint.visibility = View.VISIBLE
        updateLockIcon(0)

        btnUnlock.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .start()
    }

    private fun updateLockIcon(countdown: Int) {
        when (countdown) {
            3 -> {
                tvUnlockText.text = "3"
                tvUnlockText.textSize = 24f
            }
            2 -> {
                tvUnlockText.text = "2"
                tvUnlockText.textSize = 24f
            }
            1 -> {
                tvUnlockText.text = "1"
                tvUnlockText.textSize = 24f
            }
            else -> {
                tvUnlockText.text = "잠금해제"
                tvUnlockText.textSize = 18f
            }
        }
    }

    private fun unlockScreen() {
        val success = usePawCoins(UNLOCK_COST)

        if (!success) {
            Toast.makeText(this, "코인이 부족하여 잠금 해제를 할 수 없습니다", Toast.LENGTH_LONG).show()
            cancelLongPressCountdown()
            checkUnlockAvailability()
            return
        }

        // ⭐ 잠금 모드 해제
        stopLockMode()

        Toast.makeText(this, "잠금이 해제되었습니다! (코인 ${UNLOCK_COST}개 사용)", Toast.LENGTH_LONG).show()
        setScreenBrightness(-1f)
        recordUnlockTime()

        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    /**
     * ⭐ 잠금 모드 해제
     */
    private fun stopLockMode() {
        // lock_screen_active 플래그 해제
        sharedPreferences.edit {
            putBoolean("lock_screen_active", false)
        }

        // ❌ stopLockTask() 호출하지 않음 (어차피 startLockTask 안 했으므로)

        // Foreground Service 중지
        stopService(Intent(this, LockMonitoringService::class.java))
        android.util.Log.d("LockScreen", "✅ 잠금 모드 해제")
    }

    private fun usePawCoins(amount: Int): Boolean {
        val currentCoins = getCurrentCoins()

        if (currentCoins >= amount) {
            val newCoins = currentCoins - amount

            sharedPreferences.edit()
                .putInt("paw_coin_count", newCoins)
                .apply()

            android.util.Log.d("LockScreen", "코인 사용: ${amount}개, 잔액: ${newCoins}개")
            return true
        }

        android.util.Log.d("LockScreen", "코인 부족: 현재 ${currentCoins}개, 필요 ${amount}개")
        return false
    }

    private fun recordUnlockTime() {
        sharedPreferences.edit()
            .putLong("last_unlock_time", System.currentTimeMillis())
            .putInt("daily_unlock_count",
                sharedPreferences.getInt("daily_unlock_count", 0) + 1)
            .apply()
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        // 뒤로가기 완전 차단
        Toast.makeText(this, "잠금 해제 버튼을 사용해주세요", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        updateCoinDisplay()
        checkUnlockAvailability()
        enableImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    /**
     * ⭐ 조기 기상 배경 체크 시작 - 1분마다 알람 1시간 전인지 확인
     */
    private fun startEarlyWakeBackgroundCheck() {
        earlyWakeCheckRunnable = object : Runnable {
            override fun run() {
                if (!isEarlyWakeMode) {
                    checkIfOneHourBeforeAlarm()
                }
                // 1분마다 체크
                earlyWakeCheckHandler.postDelayed(this, 60000)
            }
        }
        // 즉시 한 번 체크하고 시작
        earlyWakeCheckRunnable?.let { earlyWakeCheckHandler.post(it) }
    }

    /**
     * ⭐ 알람 1시간 전인지 확인하여 배경 변경
     */
    private fun checkIfOneHourBeforeAlarm() {
        // 알람 시간 가져오기
        val alarmTime = sharedPreferences.getString("today_alarm_time", null)
            ?: sharedPreferences.getString("target_wake_time", "07:00")
            ?: "07:00"

        val timeParts = alarmTime.split(":")
        val alarmHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
        val alarmMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        // 알람 시간 Calendar 설정
        val alarmCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, alarmMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 현재 시간보다 과거면 다음날로 (새벽 알람 대응)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // 알람 1시간 전 시간 계산
        val oneHourBeforeAlarm = Calendar.getInstance().apply {
            timeInMillis = alarmCalendar.timeInMillis
            add(Calendar.HOUR_OF_DAY, -1)
        }

        val currentTime = System.currentTimeMillis()

        // ⭐ 현재 시간이 알람 1시간 전 이후인지 확인
        if (currentTime >= oneHourBeforeAlarm.timeInMillis) {
            android.util.Log.d("LockScreen", "✅ 알람 1시간 전 도달! 배경 이미지 변경")
            changeToEarlyWakeBackground()
        }
    }

    /**
     * ⭐ 조기 기상 배경으로 변경
     */
    private fun changeToEarlyWakeBackground() {
        isEarlyWakeMode = true

        // 배경 이미지 변경 (원하는 이미지로 변경)
        backgroundImage.setImageResource(R.drawable.lock_screen_morning)  // 아침용 배경 이미지

        // 또는 배경색 변경
        // backgroundImage.setBackgroundColor(ContextCompat.getColor(this, R.color.morning_sky))

        // 애니메이션 효과 추가 (선택사항)
        backgroundImage.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction {
                backgroundImage.setImageResource(R.drawable.lock_screen_morning)
                backgroundImage.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()
            }
            .start()

        // 메시지 변경 (선택사항)
        tvGoodNightMessage.text = "곧 일어날 시간이에요! 🌅"

        // 조기 기상 기록
        sharedPreferences.edit {
            putBoolean("early_wake_background_shown", true)
            putLong("early_wake_background_time", System.currentTimeMillis())
        }

        android.util.Log.d("LockScreen", "배경 이미지가 아침 모드로 변경되었습니다")
    }

    /**
     * ⭐ 조기 기상 체크 중단
     */
    private fun stopEarlyWakeCheck() {
        earlyWakeCheckRunnable?.let {
            earlyWakeCheckHandler.removeCallbacks(it)
        }
        earlyWakeCheckRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // ⭐ 체크 중단
        stopEarlyWakeCheck()

        countDownTimer?.cancel()
        countDownTimer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}