package com.example.sleepshift.feature

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
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sleepshift.R
import com.example.sleepshift.feature.alarm.AlarmActivity
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.service.LockMonitoringService
import com.example.sleepshift.service.LockOverlayService
import com.google.android.material.snackbar.Snackbar

class LockScreenActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvWakeTimeMessage: TextView
    private lateinit var tvCoinCount: TextView
    private lateinit var tvUnlockHint: TextView
    private lateinit var btnUnlock: LinearLayout
    private lateinit var countdownSection: LinearLayout
    private lateinit var tvCountdown: TextView

    // Logic Variables
    private val UNLOCK_COST = 15
    private var isUnlocking = false
    private var countDownTimer: CountDownTimer? = null

    // ⭐ [핵심] 정상적인 종료인지 확인하는 플래그 (true면 경고 안 함)
    private var isNormalExit = false

    // System Services
    private var alarmPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private lateinit var notificationManager: NotificationManager

    // Warning Logic
    private val warningHandler = Handler(Looper.getMainLooper())
    private var warningRunnable: Runnable? = null
    private var currentSnackbar: Snackbar? = null

    // Broadcast Receiver
    private val alarmTimeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ALARM_TIME) {
                Log.d(TAG, "🚨 알람 시간 브로드캐스트 수신 -> AlarmActivity 전환")
                transitionToAlarmActivity()
            }
        }
    }

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_DURATION_MS = 3000L
        private const val COUNTDOWN_INTERVAL_MS = 1000L
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "lock_alarm_channel"
        private const val ACTION_ALARM_TIME = "com.example.sleepshift.ALARM_TIME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()
        setContentView(R.layout.activity_lock_screen)

        initializeComponents()
        registerAlarmReceiver()

        Log.d(TAG, "✅ LockScreenActivity 시작 - 대기 모드")
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun initializeComponents() {
        initViews()
        updateDisplays()
        setupUnlockListener()

        // Systems
        initAlarmSound()
        createNotificationChannel()
        initVibrator()
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

    private fun registerAlarmReceiver() {
        val filter = IntentFilter(ACTION_ALARM_TIME)
        ContextCompat.registerReceiver(
            this,
            alarmTimeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ============================================================
    // ⭐ Lifecycle & Warning Logic (핵심 수정 부분)
    // ============================================================

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: LockScreen 복귀")

        // 1. 알람 시간인지 체크
        if (checkIfAlarmTime()) return

        // 2. 정상 복귀 시 경고 모드 해제
        stopWarningMode()

        // 3. 화면 갱신
        updateDisplays()

        // 4. 정상 종료 플래그 초기화 (다시 들어왔으므로)
        isNormalExit = false
    }

    override fun onPause() {
        super.onPause()

        // ⭐ [수정] 정상적인 종료(잠금해제, 알람전환)라면 경고 로직 스킵
        if (isNormalExit) {
            Log.d(TAG, "✅ 정상적인 화면 전환(종료)입니다. 경고를 띄우지 않습니다.")
            return
        }

        // ⭐ 비정상 이탈 감지 로직
        val isScreenOn = isScreenOn()

        if (isScreenOn) {
            // 화면이 켜져 있는데 onPause = 홈 버튼, 메뉴 버튼, 다른 앱 실행 등
            Log.w(TAG, "⚠️ 비정상 이탈 감지 (화면 ON) -> 경고 모드 시작")
            startWarningMode()
            Toast.makeText(this, "LockScreen으로 돌아오세요! 🔊", Toast.LENGTH_SHORT).show()
        } else {
            // 화면이 꺼짐 = 전원 버튼 누름 (정상 동작으로 간주, 소리는 안 냄)
            Log.d(TAG, "💤 화면 꺼짐 (전원 버튼) -> 조용히 대기")
        }
    }

    private fun checkIfAlarmTime(): Boolean {
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        val isAlarmTime = lockPrefs.getBoolean("is_alarm_time", false)

        if (isAlarmTime) {
            Log.d(TAG, "🚨 알람 시간 플래그 감지 -> AlarmActivity 전환")
            lockPrefs.edit().putBoolean("is_alarm_time", false).apply()
            transitionToAlarmActivity()
            return true
        }
        return false
    }

    // ============================================================
    // ⭐ Warning Mode Actions
    // ============================================================

    private fun startWarningMode() {
        startAlarmSound()
        startVibration()
        showAlarmNotification()
        startWarningMessages()
    }

    private fun stopWarningMode() {
        stopAlarmSound()
        stopVibration()
        dismissAlarmNotification()
        stopWarningMessages()
    }

    private fun startWarningMessages() {
        stopWarningMessages() // 중복 방지

        warningRunnable = object : Runnable {
            override fun run() {
                try {
                    currentSnackbar?.dismiss()
                    val rootView = findViewById<View>(android.R.id.content)
                    currentSnackbar = Snackbar.make(rootView, "🔊 LockScreen으로 돌아오세요!", Snackbar.LENGTH_LONG).apply {
                        view.translationY = -100f
                        setBackgroundTint(getColor(android.R.color.holo_red_dark))
                        setTextColor(getColor(android.R.color.white))
                        show()
                    }
                    warningHandler.postDelayed(this, 3000)
                } catch (e: Exception) {
                    Log.e(TAG, "경고 메시지 표시 실패", e)
                }
            }
        }
        warningRunnable?.let { warningHandler.post(it) }
    }

    private fun stopWarningMessages() {
        warningRunnable?.let { warningHandler.removeCallbacks(it) }
        warningRunnable = null
        currentSnackbar?.dismiss()
    }

    // ============================================================
    // ⭐ Transitions (화면 이동)
    // ============================================================

    /**
     * 코인 사용하여 잠금 해제 (정상 종료 1)
     */
    private fun performUnlock() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentCoins = prefs.getInt("paw_coin_count", 0)

        if (currentCoins >= UNLOCK_COST) {
            // 1. 코인 차감
            prefs.edit().putInt("paw_coin_count", currentCoins - UNLOCK_COST).apply()

            // 2. 잠금 플래그 해제 및 서비스 중지
            getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("isLocked", false).apply()

            stopLockMonitoringService()
            LockOverlayService.stop(this)

            // 3. 리소스 정리
            stopWarningMode()
            restoreOriginalVolume()
            releaseAlarmSound()

            Toast.makeText(this, "잠금 해제 완료! 코인 -$UNLOCK_COST", Toast.LENGTH_SHORT).show()

            // 4. [핵심] 정상 종료 플래그 설정 후 이동
            isNormalExit = true

            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "코인이 부족합니다!", Toast.LENGTH_SHORT).show()
            cancelCountdown() // UI 복구
        }
    }

    /**
     * 알람 시간이 되어 알람 화면으로 이동 (정상 종료 2)
     */
    private fun transitionToAlarmActivity() {
        try {
            // [핵심] 정상 종료 플래그 설정
            isNormalExit = true

            val intent = Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
                putExtra("alarm_id", prefs.getLong("current_alarm_id", 0))
            }
            startActivity(intent)
            finish()
            Log.d(TAG, "✅ AlarmActivity로 정상 전환")
        } catch (e: Exception) {
            Log.e(TAG, "AlarmActivity 전환 실패", e)
            isNormalExit = false // 실패 시 플래그 복구
        }
    }

    // ============================================================
    // Sound & Vibration
    // ============================================================

    private fun initAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            alarmPlayer = MediaPlayer.create(this, alarmUri).apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "알람음 초기화 실패", e)
        }
    }

    private fun startAlarmSound() {
        if (alarmPlayer?.isPlaying == false) {
            try { alarmPlayer?.start() } catch (e: Exception) { Log.e(TAG, "알람음 재생 실패", e) }
        }
    }

    private fun stopAlarmSound() {
        if (alarmPlayer?.isPlaying == true) {
            try {
                alarmPlayer?.pause()
                alarmPlayer?.seekTo(0)
            } catch (e: Exception) { Log.e(TAG, "알람음 중지 실패", e) }
        }
    }

    private fun releaseAlarmSound() {
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
            alarmPlayer = null
        } catch (e: Exception) { Log.e(TAG, "알람음 해제 실패", e) }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startVibration() {
        try {
            vibrator?.let {
                val pattern = longArrayOf(0, 1000, 500) // 대기 0, 진동 1초, 쉼 0.5초
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "진동 시작 실패", e) }
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (e: Exception) { Log.e(TAG, "진동 중지 실패", e) }
    }

    // ============================================================
    // Notification & Helpers
    // ============================================================

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "잠금 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "잠금 화면 이탈 알림"
                enableVibration(true)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAlarmNotification() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔊 알람이 울리고 있습니다!")
                .setContentText("앱을 열어서 알람을 중지하세요")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) { Log.e(TAG, "알림 표시 실패", e) }
    }

    private fun dismissAlarmNotification() {
        try { notificationManager.cancel(NOTIFICATION_ID) } catch (e: Exception) { }
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive
        else @Suppress("DEPRECATION") pm.isScreenOn
    }

    @SuppressLint("SetTextI18n")
    private fun updateDisplays() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        val coinCount = prefs.getInt("paw_coin_count", 0)
        val alarmTime = prefs.getString("today_alarm_time", null)
            ?: prefs.getString("target_wake_time", "07:00") ?: "07:00"

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
                    if (!isUnlocking) {
                        isUnlocking = true
                        countdownSection.visibility = View.VISIBLE
                        startCountdown()
                    }
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
                tvCountdown.text = (millisUntilFinished / 1000 + 1).toString()
            }
            override fun onFinish() { performUnlock() }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countdownSection.visibility = View.GONE
        isUnlocking = false
    }

    private fun restoreOriginalVolume() {
        try {
            val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            val originalVolume = prefs.getInt("original_alarm_volume", -1)
            if (originalVolume != -1) {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            }
        } catch (e: Exception) { Log.e(TAG, "볼륨 복원 실패", e) }
    }

    private fun stopLockMonitoringService() {
        try { stopService(Intent(this, LockMonitoringService::class.java)) }
        catch (e: Exception) { Log.e(TAG, "서비스 중지 실패", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(alarmTimeReceiver) } catch (e: Exception) {}
        stopWarningMode()
        releaseAlarmSound()
        countDownTimer?.cancel()
        Log.d(TAG, "LockScreenActivity 종료")
    }

    override fun onBackPressed() {
        // 뒤로가기 차단
    }
}