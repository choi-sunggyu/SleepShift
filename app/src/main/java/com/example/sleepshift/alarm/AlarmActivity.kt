package com.example.sleepshift.feature.alarm

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityAlarmBinding
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.DailyAlarmManager

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
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
        setupUI()
        setupLongPressListener()
        startAlarmSounds()
    }

    private fun setupFullScreenAlarm() {
        // 잠금화면 위에 표시되도록 설정
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // 전체화면으로 설정
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
        // 사용자 이름 설정 (XML의 tvGoodNightMessage를 사용)
        val userName = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            .getString("user_name", "성규") ?: "성규"
        binding.tvGoodNightMessage.text = "${userName}님\n좋은 아침 입니다 !"

        // 발바닥 코인 보상 설정
        val coinReward = calculateCoinReward()
        binding.tvCoinCount.text = coinReward.toString()

        // 버튼 텍스트를 알람해제로 변경
        binding.tvUnlockText.text = "알람해제"
        binding.tvUnlockHint.text = "해제를 원하시면 3초간 누르세요"

        // 카운트다운 텍스트도 알람해제용으로 변경
        binding.countdownSection.findViewById<android.widget.TextView>(R.id.tvCountdown)?.let {
            // 카운트다운 아래 텍스트 찾아서 변경
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
        // XML의 btnUnlock을 btnStopAlarm처럼 사용
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

        // 카운트다운 표시
        binding.countdownSection.visibility = android.view.View.VISIBLE

        // 3초 카운트다운 시작
        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.tvCountdown.text = secondsLeft.toString()
            }

            override fun onFinish() {
                // 알람 해제
                dismissAlarm()
            }
        }.start()

        // 진동 시작
        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 100, 100, 100, 100)
            vibrator?.vibrate(pattern, 0) // 반복 진동
        }
    }

    private fun stopLongPress() {
        if (!isLongPressing) return

        isLongPressing = false
        countDownTimer?.cancel()
        vibrator?.cancel()

        // 카운트다운 숨기기
        binding.countdownSection.visibility = android.view.View.GONE
        binding.tvCountdown.text = "3"
    }

    private fun startAlarmSounds() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            android.util.Log.e("AlarmActivity", "알람 소리 재생 실패: ${e.message}")

            // 알람 소리 파일이 없는 경우 기본 알람음 사용
            try {
                mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } catch (e2: Exception) {
                android.util.Log.e("AlarmActivity", "기본 알람음도 재생 실패: ${e2.message}")
            }
        }
    }

    private fun calculateCoinReward(): Int {
        // 기상시간에 따른 보상 계산 (기본 3개)
        val currentDay = getCurrentDay()

        // Day에 따른 보너스 (매 5일마다 +1 보너스)
        val dayBonus = currentDay / 5
        val baseReward = 3

        return baseReward + dayBonus
    }

    private fun getCurrentDay(): Int {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getInt("current_day", 1)
    }

    private fun dismissAlarm() {
        // 알람 중지
        stopAlarmSounds()
        vibrator?.cancel()

        // 발바닥 코인 보상 지급
        val coinReward = calculateCoinReward()
        addPawCoins(coinReward)

        // 다음 날 알람 설정
        scheduleNextAlarm()

        // 성공 애니메이션 표시 후 홈으로 이동
        showSuccessAnimation()
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

    private fun addPawCoins(amount: Int) {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentCoins = sharedPref.getInt("paw_coin_count", 130)
        val newCount = currentCoins + amount

        with(sharedPref.edit()) {
            putInt("paw_coin_count", newCount)
            apply()
        }

        android.util.Log.d("AlarmActivity", "발바닥 코인 $amount 개 획득! 총: $newCount")
    }

    private fun scheduleNextAlarm() {
        val alarmManager = DailyAlarmManager(this)
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentDay = sharedPref.getInt("current_day", 1)

        // 다음 날 알람 설정
        alarmManager.updateDailyAlarm(currentDay + 1)
    }

    private fun showSuccessAnimation() {
        // 성공 메시지 표시
        binding.tvGoodNightMessage.text = "알람 해제 완료!\n발바닥 코인을 획득했습니다"

        // 버튼 숨기기
        binding.bottomSection.visibility = android.view.View.GONE

        // 2초 후 홈 화면으로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 2000)
    }


    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSounds()
        countDownTimer?.cancel()
        vibrator?.cancel()
        longPressHandler?.removeCallbacksAndMessages(null)
    }
}
