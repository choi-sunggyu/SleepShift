package com.example.sleepshift.feature.alarm

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityAlarmBinding
import com.example.sleepshift.feature.MorningRoutineActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.service.LockOverlayService
import com.example.sleepshift.util.DailyAlarmManager
import com.example.sleepshift.util.ConsecutiveSuccessManager

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

        // ⭐⭐⭐ 알람 볼륨 최대로 설정
        setAlarmVolumeToMax()

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
     * ⭐⭐⭐ 알람 관련 모든 볼륨을 최대로 설정
     */
    private fun setAlarmVolumeToMax() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔊 알람 볼륨 최대 설정 시작")

            // ⭐ 1. STREAM_ALARM (알람 전용)
            val currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            if (!prefs.contains("original_alarm_volume")) {
                prefs.edit().putInt("original_alarm_volume", currentAlarmVolume).apply()
                Log.d(TAG, "  - 원래 알람 볼륨 저장: $currentAlarmVolume")
            }

            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                maxAlarmVolume,
                0
            )
            Log.d(TAG, "  - STREAM_ALARM: $currentAlarmVolume → $maxAlarmVolume ✅")

            // ⭐ 2. STREAM_MUSIC (미디어 재생, 많은 알람 앱이 사용)
            val currentMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            if (!prefs.contains("original_music_volume")) {
                prefs.edit().putInt("original_music_volume", currentMusicVolume).apply()
                Log.d(TAG, "  - 원래 미디어 볼륨 저장: $currentMusicVolume")
            }

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                maxMusicVolume,
                0
            )
            Log.d(TAG, "  - STREAM_MUSIC: $currentMusicVolume → $maxMusicVolume ✅")

            // ⭐ 3. 음소거/진동 모드 해제 (Android M 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val currentRingerMode = audioManager.ringerMode

                if (!prefs.contains("original_ringer_mode")) {
                    prefs.edit().putInt("original_ringer_mode", currentRingerMode).apply()
                    Log.d(TAG, "  - 원래 벨소리 모드 저장: $currentRingerMode")
                }

                when (currentRingerMode) {
                    AudioManager.RINGER_MODE_SILENT -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d(TAG, "  - 음소거 모드 → 일반 모드로 변경 ✅")
                    }
                    AudioManager.RINGER_MODE_VIBRATE -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d(TAG, "  - 진동 모드 → 일반 모드로 변경 ✅")
                    }
                    AudioManager.RINGER_MODE_NORMAL -> {
                        Log.d(TAG, "  - 이미 일반 모드 ✅")
                    }
                }
            }

            // ⭐ 4. 방해 금지 모드 확인 (경고만)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    Log.w(TAG, "⚠️ 방해 금지 모드 활성화됨! 알람 소리가 작을 수 있음")

                    // 방해 금지 모드를 해제하려면 권한이 필요하므로 토스트로 알림만
                    Toast.makeText(
                        this,
                        "⚠️ 방해 금지 모드가 켜져 있습니다. 알람이 제대로 울리지 않을 수 있어요.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.d(TAG, "  - 방해 금지 모드 꺼짐 ✅")
                }
            }

            Log.d(TAG, "✅ 알람 볼륨 설정 완료")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")

            Toast.makeText(this, "알람 볼륨이 최대로 설정되었습니다", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람 볼륨 설정 실패", e)
            Toast.makeText(this, "볼륨 설정 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ⭐⭐⭐ 원래 볼륨으로 복원
     */
    private fun restoreOriginalVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔊 원래 볼륨 복원 시작")

            // 1. STREAM_ALARM 복원
            if (prefs.contains("original_alarm_volume")) {
                val originalVolume = prefs.getInt("original_alarm_volume", -1)
                if (originalVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                    Log.d(TAG, "  - STREAM_ALARM 복원: $originalVolume")
                }
                prefs.edit().remove("original_alarm_volume").apply()
            }

            // 2. STREAM_MUSIC 복원
            if (prefs.contains("original_music_volume")) {
                val originalVolume = prefs.getInt("original_music_volume", -1)
                if (originalVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    Log.d(TAG, "  - STREAM_MUSIC 복원: $originalVolume")
                }
                prefs.edit().remove("original_music_volume").apply()
            }

            // 3. 벨소리 모드 복원
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (prefs.contains("original_ringer_mode")) {
                    val originalMode = prefs.getInt("original_ringer_mode", AudioManager.RINGER_MODE_NORMAL)
                    audioManager.ringerMode = originalMode
                    Log.d(TAG, "  - 벨소리 모드 복원: $originalMode")
                    prefs.edit().remove("original_ringer_mode").apply()
                }
            }

            Log.d(TAG, "✅ 볼륨 복원 완료")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 볼륨 복원 실패", e)
        }
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

        // ⭐ 오버레이 서비스 중지
        LockOverlayService.stop(this)

        // ⭐⭐⭐ 알람 해제 시 코인 지급 없음 (문제 2 해결)
        // 코인은 모닝 루틴 완료 시에만 지급

        // ⭐ Day 카운트 증가 및 다음 날 알람 설정
        incrementDayAndScheduleNextAlarm()

        // ⭐ 알람 플래그 해제
        clearAlarmFlags()

        // ⭐ 볼륨 복원 (선택적 - 필요한 경우 주석 해제)
        restoreOriginalVolume()

        // 모닝 루틴으로 이동
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