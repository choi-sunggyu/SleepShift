package com.example.sleepshift

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.service.AccessibilityLockService
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
    private val unlockDuration = 3000L // 3초간 누르면 해제

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val UNLOCK_COST = 15 // ⭐ 잠금 해제 비용 15개
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "✅ LockScreenActivity onCreate")

        setContentView(R.layout.activity_lock_screen)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        initViews()
        setupWindowFlags()
        updateAllDisplays()
        setupUnlockButton()

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
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

    private fun setupWindowFlags() {
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
    }

    /**
     * ⭐ 모든 화면 정보 업데이트
     */
    private fun updateAllDisplays() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // 1. 사용자 이름 업데이트
        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        tvGoodNightMessage.text = "${userName}님 잘자요!"
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "사용자 이름: $userName")

        // 2. ⭐⭐⭐ 알람 시간 업데이트 (완전 수정)
        // 먼저 alarm_hour 확인
        val alarmHour = prefs.getInt("alarm_hour", -1)

        if (alarmHour != -1) {
            // alarm_hour가 있으면 사용
            tvWakeTimeMessage.text = "${alarmHour}시에 깨워드릴게요"
            Log.d(TAG, "알람 시간 (alarm_hour): $alarmHour")
        } else {
            // 없으면 alarm_time에서 파싱
            val alarmTime = prefs.getString("alarm_time", "07:00") ?: "07:00"
            val hour = alarmTime.split(":").getOrNull(0)?.toIntOrNull() ?: 7
            tvWakeTimeMessage.text = "${hour}시에 깨워드릴게요"
            Log.d(TAG, "알람 시간 (alarm_time): $alarmTime → ${hour}시")
        }

        // SharedPreferences 전체 내용 로그 (디버깅용)
        Log.d(TAG, "SharedPreferences 내용:")
        prefs.all.forEach { (key, value) ->
            if (key.contains("alarm", ignoreCase = true)) {
                Log.d(TAG, "  - $key = $value")
            }
        }

        // 3. 코인 개수 업데이트
        updateCoinDisplay()

        // 4. 잠금 해제 안내 메시지 업데이트
        tvUnlockHint.text = "해제를 원하시면 3초간 누르세요 (코인 ${UNLOCK_COST}개 소모)"

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * 잠금 해제 버튼 설정
     */
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

    /**
     * 카운트다운 시작
     */
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

    /**
     * 카운트다운 중지
     */
    private fun stopCountdown() {
        countdownSection.visibility = View.GONE
        countdownJob?.cancel()
    }

    // 🔹 홈, 메뉴, 백버튼 차단
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BACK -> {
                Log.d(TAG, "버튼 차단: $keyCode")
                Toast.makeText(this, "잠금 화면에서는 이 버튼을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 🔹 최근 앱 목록 진입 차단
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // ⭐ 잠금 해제 중에는 포커스 체크 무시
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        val isLocked = lockPrefs.getBoolean("isLocked", false)

        if (!hasFocus && isLocked) {
            Log.w(TAG, "⚠️ 포커스 잃음 - 다시 가져오기")
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
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
        val coins = prefs.getInt("paw_coin_count", 0)

        Log.d(TAG, "현재 코인: $coins, 필요 코인: $UNLOCK_COST")

        if (coins >= UNLOCK_COST) {
            // ⭐ 15개 차감
            val newCoinCount = coins - UNLOCK_COST
            prefs.edit().putInt("paw_coin_count", newCoinCount).apply()

            // ✅ 1단계: 잠금 상태를 먼저 해제 (commit으로 즉시 반영)
            val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
            lockPrefs.edit().putBoolean("isLocked", false).commit()

            Log.d(TAG, "✅ 잠금 상태 해제 완료")
            Log.d(TAG, "✅ 코인 ${UNLOCK_COST}개 차감 (잔여: ${newCoinCount}개)")

            Toast.makeText(
                this,
                "🔓 잠금 해제! 홈 화면으로 이동합니다...",
                Toast.LENGTH_SHORT
            ).show()

            // ✅ 2단계: 약간의 딜레이 후 HomeActivity로 이동
            CoroutineScope(Dispatchers.Main).launch {
                delay(300) // 300ms 대기 - SharedPreferences 반영 및 AccessibilityService가 감지할 시간

                Log.d(TAG, "✅ HomeActivity로 이동 시작")

                val intent = Intent(this@LockScreenActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

                Log.d(TAG, "✅ LockScreenActivity 종료")
            }

        } else {
            Log.w(TAG, "⚠️ 코인 부족 - 해제 불가 (보유: $coins, 필요: $UNLOCK_COST)")
            Toast.makeText(
                this,
                "⚠️ 코인이 부족합니다!\n필요: ${UNLOCK_COST}개 / 보유: ${coins}개",
                Toast.LENGTH_LONG
            ).show()

            // 코인 부족 시 애니메이션 효과
            imgLockIcon.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    imgLockIcon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    // 🔹 코인 표시 업데이트
    private fun updateCoinDisplay() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("paw_coin_count", 0)
        tvCoinCount.text = coins.toString()
        Log.d(TAG, "코인 개수 업데이트: $coins")
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 최신 정보로 업데이트
        updateAllDisplays()
        Log.d(TAG, "onResume - 화면 정보 업데이트")
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockJob?.cancel()
        countdownJob?.cancel()
        Log.d(TAG, "LockScreenActivity onDestroy")
    }
}