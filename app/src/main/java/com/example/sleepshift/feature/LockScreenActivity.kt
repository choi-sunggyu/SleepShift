package com.example.sleepshift.feature

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
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
    private var returnRunnable: Runnable? = null  // ⭐ Handler 중복 방지용
    private var pauseCount = 0  // ⭐ onPause 호출 횟수 추적

    // 코인 사용 관련
    private val UNLOCK_COST = 15

    // ⭐ 배경 이미지 변경용 (nullable로 변경)
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

        initViews()
        setupUI()
        setupUnlockButton()

        enableImmersiveMode()
        startLockMode()
        recordSleepCheckInAndScheduleNextAlarm()

        // ⭐ 배경 이미지 안전하게 초기화
        initBackgroundImage()

        // ⭐⭐⭐ 조기 기상 체크는 일단 비활성화 (안정성 우선)
        // startEarlyWakeBackgroundCheck()

        Log.d("LockScreen", "✅ LockScreenActivity 초기화 완료")
    }

    /**
     * ⭐ 배경 이미지 안전하게 초기화
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
     * 잠금 모드 시작
     */
    private fun startLockMode() {
        sharedPreferences.edit {
            putBoolean("lock_screen_active", true)
        }

        startLockMonitoringService()
        Log.d("LockScreen", "✅ 잠금 모드 시작")
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
     * ⭐ 알람 시간 표시 업데이트 (새로 추가)
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
     * 잠금 모드 해제
     */
    private fun stopLockMode() {
        sharedPreferences.edit {
            putBoolean("lock_screen_active", false)
        }

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
     * ⭐ 뒤로가기 완전 차단 (super 호출 제거)
     */
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 의도적으로 super를 호출하지 않아 뒤로가기를 완전히 차단
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

        if (!isUnlocking && !isAlarmRinging) {
            Log.d("LockScreen", "홈 버튼 감지 - 즉시 복귀 시도")

            // ⭐ 즉시 복귀 (딜레이 없음)
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
        } else {
            Log.d("LockScreen", "홈 버튼 무시 (잠금해제 중 또는 알람 울림)")
        }
    }

    /**
     * ⭐ 비정상 종료 감지 (알람 제외, Handler 중복 방지)
     */
    override fun onPause() {
        super.onPause()

        pauseCount++
        Log.d("LockScreen", "onPause 호출 #$pauseCount")

        val isUnlocking = sharedPreferences.getBoolean("is_unlocking", false)
        val isAlarmRinging = sharedPreferences.getBoolean("is_alarm_ringing", false)

        // ⭐ 알람이 울리는 중이면 복귀하지 않음
        if (!isUnlocking && !isAlarmRinging) {
            // ⭐ 기존 Runnable 취소
            returnRunnable?.let {
                handler.removeCallbacks(it)
                Log.d("LockScreen", "기존 복귀 Runnable 취소됨")
            }

            Log.d("LockScreen", "비정상 종료 감지 - 복귀 대기 (300ms)")

            // ⭐ 새 Runnable 생성
            returnRunnable = Runnable {
                // ⭐ 재확인: 알람이 울리기 시작했는지
                val stillNotAlarm = !sharedPreferences.getBoolean("is_alarm_ringing", false)
                val stillLocked = sharedPreferences.getBoolean("lock_screen_active", false)
                val stillNotUnlocking = !sharedPreferences.getBoolean("is_unlocking", false)

                if (stillLocked && stillNotAlarm && stillNotUnlocking) {
                    Log.d("LockScreen", "복귀 조건 충족 - 실행")
                    val intent = Intent(this, LockScreenActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(intent)
                } else {
                    Log.d("LockScreen", "복귀 취소 (잠금:$stillLocked, 알람:$stillNotAlarm, 해제:$stillNotUnlocking)")
                }
                returnRunnable = null
            }

            // ⭐ 300ms 후 실행 (더 빠르게)
            handler.postDelayed(returnRunnable!!, 300)

        } else {
            if (isAlarmRinging) {
                Log.d("LockScreen", "알람이 울리는 중 - 복귀하지 않음")
            }
            if (isUnlocking) {
                Log.d("LockScreen", "잠금 해제 중 - 복귀하지 않음")
            }
        }
    }

    /**
     * ⭐ onResume에서 데이터 업데이트
     */
    override fun onResume() {
        super.onResume()
        updateCoinDisplay()
        updateAlarmTimeDisplay()  // ⭐ 알람 시간 재확인
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

    /**
     * ⭐ 조기 기상 배경 체크 시작
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
        Log.d("LockScreen", "조기 기상 체크 시작")
    }

    /**
     * ⭐ 안전한 조기 기상 체크
     */
    private fun checkIfOneHourBeforeAlarm() {
        try {
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

                // 현재 시간보다 과거면 다음날로
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

            // 현재 시간이 알람 1시간 전 ~ 알람 시간 사이인지 확인
            if (currentTime >= oneHourBeforeAlarm.timeInMillis &&
                currentTime < alarmCalendar.timeInMillis) {
                Log.d("LockScreen", "✅ 알람 1시간 전 도달! 배경 이미지 변경")
                changeToEarlyWakeBackground()
            }
        } catch (e: Exception) {
            Log.e("LockScreen", "❌ 조기 기상 체크 실패", e)
        }
    }

    /**
     * ⭐ 안전한 조기 기상 배경 변경
     */
    private fun changeToEarlyWakeBackground() {
        try {
            isEarlyWakeMode = true

            // backgroundImage가 있을 때만 변경
            backgroundImage?.let { bgImage ->
                // 리소스 존재 여부 확인
                try {
                    @Suppress("DEPRECATION")
                    resources.getDrawable(R.drawable.lock_screen_morning, null)

                    // 애니메이션 효과
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

            // 메시지 변경
            tvGoodNightMessage.text = "곧 일어날 시간이에요! 🌅"

            // 조기 기상 기록
            sharedPreferences.edit {
                putBoolean("early_wake_background_shown", true)
                putLong("early_wake_background_time", System.currentTimeMillis())
            }

            Log.d("LockScreen", "조기 기상 모드 활성화 완료")
        } catch (e: Exception) {
            Log.e("LockScreen", "❌ 배경 변경 실패", e)
        }
    }

    /**
     * 조기 기상 체크 중단
     */
    private fun stopEarlyWakeCheck() {
        earlyWakeCheckRunnable?.let {
            earlyWakeCheckHandler.removeCallbacks(it)
        }
        earlyWakeCheckRunnable = null
        Log.d("LockScreen", "조기 기상 체크 중단")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEarlyWakeCheck()

        // ⭐ 복귀 Runnable 취소
        returnRunnable?.let { handler.removeCallbacks(it) }
        returnRunnable = null

        countDownTimer?.cancel()
        countDownTimer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("LockScreen", "LockScreenActivity 종료")
    }
}