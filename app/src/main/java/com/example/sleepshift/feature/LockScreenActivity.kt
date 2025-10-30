package com.example.sleepshift.feature

import android.util.Log
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.example.sleepshift.R
import com.example.sleepshift.service.LockOverlayService

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

    //노티피케이션
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_DURATION_MS = 3000L
        private const val COUNTDOWN_INTERVAL_MS = 1000L
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "lock_alarm_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        initViews()
        updateDisplays()
        setupUnlockListener()
        initAlarmSound()
        createNotificationChannel()
    }

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

            // ⭐⭐⭐ 알람음 완전히 해제
            stopAlarmSound()
            dismissAlarmNotification()
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
     * ⭐⭐⭐ LockScreen에서 벗어날 때 (홈 버튼, 다른 앱 등)
     */
    override fun onPause() {
        super.onPause()
        isOnLockScreen = false

        // ⭐ LockScreen을 벗어나면 알람음 시작!
        startAlarmSound()
        showAlarmNotification()

        Log.d(TAG, "⚠️ LockScreen 벗어남 - 알람음 시작!")
        Toast.makeText(this, "LockScreen으로 돌아오세요! 🔊", Toast.LENGTH_SHORT).show()
    }

    /**
     * ⭐⭐⭐ LockScreen으로 돌아올 때
     */
    override fun onResume() {
        super.onResume()
        isOnLockScreen = true

        // ⭐ LockScreen으로 돌아오면 알람음 중지!
        stopAlarmSound()
        dismissAlarmNotification()

        updateDisplays()
        Log.d(TAG, "✅ LockScreen 복귀 - 알람음 중지")
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
        stopAlarmSound()
        dismissAlarmNotification()
        releaseAlarmSound()
        countDownTimer?.cancel()
        Log.d(TAG, "LockScreenActivity 종료")
    }

    override fun onBackPressed() {
        // 뒤로가기 막기
        Log.d(TAG, "뒤로가기 차단됨")
    }
}