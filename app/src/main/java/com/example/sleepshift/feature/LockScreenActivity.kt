package com.example.sleepshift.feature

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.sleepshift.util.DeviceAdminHelper
import java.text.SimpleDateFormat
import java.util.*

class LockScreenActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var deviceAdminHelper: DeviceAdminHelper

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
    private var returnRunnable: Runnable? = null
    private var pauseCount = 0

    // 코인 사용 관련
    private val UNLOCK_COST = 15

    // 배경 이미지 변경용
    private var backgroundImage: ImageView? = null
    private val earlyWakeCheckHandler = Handler(Looper.getMainLooper())
    private var earlyWakeCheckRunnable: Runnable? = null
    private var isEarlyWakeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 방향 설정 (Android 16 미만)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setupLockScreenMode()
        setContentView(R.layout.activity_lock_screen)

        // 화면을 항상 켜진 상태로 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // ⭐⭐⭐ Device Admin Helper 초기화
        deviceAdminHelper = DeviceAdminHelper(this)

        // ⭐⭐⭐ 정상 이동 플래그 확인 및 해제
        val isNormalTransition = sharedPreferences.getBoolean("is_going_to_lockscreen", false)
        if (isNormalTransition) {
            sharedPreferences.edit {
                putBoolean("is_going_to_lockscreen", false)
            }
            Log.d("LockScreen", "✅ 정상 이동 확인 - 플래그 해제")
        }

        initViews()
        setupUI()
        setupUnlockButton()

        enableImmersiveMode()

        // ⭐⭐⭐ Device Admin 권한 확인
        checkDeviceAdminPermission()

        startLockMode()
        recordSleepCheckInAndScheduleNextAlarm()

        // 배경 이미지 안전하게 초기화
        initBackgroundImage()

        // 조기 기상 체크는 일단 비활성화 (안정성 우선)
        // startEarlyWakeBackgroundCheck()

        Log.d("LockScreen", "✅ LockScreenActivity 초기화 완료")
    }

    /**
     * ⭐⭐⭐ Device Admin 권한 확인 (최초 1회만)
     */
    private fun checkDeviceAdminPermission() {
        val hasAsked = sharedPreferences.getBoolean("has_asked_device_admin", false)

        if (!hasAsked && !deviceAdminHelper.isAdminActive()) {
            // 다이얼로그 표시
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🔒 강력한 잠금 기능")
                .setMessage(
                    "홈 버튼까지 완전히 차단하려면\n기기 관리자 권한이 필요합니다.\n\n" +
                            "✅ 수면 시간에만 사용됩니다\n" +
                            "✅ 언제든지 해제 가능합니다\n" +
                            "✅ 개인정보에 접근하지 않습니다\n\n" +
                            "지금 활성화하시겠습니까?"
                )
                .setPositiveButton("활성화") { _, _ ->
                    deviceAdminHelper.requestAdminPermission(this)
                    sharedPreferences.edit {
                        putBoolean("has_asked_device_admin", true)
                    }
                }
                .setNegativeButton("나중에") { _, _ ->
                    sharedPreferences.edit {
                        putBoolean("has_asked_device_admin", true)
                    }
                    Toast.makeText(this, "Screen Pinning만 사용됩니다", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * ⭐⭐⭐ Device Admin 권한 요청 결과 처리
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == DeviceAdminHelper.REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d("LockScreen", "✅ Device Admin 권한 승인됨")
                Toast.makeText(this, "✅ 강력한 잠금이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("LockScreen", "⚠️ Device Admin 권한 거부됨")
                Toast.makeText(this, "Screen Pinning만 사용됩니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 배경 이미지 안전하게 초기화
     */
    private fun initBackgroundImage() {
        try {
            backgroundImage = findViewById(R.id.lockScreenBackground)
            if (backgroundImage == null) {
                Log.w("LockScreen", "⚠️ lockScreenBackground를 찾을 수 없습니다 (선택사항)")
            } else {
                Log.d("LockScreen", "✅ 배경 이미지 초기화 완료")
            }
        } catch (e: Exception) {
            Log.e("LockScreen", "❌ backgroundImage 초기화 실패 (계속 진행)", e)
        }
    }

    /**
     * 잠금 화면 모드 설정
     */
    private fun setupLockScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
     * Immersive Sticky Mode
     */
    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(android.view.WindowInsets.Type.systemBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
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
     * ⭐⭐⭐ 잠금 모드 시작 (Screen Pinning 추가)
     */
    private fun startLockMode() {
        // lock_screen_active 플래그 설정
        sharedPreferences.edit {
            putBoolean("lock_screen_active", true)
        }

        // ⭐⭐⭐ Screen Pinning 시작 (홈 버튼 1차 차단)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask()
                Log.d("LockScreen", "✅ Screen Pinning 시작됨")
            } catch (e: Exception) {
                Log.e("LockScreen", "❌ Screen Pinning 실패", e)
                Toast.makeText(this, "Screen Pinning 실패 - 일반 모드 사용", Toast.LENGTH_SHORT).show()
            }
        }

        // Foreground Service 시작
        startLockMonitoringService()

        Log.d("LockScreen", "✅ 잠금 모드 시작 (Screen Pinning + Service)")
    }

    /**
     * 잠금 감시 서비스 시작
     */
    private fun startLockMonitoringService() {
        val intent = Intent(this, LockMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d("LockScreen", "Lock Monitoring Service 시작됨")
    }

    /**
     * 수면 체크인 기록 및 다음날 알람 설정
     */
    private fun recordSleepCheckInAndScheduleNextAlarm() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        sharedPreferences.edit {
            putString("last_sleep_checkin_date", today)
            putLong("last_sleep_checkin_time", System.currentTimeMillis())
        }

        Log.d("LockScreen", "수면 체크인 기록됨: $today")
        scheduleNextDayAlarm()
    }

    /**
     * 다음날 알람 설정
     */
    private fun scheduleNextDayAlarm() {
        val alarmManager = DailyAlarmManager(this)
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val isOneTimeAlarm = sharedPreferences.getBoolean("is_one_time_alarm", false)

        if (isOneTimeAlarm) {
            sharedPreferences.edit {
                putInt("current_day", currentDay + 1)
            }
            Log.d("LockScreen", "일회성 알람 유지, Day만 증가: $currentDay → ${currentDay + 1}")
        } else {
            val nextDay = currentDay + 1
            alarmManager.updateDailyAlarm(nextDay)
            Log.d("LockScreen", "다음날(Day $nextDay) 알람 설정 완료")
        }
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
        updateAlarmTimeDisplay()
        checkUnlockAvailability()
    }

    /**
     * 알람 시간 표시 업데이트
     */
    private fun updateAlarmTimeDisplay() {
        try {
            val alarmTime = sharedPreferences.getString("today_alarm_time", null)
                ?: sharedPreferences.getString("target_wake_time", "07:00")
                ?: "07:00"

            val wakeTimeText = findViewById<TextView>(R.id.tvWakeTimeMessage)
            wakeTimeText?.text = "${alarmTime}에 깨워드릴게요"

            Log.d("LockScreen", "알람 시간 표시: $alarmTime")
        } catch (e: Exception) {
            Log.e("LockScreen", "알람 시간 표시 실패", e)
        }
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

        sharedPreferences.edit {
            putBoolean("is_unlocking", true)
        }

        stopLockMode()

        Toast.makeText(this, "잠금이 해제되었습니다! (코인 ${UNLOCK_COST}개 사용)", Toast.LENGTH_LONG).show()
        setScreenBrightness(-1f)
        recordUnlockTime()

        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        sharedPreferences.edit {
            putBoolean("is_unlocking", false)
        }

        finish()
    }

    /**
     * ⭐⭐⭐ 잠금 모드 해제 (Screen Pinning 해제)
     */
    private fun stopLockMode() {
        // lock_screen_active 플래그 해제
        sharedPreferences.edit {
            putBoolean("lock_screen_active", false)
        }

        // ⭐⭐⭐ Screen Pinning 해제
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                stopLockTask()
                Log.d("LockScreen", "✅ Screen Pinning 해제됨")
            } catch (e: Exception) {
                Log.e("LockScreen", "❌ Screen Pinning 해제 실패", e)
            }
        }

        // Foreground Service 중지
        stopService(Intent(this, LockMonitoringService::class.java))
        Log.d("LockScreen", "✅ 잠금 모드 해제")
    }

    private fun usePawCoins(amount: Int): Boolean {
        val currentCoins = getCurrentCoins()

        if (currentCoins >= amount) {
            val newCoins = currentCoins - amount

            sharedPreferences.edit()
                .putInt("paw_coin_count", newCoins)
                .apply()

            Log.d("LockScreen", "코인 사용: ${amount}개, 잔액: ${newCoins}개")
            return true
        }

        Log.d("LockScreen", "코인 부족: 현재 ${currentCoins}개, 필요 ${amount}개")
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

    /**
     * 뒤로가기 완전 차단
     */
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "잠금 해제 버튼을 사용해주세요", Toast.LENGTH_SHORT).show()
        Log.d("LockScreen", "뒤로가기 차단됨")
    }

    /**
     * 홈 버튼 감지 (즉시 복귀)
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val isUnlocking = sharedPreferences.getBoolean("is_unlocking", false)
        val isAlarmRinging = sharedPreferences.getBoolean("is_alarm_ringing", false)
        val isGoingToLockscreen = sharedPreferences.getBoolean("is_going_to_lockscreen", false)

        if (!isUnlocking && !isAlarmRinging && !isGoingToLockscreen) {
            Log.d("LockScreen", "홈 버튼 감지 - 즉시 복귀 시도")

            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
        } else {
            Log.d("LockScreen", "홈 버튼 무시 (잠금해제 또는 알람 또는 정상이동)")
        }
    }

    /**
     * 비정상 종료 감지
     */
    override fun onPause() {
        super.onPause()

        pauseCount++
        Log.d("LockScreen", "onPause 호출 #$pauseCount")

        val isUnlocking = sharedPreferences.getBoolean("is_unlocking", false)
        val isAlarmRinging = sharedPreferences.getBoolean("is_alarm_ringing", false)
        val isGoingToLockscreen = sharedPreferences.getBoolean("is_going_to_lockscreen", false)

        if (!isUnlocking && !isAlarmRinging && !isGoingToLockscreen) {
            returnRunnable?.let {
                handler.removeCallbacks(it)
                Log.d("LockScreen", "기존 복귀 Runnable 취소됨")
            }

            Log.d("LockScreen", "비정상 종료 감지 - 복귀 대기 (300ms)")

            returnRunnable = Runnable {
                val stillNotAlarm = !sharedPreferences.getBoolean("is_alarm_ringing", false)
                val stillLocked = sharedPreferences.getBoolean("lock_screen_active", false)
                val stillNotUnlocking = !sharedPreferences.getBoolean("is_unlocking", false)
                val stillNotGoingToLockscreen = !sharedPreferences.getBoolean("is_going_to_lockscreen", false)

                if (stillLocked && stillNotAlarm && stillNotUnlocking && stillNotGoingToLockscreen) {
                    Log.d("LockScreen", "복귀 조건 충족 - 실행")
                    val intent = Intent(this, LockScreenActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(intent)
                } else {
                    Log.d("LockScreen", "복귀 취소 (잠금:$stillLocked, 알람:$stillNotAlarm, 해제:$stillNotUnlocking, 정상이동:$stillNotGoingToLockscreen)")
                }
                returnRunnable = null
            }

            handler.postDelayed(returnRunnable!!, 300)

        } else {
            if (isAlarmRinging) {
                Log.d("LockScreen", "알람이 울리는 중 - 복귀하지 않음")
            }
            if (isUnlocking) {
                Log.d("LockScreen", "잠금 해제 중 - 복귀하지 않음")
            }
            if (isGoingToLockscreen) {
                Log.d("LockScreen", "정상 이동 중 - 복귀하지 않음")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCoinDisplay()
        updateAlarmTimeDisplay()
        checkUnlockAvailability()
        enableImmersiveMode()
        Log.d("LockScreen", "onResume - 데이터 업데이트 완료")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEarlyWakeCheck()

        returnRunnable?.let { handler.removeCallbacks(it) }
        returnRunnable = null

        countDownTimer?.cancel()
        countDownTimer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("LockScreen", "LockScreenActivity 종료")
    }

    // 조기 기상 관련 메서드들 (현재 비활성화)
    private fun startEarlyWakeBackgroundCheck() {
        earlyWakeCheckRunnable = object : Runnable {
            override fun run() {
                if (!isEarlyWakeMode) {
                    checkIfOneHourBeforeAlarm()
                }
                earlyWakeCheckHandler.postDelayed(this, 60000)
            }
        }
        earlyWakeCheckRunnable?.let { earlyWakeCheckHandler.post(it) }
        Log.d("LockScreen", "조기 기상 체크 시작")
    }

    private fun checkIfOneHourBeforeAlarm() {
        try {
            val alarmTime = sharedPreferences.getString("today_alarm_time", null)
                ?: sharedPreferences.getString("target_wake_time", "07:00")
                ?: "07:00"

            val timeParts = alarmTime.split(":")
            val alarmHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
            val alarmMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

            val alarmCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarmHour)
                set(Calendar.MINUTE, alarmMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val oneHourBeforeAlarm = Calendar.getInstance().apply {
                timeInMillis = alarmCalendar.timeInMillis
                add(Calendar.HOUR_OF_DAY, -1)
            }

            val currentTime = System.currentTimeMillis()

            if (currentTime >= oneHourBeforeAlarm.timeInMillis &&
                currentTime < alarmCalendar.timeInMillis) {
                Log.d("LockScreen", "✅ 알람 1시간 전 도달! 배경 이미지 변경")
                changeToEarlyWakeBackground()
            }
        } catch (e: Exception) {
            Log.e("LockScreen", "❌ 조기 기상 체크 실패", e)
        }
    }

    private fun changeToEarlyWakeBackground() {
        try {
            isEarlyWakeMode = true

            backgroundImage?.let { bgImage ->
                try {
                    @Suppress("DEPRECATION")
                    resources.getDrawable(R.drawable.lock_screen_morning, null)

                    bgImage.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            bgImage.setImageResource(R.drawable.lock_screen_morning)
                            bgImage.animate()
                                .alpha(1f)
                                .setDuration(500)
                                .start()
                        }
                        .start()

                    Log.d("LockScreen", "배경 이미지 변경 완료")
                } catch (e: Exception) {
                    Log.w("LockScreen", "lock_screen_morning 이미지 없음 (무시)", e)
                }
            } ?: run {
                Log.d("LockScreen", "backgroundImage가 null (배경 변경 건너뜀)")
            }

            tvGoodNightMessage.text = "곧 일어날 시간이에요! 🌅"

            sharedPreferences.edit {
                putBoolean("early_wake_background_shown", true)
                putLong("early_wake_background_time", System.currentTimeMillis())
            }

            Log.d("LockScreen", "조기 기상 모드 활성화 완료")
        } catch (e: Exception) {
            Log.e("LockScreen", "❌ 배경 변경 실패", e)
        }
    }

    private fun stopEarlyWakeCheck() {
        earlyWakeCheckRunnable?.let {
            earlyWakeCheckHandler.removeCallbacks(it)
        }
        earlyWakeCheckRunnable = null
    }
}