package com.example.sleepshift

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import kotlinx.coroutines.*

class LockScreenActivity : AppCompatActivity() {

    private lateinit var powerManager: PowerManager
    private lateinit var tvCoinCount: TextView
    private var unlockJob: Job? = null
    private var isPressed = false
    private var pressStartTime = 0L
    private val unlockDuration = 3000L // 3초간 누르면 해제

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        tvCoinCount = findViewById(R.id.tvCoinCount)

        // 🔹 화면 잠금 관련 플래그 설정
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // 🔹 기존 키가드 해제
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        // 🔹 코인 표시 업데이트
        updateCoinDisplay()

        // 🔹 해제 버튼 (길게 누름 이벤트)
        val unlockButton = findViewById<View>(R.id.btnUnlock)
        unlockButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    pressStartTime = System.currentTimeMillis()
                    unlockJob = startUnlockTimer()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    unlockJob?.cancel()
                    true
                }
                else -> false
            }
        }
    }

    // 🔹 홈, 메뉴, 백버튼 차단
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BACK -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 🔹 홈으로 나가려 하면 다시 복귀
    override fun onPause() {
        super.onPause()
        val intent = Intent(this, LockScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    // 🔹 코루틴으로 3초 누름 감지
    private fun startUnlockTimer() = CoroutineScope(Dispatchers.Main).launch {
        delay(unlockDuration)
        if (isPressed) {
            tryUnlock()
        }
    }

    // 🔹 코인 차감 후 해제 시도
    private fun tryUnlock() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coin_count", 3)

        if (coins > 0) {
            prefs.edit().putInt("coin_count", coins - 1).apply()
            updateCoinDisplay()
            Toast.makeText(this, "🔓 코인 1개 사용! 잠금 해제됩니다.", Toast.LENGTH_SHORT).show()
            finishAffinity() // 모든 액티비티 종료
        } else {
            Toast.makeText(this, "⚠️ 코인이 부족합니다. 해제 불가!", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔹 코인 표시 업데이트
    private fun updateCoinDisplay() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coin_count", 3)
        tvCoinCount.text = coins.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockJob?.cancel()
    }
}
