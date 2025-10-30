package com.example.sleepshift.feature

import android.util.Log
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_DURATION_MS = 3000L
        private const val COUNTDOWN_INTERVAL_MS = 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        initViews()
        updateDisplays()
        setupUnlockListener()
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

    @SuppressLint("SetTextI18n")
    private fun updateDisplays() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        val coinCount = prefs.getInt("paw_coin_count", 0)

        // ⭐ 알람 시간 가져오기 (today_alarm_time 우선, 없으면 target_wake_time)
        val alarmTime = prefs.getString("today_alarm_time", null)
            ?: prefs.getString("target_wake_time", "07:00")
            ?: "07:00"

        tvGoodNightMessage.text = "${userName}님 잘자요!"
        tvWakeTimeMessage.text = "${alarmTime}에 깨워드릴게요"  // ⭐ 그대로 사용
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

            // ⭐ 잠금 플래그 해제
            val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
            lockPrefs.edit().putBoolean("isLocked", false).apply()

            // ⭐⭐⭐ 모니터링 서비스 중지
            stopLockMonitoringService()

            // ⭐ 오버레이 서비스 중지
            LockOverlayService.stop(this)

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

    /**
     * ⭐⭐⭐ LockMonitoringService 중지
     */
    private fun stopLockMonitoringService() {
        try {
            val serviceIntent = Intent(this, com.example.sleepshift.service.LockMonitoringService::class.java)
            stopService(serviceIntent)
            Log.d(TAG, "✅ LockMonitoringService 중지")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LockMonitoringService 중지 실패", e)
        }
    }

    override fun onResume() {
        super.onResume()
        updateDisplays()
    }

    override fun onBackPressed() {
        // 뒤로가기 막기
    }
}
