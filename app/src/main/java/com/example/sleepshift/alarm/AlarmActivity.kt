package com.example.sleepshift.feature.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityAlarmBinding
import com.example.sleepshift.feature.MorningRoutineActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.service.LockOverlayService
import com.example.sleepshift.util.DailyAlarmManager
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var consecutiveSuccessManager: ConsecutiveSuccessManager
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var countDownTimer: CountDownTimer? = null
    private var longPressHandler: Handler? = null
    private var isLongPressing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // ⭐⭐⭐ 알람 시작 플래그 설정 (가장 먼저!)
        setAlarmFlags()

        setupFullScreenAlarm()
        initializeComponents()

        // 연속 성공 매니저 초기화
        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        setupUI()
        setupLongPressListener()
        startAlarmSounds()

        Log.d("AlarmActivity", "✅ 알람 액티비티 시작 - 잠금 해제됨")
    }

    /**
     * ⭐ onResume에서 코인 동기화 (문제 5 해결)
     */
    override fun onResume() {
        super.onResume()

        // ⭐⭐⭐ 코인 업데이트
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvCoinCount.text = currentCoins.toString()

        Log.d("AlarmActivity", "onResume - 코인 업데이트: $currentCoins")
    }

    /**
     * ⭐⭐⭐ 알람 플래그 설정 (LockScreen 무한 복귀 방지)
     */
    private fun setAlarmFlags() {
        sharedPreferences.edit {
            putBoolean("is_alarm_ringing", true)      // 알람 울리는 중
            putBoolean("lock_screen_active", false)   // 잠금 해제
        }
        Log.d("AlarmActivity", "✅ 알람 플래그 설정 완료")
    }

    /**
     * ⭐⭐⭐ 알람 플래그 해제
     */
    private fun clearAlarmFlags() {
        sharedPreferences.edit {
            putBoolean("is_alarm_ringing", false)
        }
        Log.d("AlarmActivity", "✅ 알람 플래그 해제 완료")
    }

    private fun setupFullScreenAlarm() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun initializeComponents() {
        longPressHandler = Handler(Looper.getMainLooper())
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupUI() {
        val userName = sharedPreferences.getString("user_name", "사용자") ?: "사용자"
        binding.tvGoodMorningMessage.text = "${userName}님\n좋은 아침 입니다 !"

        // ⭐⭐⭐ 알람 해제 시 보상 없음 (문제 2 해결)
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvCoinCount.text = currentCoins.toString()

        binding.tvUnlockText.text = "알람해제"
        binding.tvUnlockHint.text = "해제를 원하시면 3초간 누르세요"

        // 카운트다운 텍스트 변경
        binding.countdownSection.findViewById<android.widget.TextView>(R.id.tvCountdown)?.let {
            val parent = binding.countdownSection
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is android.widget.TextView && child.text == "길게 눌러서 잠금 해제") {
                    child.text = "길게 눌러서 알람 해제"
                    break
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongPressListener() {
        binding.btnUnlock.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startLongPress()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopLongPress()
                    true
                }
                else -> false
            }
        }
    }

    private fun startLongPress() {
        if (isLongPressing) return

        isLongPressing = true
        binding.countdownSection.visibility = android.view.View.VISIBLE

        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.tvCountdown.text = secondsLeft.toString()
            }

            override fun onFinish() {
                dismissAlarm()
            }
        }.start()

        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 100, 100, 100, 100)
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopLongPress() {
        if (!isLongPressing) return

        isLongPressing = false
        countDownTimer?.cancel()
        vibrator?.cancel()

        binding.countdownSection.visibility = android.view.View.GONE
        binding.tvCountdown.text = "3"
    }

    private fun startAlarmSounds() {
        try {
            // 시스템 기본 알람음 사용
            val alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            Log.d("AlarmActivity", "시스템 알람음 재생 시작")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "알람음 재생 실패: ${e.message}")

            // 백업: 기본 알림음 사용
            try {
                val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, notificationUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                Log.e("AlarmActivity", "백업 알람음도 재생 실패: ${e2.message}")
            }
        }
    }

    private fun calculateCoinReward(): Int {
        val currentDay = getCurrentDay()
        val dayBonus = currentDay / 5
        val baseReward = 3
        return baseReward + dayBonus
    }

    private fun getCurrentDay(): Int {
        return sharedPreferences.getInt("current_day", 1)
    }

    private fun dismissAlarm() {
        stopAlarmSounds()
        vibrator?.cancel()

        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().apply {
            putBoolean("isLocked", false)
            putBoolean("is_alarm_time", false)
            apply()
        }

        // 오버레이 서비스 중지
        LockOverlayService.stop(this)

        // ⭐⭐⭐ 기상 성공 기록
        recordWakeSuccess()

        // Day 카운트 증가 및 다음 날 알람 설정
        incrementDayAndScheduleNextAlarm()

        // 알람 플래그 해제
        clearAlarmFlags()

        // 모닝 루틴으로 이동
        goToMorningRoutine()
    }

    /**
     * ⭐⭐⭐ 기상 성공 기록
     */
    private fun recordWakeSuccess() {
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            sharedPreferences.edit().apply {
                putBoolean("wake_success_$today", true)
                putString("actual_waketime_$today", currentTime)
                apply()
            }

            Log.d("AlarmActivity", "✅ 기상 성공 기록: $today $currentTime")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "❌ 기상 성공 기록 실패", e)
        }
    }

    private fun stopAlarmSounds() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    /**
     * 코인 지급
     */
    private fun addPawCoins(amount: Int) {
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        val newCount = currentCoins + amount

        sharedPreferences.edit {
            putInt("paw_coin_count", newCount)
        }

        Log.d("AlarmActivity", "발바닥 코인 $amount 개 획득! 총: $newCount")
    }

    /**
     * Day 증가 및 다음 날 알람 설정 (LockScreen과 동일한 패턴)
     */
    private fun incrementDayAndScheduleNextAlarm() {
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val nextDay = currentDay + 1

        // Day 카운트 증가
        sharedPreferences.edit {
            putInt("current_day", nextDay)
        }

        Log.d("AlarmActivity", "Day $currentDay → Day $nextDay 증가")

        // ⭐ 일회성 알람 플래그 제거 (알람이 울렸으므로)
        if (sharedPreferences.getBoolean("is_one_time_alarm", false)) {
            sharedPreferences.edit {
                putBoolean("is_one_time_alarm", false)
                remove("one_time_alarm_time")
            }

            Log.d("AlarmActivity", "일회성 알람 플래그 제거")
        }

        // 다음 날 알람 설정
        val alarmManager = DailyAlarmManager(this)
        alarmManager.updateDailyAlarm(nextDay)

        Log.d("AlarmActivity", "Day $nextDay 알람 설정 완료")
    }

    /**
     * 모닝 루틴으로 이동
     */
    private fun goToMorningRoutine() {
        // ⭐⭐⭐ 모닝 루틴 진입 플래그 설정 (알람 재울림 방지)
        sharedPreferences.edit {
            putBoolean("is_in_morning_routine", true)
            putLong("morning_routine_start_time", System.currentTimeMillis())
        }

        Log.d("AlarmActivity", "✅ 모닝 루틴 진입 플래그 설정")

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MorningRoutineActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1000)
    }

    /**
     * ⭐ 뒤로가기 완전 차단
     */
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 의도적으로 super를 호출하지 않아 뒤로가기를 완전히 차단
        Log.d("AlarmActivity", "뒤로가기 버튼 차단됨 - 알람을 해제해야 합니다")
    }

    override fun onDestroy() {
        super.onDestroy()

        // ⭐ 알람 시간 플래그 해제
        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().putBoolean("is_alarm_time", false).apply()

//        clearAlarmFlags()
//
//        stopAlarmSounds()
//        countDownTimer?.cancel()
//        vibrator?.cancel()
//        longPressHandler?.removeCallbacksAndMessages(null)
//
//        Log.d("AlarmActivity", "✅ 알람 액티비티 종료")
    }
}