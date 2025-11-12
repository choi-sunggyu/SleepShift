package com.example.sleepshift.feature

import android.util.Log
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sleepshift.R
import com.example.sleepshift.service.LockOverlayService
import com.google.android.material.snackbar.Snackbar

class LockScreenActivity : AppCompatActivity() {

    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvWakeTimeMessage: TextView
    private lateinit var tvCoinCount: TextView
    private lateinit var tvUnlockHint: TextView
    private lateinit var btnUnlock: LinearLayout
    private lateinit var countdownSection: LinearLayout
    private lateinit var tvCountdown: TextView

    private val UNLOCK_COST = 15
    private var isUnlocking = false
    private var countDownTimer: CountDownTimer? = null

    // ⭐⭐⭐ 알람음 재생기
    private var alarmPlayer: MediaPlayer? = null
    private var isOnLockScreen = true  // LockScreen에 있는지 여부

    // ⭐⭐⭐ 알람 시간 브로드캐스트 리시버
    private val alarmTimeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "========== 브로드캐스트 수신 ==========")
            Log.d(TAG, "Action: ${intent?.action}")

            if (intent?.action == "com.example.sleepshift.ALARM_TIME") {
                Log.d(TAG, "🚨🚨🚨 알람 시간 브로드캐스트 수신!")
                transitionToAlarmActivity()
            } else {
                Log.w(TAG, "알 수 없는 브로드캐스트: ${intent?.action}")
            }

            Log.d(TAG, "========== 브로드캐스트 처리 완료 ==========")
        }
    }

    //노티피케이션
    private lateinit var notificationManager: NotificationManager
    private var vibrator: Vibrator? = null
    private val warningHandler = android.os.Handler(Looper.getMainLooper())
    private var warningRunnable: Runnable? = null
    private var currentSnackbar: Snackbar? = null

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_DURATION_MS = 3000L
        private const val COUNTDOWN_INTERVAL_MS = 1000L
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "lock_alarm_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ⭐⭐⭐ 화면 켜기 및 잠금 해제
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_lock_screen)

        initViews()
        updateDisplays()
        setupUnlockListener()
        initAlarmSound()
        createNotificationChannel()
        initVibrator()

        // ⭐⭐⭐ 알람 시간 브로드캐스트 등록
        Log.d(TAG, "🔔 알람 시간 브로드캐스트 리시버 등록 시작")
        val filter = IntentFilter("com.example.sleepshift.ALARM_TIME")
        ContextCompat.registerReceiver(
            this,
            alarmTimeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "✅ 알람 시간 브로드캐스트 리시버 등록 완료!")

        // ⭐⭐⭐ LockScreen이 처음 시작될 때 (알람에서 넘어올 때)
        // 알람음과 진동은 시작하지 않음 - 조용히 대기
        Log.d(TAG, "✅ LockScreenActivity 시작 - 조용히 대기")
    }

    //진동 초기화
    private fun showAlarmNotification() {
        try {
            // LockScreenActivity로 돌아가는 Intent
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔊 알람이 울리고 있습니다!")
                .setContentText("앱을 열어서 알람을 중지하세요")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // 시스템 기본 아이콘
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)  // 사용자가 직접 닫을 수 없음
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 500, 500))  // 진동
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ 알림 표시 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 표시 실패", e)
        }
    }

    //진동 반복
    private fun startVibration() {
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 패턴: 0.5초 대기 → 1초 진동 → 0.5초 대기 → 반복
                    val pattern = longArrayOf(0, 1000, 500)
                    val effect = VibrationEffect.createWaveform(pattern, 0)  // 0 = 무한 반복
                    it.vibrate(effect)
                } else {
                    // 구버전
                    @Suppress("DEPRECATION")
                    val pattern = longArrayOf(0, 1000, 500)
                    it.vibrate(pattern, 0)  // 0 = 무한 반복
                }
                Log.d(TAG, "🔔 진동 시작!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 진동 시작 실패", e)
        }
    }

    //진동 중지
    private fun stopVibration() {
        try {
            vibrator?.cancel()
            Log.d(TAG, "🔕 진동 중지")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 진동 중지 실패", e)
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        Log.d(TAG, "✅ 진동 초기화 완료")
    }

    /**
     * ⭐⭐⭐ 반복 경고 메시지 시작
     */
    private fun startWarningMessages() {
        stopWarningMessages()  // 기존 것 중지

        warningRunnable = object : Runnable {
            override fun run() {
                try {
                    // Snackbar 사용 (Toast보다 오래 표시)
                    currentSnackbar?.dismiss()

                    val rootView = findViewById<View>(android.R.id.content)
                    currentSnackbar = Snackbar.make(
                        rootView,
                        "🔊 LockScreen으로 돌아오세요!",
                        Snackbar.LENGTH_LONG
                    ).apply {
                        // 상단에 표시
                        view.translationY = -100f

                        // 배경색 변경
                        setBackgroundTint(getColor(android.R.color.holo_red_dark))
                        setTextColor(getColor(android.R.color.white))

                        show()
                    }

                    // 3초마다 반복
                    warningHandler.postDelayed(this, 3000)
                } catch (e: Exception) {
                    Log.e(TAG, "경고 메시지 표시 실패", e)
                }
            }
        }

        // ⭐⭐⭐ 수정: 최초 실행
        warningRunnable?.let { warningHandler.post(it) }
        Log.d(TAG, "⚠️ 반복 경고 메시지 시작")
    }

    /**
     * ⭐⭐⭐ 반복 경고 메시지 중지
     */
    private fun stopWarningMessages() {
        if (warningRunnable != null) {
            warningHandler.removeCallbacks(warningRunnable!!)
        }
        warningRunnable = null
        currentSnackbar?.dismiss()
        currentSnackbar = null
        Log.d(TAG, "✅ 반복 경고 메시지 중지")
    }

    private fun dismissAlarmNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "✅ 알림 제거 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 제거 실패", e)
        }
    }

    private fun initViews() {
        tvGoodNightMessage = findViewById(R.id.tvGoodNightMessage)
        tvWakeTimeMessage = findViewById(R.id.tvWakeTimeMessage)
        tvCoinCount = findViewById(R.id.tvCoinCount)
        tvUnlockHint = findViewById(R.id.tvUnlockHint)
        btnUnlock = findViewById(R.id.btnUnlock)
        countdownSection = findViewById(R.id.countdownSection)
        tvCountdown = findViewById(R.id.tvCountdown)
    }

    /**
     * ⭐⭐⭐ 알람음 초기화
     */
    private fun initAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            alarmPlayer = MediaPlayer.create(this, alarmUri)
            alarmPlayer?.isLooping = true  // 계속 반복
            alarmPlayer?.setVolume(1.0f, 1.0f)  // 최대 볼륨

            Log.d(TAG, "✅ 알람음 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람음 초기화 실패", e)
        }
    }

    /**
     * ⭐⭐⭐ 알람음 시작
     */
    private fun startAlarmSound() {
        try {
            if (alarmPlayer?.isPlaying == false) {
                alarmPlayer?.start()
                Log.d(TAG, "🔊 알람음 시작!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람음 재생 실패", e)
        }
    }

    /**
     * ⭐⭐⭐ 알람음 중지
     */
    private fun stopAlarmSound() {
        try {
            if (alarmPlayer?.isPlaying == true) {
                alarmPlayer?.pause()
                alarmPlayer?.seekTo(0)  // 처음으로 되돌리기
                Log.d(TAG, "🔇 알람음 중지")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람음 중지 실패", e)
        }
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "잠금 알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "잠금 화면 알람 알림"
                enableVibration(true)
                setSound(null, null)  // 소리는 MediaPlayer로 처리
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ 알림 채널 생성 완료")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDisplays() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        val coinCount = prefs.getInt("paw_coin_count", 0)

        val alarmTime = prefs.getString("today_alarm_time", null)
            ?: prefs.getString("target_wake_time", "07:00")
            ?: "07:00"

        tvGoodNightMessage.text = "${userName}님 잘자요!"
        tvWakeTimeMessage.text = "${alarmTime}에 깨워드릴게요"
        tvCoinCount.text = coinCount.toString()
        tvUnlockHint.text = "해제를 원하시면 3초간 누르세요 (코인 ${UNLOCK_COST}개 소모)"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUnlockListener() {
        btnUnlock.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isUnlocking) return@setOnTouchListener true
                    isUnlocking = true

                    countdownSection.visibility = View.VISIBLE
                    startCountdown()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelCountdown()
                    true
                }
                else -> false
            }
        }
    }

    private fun startCountdown() {
        tvCountdown.text = "3"
        countDownTimer = object : CountDownTimer(UNLOCK_DURATION_MS, COUNTDOWN_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000 + 1).toInt()
                tvCountdown.text = secondsLeft.toString()
            }

            override fun onFinish() {
                performUnlock()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countdownSection.visibility = View.GONE
        isUnlocking = false
    }

    private fun performUnlock() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentCoins = prefs.getInt("paw_coin_count", 0)

        if (currentCoins >= UNLOCK_COST) {
            prefs.edit().apply {
                putInt("paw_coin_count", currentCoins - UNLOCK_COST)
                apply()
            }

            // 잠금 플래그 해제
            val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
            lockPrefs.edit().putBoolean("isLocked", false).apply()

            // 서비스 중지
            stopLockMonitoringService()
            LockOverlayService.stop(this)

            //알람음 완전히 해제
            stopAlarmSound()
            stopVibration()
            restoreOriginalVolume()
            dismissAlarmNotification()
            stopWarningMessages()
            releaseAlarmSound()

            Toast.makeText(this, "잠금 해제 완료! 코인 -$UNLOCK_COST", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, com.example.sleepshift.feature.home.HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            finish()
        } else {
            Toast.makeText(this, "코인이 부족합니다!", Toast.LENGTH_SHORT).show()
        }

        countdownSection.visibility = View.GONE
        isUnlocking = false
        updateDisplays()
    }

    private fun restoreOriginalVolume() {
        try {
            val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            val originalVolume = prefs.getInt("original_alarm_volume", -1)

            if (originalVolume != -1) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    originalVolume,
                    0
                )
                Log.d(TAG, "🔊 알람 볼륨 복원: $originalVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 볼륨 복원 실패", e)
        }
    }

    private fun stopLockMonitoringService() {
        try {
            val serviceIntent = Intent(this, com.example.sleepshift.service.LockMonitoringService::class.java)
            stopService(serviceIntent)
            Log.d(TAG, "✅ LockMonitoringService 중지")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LockMonitoringService 중지 실패", e)
        }
    }

    /**
     * ⭐⭐⭐ AlarmActivity로 전환
     */
    private fun transitionToAlarmActivity() {
        try {
            Log.d(TAG, "🔄 AlarmActivity로 전환 시작")

            val alarmIntent = Intent(this, com.example.sleepshift.feature.alarm.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
                val alarmId = prefs.getLong("current_alarm_id", 0)
                putExtra("alarm_id", alarmId)
            }

            startActivity(alarmIntent)
            finish()

            Log.d(TAG, "✅ AlarmActivity 시작 및 LockScreen 종료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ AlarmActivity 전환 실패", e)
        }
    }

    /**
     * ⭐⭐⭐ 화면이 켜져있는지 확인
     */
    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    /**
     * ⭐⭐⭐ LockScreen에서 벗어날 때 (홈 버튼, 다른 앱 등)
     * - 화면이 꺼진 상태 (전원 버튼): 알람 울리지 않음
     * - 화면이 켜진 상태 (홈 버튼/앱 전환): 알람 울림
     */
    override fun onPause() {
        super.onPause()
        isOnLockScreen = false

        // ⭐⭐⭐ 화면이 켜져있으면 사용자가 의도적으로 떠난 것
        val screenOn = isScreenOn()

        if (screenOn) {
            // 화면이 켜져있는데 onPause() = 홈 버튼 또는 앱 전환
            startAlarmSound()
            startVibration()
            showAlarmNotification()
            startWarningMessages()

            Log.d(TAG, "⚠️ LockScreen 벗어남 (화면 ON) - 알람음 + 진동 + 경고 시작!")
            Toast.makeText(this, "LockScreen으로 돌아오세요! 🔊", Toast.LENGTH_SHORT).show()
        } else {
            // 화면이 꺼진 상태에서 onPause() = 전원 버튼
            Log.d(TAG, "💤 화면 꺼짐 (전원버튼) - 알람 없음")
        }
    }

    /**
     * ⭐⭐⭐ LockScreen으로 돌아올 때
     */
    override fun onResume() {
        super.onResume()
        isOnLockScreen = true

        Log.d(TAG, "========== onResume 시작 ==========")

        // ⭐⭐⭐ 알람 시간이 된 경우 AlarmActivity로 전환
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        val isAlarmTime = lockPrefs.getBoolean("is_alarm_time", false)

        Log.d(TAG, "is_alarm_time 플래그 체크: $isAlarmTime")

        if (isAlarmTime) {
            Log.d(TAG, "🚨🚨🚨 알람 시간 감지! AlarmActivity로 전환 시작")

            // 플래그 리셋
            lockPrefs.edit().putBoolean("is_alarm_time", false).apply()
            Log.d(TAG, "is_alarm_time 플래그 리셋 완료")

            // AlarmActivity로 전환
            transitionToAlarmActivity()
            return
        }

        // ⭐ 알람 시간이 아닌 경우 정상 처리
        Log.d(TAG, "정상 onResume 처리 - 알람 중지")
        stopAlarmSound()
        stopVibration()
        dismissAlarmNotification()
        stopWarningMessages()

        updateDisplays()
        Log.d(TAG, "✅ LockScreen 복귀 - 알람음 + 진동 + 경고 중지")
        Log.d(TAG, "========== onResume 종료 ==========")
    }

    /**
     * ⭐⭐⭐ 알람음 리소스 해제
     */
    private fun releaseAlarmSound() {
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
            alarmPlayer = null
            Log.d(TAG, "✅ 알람음 리소스 해제")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람음 리소스 해제 실패", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // ⭐⭐⭐ 브로드캐스트 리시버 해제
        try {
            unregisterReceiver(alarmTimeReceiver)
            Log.d(TAG, "✅ 알람 시간 브로드캐스트 리시버 해제")
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 리시버 해제 실패", e)
        }

        stopAlarmSound()
        stopVibration()
        dismissAlarmNotification()
        stopWarningMessages()
        releaseAlarmSound()
        countDownTimer?.cancel()
        Log.d(TAG, "LockScreenActivity 종료")
    }

    override fun onBackPressed() {
        // 뒤로가기 막기
        Log.d(TAG, "뒤로가기 차단됨")
    }
}