package com.example.sleepshift.feature.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
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
import com.example.sleepshift.service.LockOverlayService
import com.example.sleepshift.util.DailyAlarmManager
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.text.SimpleDateFormat
import java.util.*

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

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // 알람 볼륨 최대로 (혹시 모를 상황 대비)
        setMaxAlarmVolume()

        // 알람 플래그 설정
        setAlarmFlags()

        setupFullScreenAlarm()
        initializeComponents()

        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        setupUI()
        setupLongPressListener()
        startAlarmSounds()

        Log.d("AlarmActivity", "알람 시작!")
    }

    override fun onResume() {
        super.onResume()

        // 코인 업데이트
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvCoinCount.text = currentCoins.toString()
    }

    // 알람 플래그 설정
    private fun setAlarmFlags() {
        sharedPreferences.edit {
            putBoolean("is_alarm_ringing", true)
            putBoolean("lock_screen_active", false)
        }
        Log.d("AlarmActivity", "알람 플래그 on")
    }

    // 알람 플래그 해제
    private fun clearAlarmFlags() {
        sharedPreferences.edit {
            putBoolean("is_alarm_ringing", false)
        }
        Log.d("AlarmActivity", "알람 플래그 off")
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
        binding.tvGoodMorningMessage.text = "${userName}님\n좋은 아침 입니다!"

        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvCoinCount.text = currentCoins.toString()

        binding.tvUnlockText.text = "알람해제"
        binding.tvUnlockHint.text = "해제를 원하시면 3초간 누르세요"
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
            val alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            Log.d("AlarmActivity", "알람음 시작")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "알람음 실패", e)

            // 백업으로 알림음 사용
            try {
                val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, notificationUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                Log.e("AlarmActivity", "백업 알람음도 실패", e2)
            }
        }
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

        LockOverlayService.stop(this)

        // 기상 성공 기록
        recordWakeSuccess()

        // 원래 볼륨으로 복원
        restoreOriginalVolume()

        // 첫 알람인지 확인
        val alarmId = intent.getLongExtra("alarm_id", 0L)
        val savedAlarmId = sharedPreferences.getLong("current_alarm_id", 0L)
        val isFirstAlarm = (alarmId == savedAlarmId)

        Log.d("AlarmActivity", "알람 ID: $alarmId vs 저장: $savedAlarmId")
        Log.d("AlarmActivity", "첫 알람: $isFirstAlarm")

        // 첫 알람일때만 플래그 설정
        if (isFirstAlarm) {
            sharedPreferences.edit {
                putBoolean("is_first_alarm_try", true)
                putLong("morning_routine_start_time", System.currentTimeMillis())
            }
            Log.d("AlarmActivity", "첫 시도 플래그 설정")
        } else {
            Log.d("AlarmActivity", "재알람 - 플래그 안 설정")
        }

        // Day 증가 및 다음 알람 설정
        incrementDayAndScheduleNextAlarm()

        clearAlarmFlags()

        // 모닝루틴으로 이동
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

    // 알람 볼륨 최대로 설정
    private fun setMaxAlarmVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            // 원래 볼륨 저장 (한번만)
            if (!sharedPreferences.contains("original_alarm_volume")) {
                sharedPreferences.edit().putInt("original_alarm_volume", currentVolume).apply()
                Log.d("AlarmActivity", "원래 볼륨 저장: $currentVolume")
            }

            // 볼륨 최대로
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            Log.d("AlarmActivity", "알람 볼륨 최대: $currentVolume -> $maxVolume")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "볼륨 설정 실패", e)
        }
    }

    // 원래 볼륨으로 복원
    private fun restoreOriginalVolume() {
        try {
            val originalVolume = sharedPreferences.getInt("original_alarm_volume", -1)

            if (originalVolume != -1) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                Log.d("AlarmActivity", "볼륨 복원: $originalVolume")
            }
        } catch (e: Exception) {
            Log.e("AlarmActivity", "볼륨 복원 실패", e)
        }
    }

    // 기상 성공 기록
    private fun recordWakeSuccess() {
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            // 첫 시도인지 확인
            val alarmId = intent.getLongExtra("alarm_id", 0L)
            val savedAlarmId = sharedPreferences.getLong("current_alarm_id", 0L)
            val isFirstAlarm = (alarmId == savedAlarmId)

            // 첫 시도만 기상 성공
            val wakeSuccess = isFirstAlarm

            sharedPreferences.edit().apply {
                putBoolean("wake_success_$today", wakeSuccess)
                putString("actual_waketime_$today", currentTime)
                apply()
            }

            if (wakeSuccess) {
                Log.d("AlarmActivity", "✅ 기상 성공: $today $currentTime")
            } else {
                Log.d("AlarmActivity", "❌ 재알람 - 기상 실패: $today $currentTime")
            }
        } catch (e: Exception) {
            Log.e("AlarmActivity", "기상 기록 실패", e)
        }
    }

    // Day 증가 및 다음 알람 설정
    private fun incrementDayAndScheduleNextAlarm() {
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val nextDay = currentDay + 1

        sharedPreferences.edit {
            putInt("current_day", nextDay)
        }

        Log.d("AlarmActivity", "Day $currentDay -> Day $nextDay")

        // 일회성 알람이었으면 플래그 제거
        if (sharedPreferences.getBoolean("is_one_time_alarm", false)) {
            sharedPreferences.edit {
                putBoolean("is_one_time_alarm", false)
                remove("one_time_alarm_time")
            }
            Log.d("AlarmActivity", "일회성 알람 제거")
        }

        // 다음날 알람 설정
        val alarmManager = DailyAlarmManager(this)
        alarmManager.updateDailyAlarm(nextDay)

        Log.d("AlarmActivity", "Day $nextDay 알람 설정")
    }

    // 모닝루틴으로 이동
    private fun goToMorningRoutine() {
        sharedPreferences.edit {
            putBoolean("is_in_morning_routine", true)
            putLong("morning_routine_start_time", System.currentTimeMillis())
        }

        Log.d("AlarmActivity", "모닝루틴 진입")

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MorningRoutineActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1000)
    }

    // 뒤로가기 차단
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d("AlarmActivity", "뒤로가기 차단")
    }

    override fun onDestroy() {
        super.onDestroy()

        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().putBoolean("is_alarm_time", false).apply()
    }
}