package com.example.sleepshift

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.sleepshift.feature.home.HomeActivity
import kotlinx.coroutines.*

class LockScreenActivity : AppCompatActivity() {

    private lateinit var powerManager: PowerManager
    private lateinit var tvCoinCount: TextView
    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvWakeTimeMessage: TextView
    private lateinit var tvUnlockHint: TextView
    private lateinit var countdownSection: LinearLayout
    private lateinit var tvCountdown: TextView
    private lateinit var imgLockIcon: ImageView

    private var unlockJob: Job? = null
    private var countdownJob: Job? = null
    private var isPressed = false
    private var pressStartTime = 0L
    private val unlockDuration = 3000L

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_COST = 15
        private const val ACTION_ALARM_UPDATED = "com.example.sleepshift.ALARM_UPDATED"
    }

    private val alarmUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "📩 알람 업데이트 수신")
            updateAllDisplays()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "✅ LockScreenActivity onCreate 시작")

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)


        // 1️⃣ 레이아웃 먼저 로드
        setContentView(R.layout.activity_lock_screen)
        initViews()

        // 2️⃣ Window 플래그 및 Lock 해제 설정
        setupWindowFlags()

        // 3️⃣ 디스플레이 업데이트
        updateAllDisplays()

        // 4️⃣ 버튼 설정
        setupUnlockButton()

        // 5️⃣ Broadcast 수신 등록 (LocalBroadcastManager)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(alarmUpdateReceiver, IntentFilter(ACTION_ALARM_UPDATED))

        Log.d(TAG, "✅ LockScreenActivity onCreate 완료")
    }

    private fun setupWindowFlags() {
        // 공식 API (Android 8.1 이상)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // KeyguardManager 사용해서 시스템 잠금 해제 요청
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
    }

    private fun initViews() {
        tvCoinCount = findViewById(R.id.tvCoinCount)
        tvGoodNightMessage = findViewById(R.id.tvGoodNightMessage)
        tvWakeTimeMessage = findViewById(R.id.tvWakeTimeMessage)
        tvUnlockHint = findViewById(R.id.tvUnlockHint)
        countdownSection = findViewById(R.id.countdownSection)
        tvCountdown = findViewById(R.id.tvCountdown)
        imgLockIcon = findViewById(R.id.imgLockIcon)
    }

    private fun updateAllDisplays() {
        val prefs = applicationContext.getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        val alarmHour = prefs.getInt("alarm_hour", 7)

        tvGoodNightMessage.text = "${userName}님 잘자요!"
        tvWakeTimeMessage.text = "${alarmHour}시에 깨워드릴게요"
        tvUnlockHint.text = "해제를 원하시면 3초간 누르세요 (코인 ${UNLOCK_COST}개 소모)"

        updateCoinDisplay()
    }

    private fun updateCoinDisplay() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("paw_coin_count", 0)
        tvCoinCount.text = coins.toString()
        Log.d(TAG, "코인 개수 업데이트: $coins")
    }

    private fun setupUnlockButton() {
        val unlockButton = findViewById<View>(R.id.btnUnlock)
        unlockButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    pressStartTime = System.currentTimeMillis()
                    startCountdown()
                    unlockJob = startUnlockTimer()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    stopCountdown()
                    unlockJob?.cancel()
                    true
                }
                else -> false
            }
        }
    }

    private fun startCountdown() {
        countdownSection.visibility = View.VISIBLE
        tvCountdown.text = "3"

        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            for (i in 3 downTo 1) {
                if (!isPressed) break
                tvCountdown.text = i.toString()
                delay(1000)
            }
        }
    }

    private fun stopCountdown() {
        countdownSection.visibility = View.GONE
        countdownJob?.cancel()
    }

    private fun startUnlockTimer() = CoroutineScope(Dispatchers.Main).launch {
        delay(unlockDuration)
        if (isPressed) tryUnlock()
    }

    private fun tryUnlock() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("paw_coin_count", 0)

        if (coins >= UNLOCK_COST) {
            val newCoinCount = coins - UNLOCK_COST
            prefs.edit().putInt("paw_coin_count", newCoinCount).apply()

            val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
            lockPrefs.edit().putBoolean("isLocked", false).commit()

            Toast.makeText(this, "🔓 잠금 해제!", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.Main).launch {
                delay(300)
                val intent = Intent(this@LockScreenActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        } else {
            Toast.makeText(
                this,
                "⚠️ 코인이 부족합니다!\n필요: ${UNLOCK_COST}개 / 보유: ${coins}개",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        setupWindowFlags()
        updateAllDisplays()
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockJob?.cancel()
        countdownJob?.cancel()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(alarmUpdateReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BACK -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
