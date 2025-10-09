package com.example.sleepshift.feature.alarm

import android.content.Context
import android.content.Intent
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
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityAlarmBinding
import com.example.sleepshift.feature.MorningRoutineActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.DailyAlarmManager
import com.example.sleepshift.util.ConsecutiveSuccessManager

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
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

        setupFullScreenAlarm()
        initializeComponents()

        // 연속 성공 매니저 초기화
        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        setupUI()
        setupLongPressListener()
        startAlarmSounds()
    }

    private fun setupFullScreenAlarm() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

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
        val userName = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            .getString("user_name", "사용자") ?: "사용자"
        binding.tvGoodMorningMessage.text = "${userName}님\n좋은 아침 입니다 !"

        val coinReward = calculateCoinReward()
        binding.tvCoinCount.text = coinReward.toString()

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
            android.util.Log.d("AlarmActivity", "시스템 알람음 재생 시작")
        } catch (e: Exception) {
            android.util.Log.e("AlarmActivity", "알람음 재생 실패: ${e.message}")

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
                android.util.Log.e("AlarmActivity", "백업 알람음도 재생 실패: ${e2.message}")
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
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getInt("current_day", 1)
    }

    private fun dismissAlarm() {
        stopAlarmSounds()
        vibrator?.cancel()

        // ⭐ 코인 지급
        val coinReward = calculateCoinReward()
        addPawCoins(coinReward)

        // ⭐ Day 카운트 증가 및 다음 날 알람 설정
        incrementDayAndScheduleNextAlarm()

        // 성공 애니메이션 표시 후 모닝 루틴으로 이동
        goToMorningRoutine()
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
     * ⭐ 코인 지급
     */
    private fun addPawCoins(amount: Int) {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentCoins = sharedPref.getInt("paw_coin_count", 0)
        val newCount = currentCoins + amount

        with(sharedPref.edit()) {
            putInt("paw_coin_count", newCount)
            apply()
        }

        android.util.Log.d("AlarmActivity", "발바닥 코인 $amount 개 획득! 총: $newCount")
    }

    /**
     * ⭐ Day 증가 및 다음 날 알람 설정 (LockScreen과 동일한 패턴)
     */
    private fun incrementDayAndScheduleNextAlarm() {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentDay = sharedPref.getInt("current_day", 1)
        val nextDay = currentDay + 1

        // ⭐ Day 카운트 증가 (중요!)
        sharedPref.edit()
            .putInt("current_day", nextDay)
            .apply()

        android.util.Log.d("AlarmActivity", "Day $currentDay → Day $nextDay 증가")

        // ⭐ 다음 날 알람 설정 (nextDay + 1이 아니라 nextDay)
        val alarmManager = DailyAlarmManager(this)
        alarmManager.updateDailyAlarm(nextDay)

        android.util.Log.d("AlarmActivity", "Day $nextDay 알람 설정 완료")
    }

    /**
     * ⭐ 구 메서드 (사용 안 함 - 삭제 가능)
     */
    @Deprecated("incrementDayAndScheduleNextAlarm()를 사용하세요")
    private fun scheduleNextAlarm() {
        val alarmManager = DailyAlarmManager(this)
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentDay = sharedPref.getInt("current_day", 1)

        alarmManager.updateDailyAlarm(currentDay + 1)
    }

    //모닝 루틴으로 이동
    private fun goToMorningRoutine() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MorningRoutineActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1000)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 뒤로 가기 버튼 비활성화
        Log.d("AlarmActivity", "뒤로가기 버튼 무시됨 - 알람을 해제해야 합니다")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSounds()
        countDownTimer?.cancel()
        vibrator?.cancel()
        longPressHandler?.removeCallbacksAndMessages(null)
    }
}