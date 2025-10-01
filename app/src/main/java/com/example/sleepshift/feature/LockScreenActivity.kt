package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.feature.home.HomeActivity

class LockScreenActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // Views
    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvCoinCount: TextView
    private lateinit var btnUnlock: LinearLayout
    private lateinit var imgLockIcon: ImageView
    private lateinit var tvUnlockText: TextView
    private lateinit var tvUnlockHint: TextView
    private lateinit var countdownSection: LinearLayout
    private lateinit var tvCountdown: TextView

    // 카운트다운 관련
    private var countDownTimer: CountDownTimer? = null
    private var isLongPressing = false
    private val handler = Handler(Looper.getMainLooper())

    // 코인 사용 관련
    private val UNLOCK_COST = 1 // 잠금 해제에 필요한 코인

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        // 화면을 항상 켜진 상태로 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 화면 밝기를 최소로 설정
        setScreenBrightness(0.1f)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        initViews()
        setupUI()
        setupUnlockButton()
    }

    private fun initViews() {
        tvGoodNightMessage = findViewById(R.id.tvGoodNightMessage)
        tvCoinCount = findViewById(R.id.tvCoinCount)
        btnUnlock = findViewById(R.id.btnUnlock)
        imgLockIcon = findViewById(R.id.imgLockIcon)
        tvUnlockText = findViewById(R.id.tvUnlockText)
        tvUnlockHint = findViewById(R.id.tvUnlockHint)
        countdownSection = findViewById(R.id.countdownSection)
        tvCountdown = findViewById(R.id.tvCountdown)
    }

    private fun setupUI() {
        // 사용자 이름 표시
        val userName = sharedPreferences.getString("user_name", "성규")
        tvGoodNightMessage.text = "${userName}님 잘자요!"

        // 발바닥 코인 개수 표시
        updateCoinDisplay()

        // 알람 시간 표시 - 수정된 부분
        val alarmHour = sharedPreferences.getInt("alarm_hour", 7)
        val alarmMinute = sharedPreferences.getInt("alarm_minute", 0)
        val alarmTime = String.format("%02d:%02d", alarmHour, alarmMinute)

        val wakeTimeText = findViewById<TextView>(R.id.tvWakeTimeMessage)
        wakeTimeText?.text = "${alarmTime}에 깨워드릴게요"

        // 코인 부족시 버튼 비활성화 처리
        checkUnlockAvailability()
    }

    private fun updateCoinDisplay() {
        val coinCount = getCurrentCoins()
        tvCoinCount.text = coinCount.toString()
    }

    private fun getCurrentCoins(): Int {
        return sharedPreferences.getInt("paw_coin_count", 0)
    }

    private fun checkUnlockAvailability() {
        val currentCoins = getCurrentCoins()

        if (currentCoins < UNLOCK_COST) {
            // 코인 부족시 버튼 비활성화
            btnUnlock.alpha = 0.5f
            tvUnlockHint.text = "곰젤리 (${UNLOCK_COST}개 필요)"
            btnUnlock.isEnabled = false
        } else {
            // 코인 충분시 버튼 활성화
            btnUnlock.alpha = 1.0f
            tvUnlockHint.text = "해제를 원하시면 3초간 누르세요"
            btnUnlock.isEnabled = true
        }
    }

    private fun setupUnlockButton() {
        btnUnlock.setOnTouchListener { _, event ->
            // 코인이 부족하면 터치 무시
            if (getCurrentCoins() < UNLOCK_COST) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showInsufficientCoinsMessage()
                }
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startLongPressCountdown()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPressCountdown()
                    true
                }
                else -> false
            }
        }
    }

    private fun showInsufficientCoinsMessage() {
        val currentCoins = getCurrentCoins()
        val needed = UNLOCK_COST - currentCoins
        Toast.makeText(
            this,
            "발바닥 코인이 부족합니다!\n현재: ${currentCoins}개, 필요: ${UNLOCK_COST}개\n(${needed}개 더 필요)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startLongPressCountdown() {
        if (isLongPressing) return

        isLongPressing = true

        // UI 변경
        countdownSection.visibility = View.VISIBLE
        tvUnlockHint.visibility = View.GONE

        // 3초 카운트다운 시작
        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000 + 1).toInt()
                tvCountdown.text = secondsRemaining.toString()

                // 자물쇠 아이콘을 숫자로 변경
                updateLockIcon(secondsRemaining)

                // 버튼 스케일 애니메이션
                btnUnlock.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(200)
                    .start()
            }

            override fun onFinish() {
                // 카운트다운 완료 - 잠금 해제
                unlockScreen()
            }
        }.start()
    }

    private fun cancelLongPressCountdown() {
        if (!isLongPressing) return

        isLongPressing = false

        // 카운트다운 취소
        countDownTimer?.cancel()
        countDownTimer = null

        // UI 복원
        countdownSection.visibility = View.GONE
        tvUnlockHint.visibility = View.VISIBLE
        updateLockIcon(0) // 원래 자물쇠 아이콘으로 복원

        // 버튼 스케일 복원
        btnUnlock.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .start()
    }

    private fun updateLockIcon(countdown: Int) {
        when (countdown) {
            3 -> {
                tvUnlockText.text = "3"
                tvUnlockText.textSize = 24f
            }
            2 -> {
                tvUnlockText.text = "2"
                tvUnlockText.textSize = 24f
            }
            1 -> {
                tvUnlockText.text = "1"
                tvUnlockText.textSize = 24f
            }
            else -> {
                // 원래 상태로 복원
                tvUnlockText.text = "잠금해제"
                tvUnlockText.textSize = 18f
            }
        }
    }

    private fun unlockScreen() {
        // 코인 사용 처리
        val success = usePawCoins(UNLOCK_COST)

        if (!success) {
            // 코인 사용 실패 (코인 부족)
            Toast.makeText(this, "코인이 부족하여 잠금 해제를 할 수 없습니다", Toast.LENGTH_LONG).show()
            cancelLongPressCountdown()
            checkUnlockAvailability() // UI 업데이트
            return
        }

        // 성공 메시지
        Toast.makeText(this, "잠금이 해제되었습니다! (코인 ${UNLOCK_COST}개 사용)", Toast.LENGTH_LONG).show()

        // 화면 밝기 복원
        setScreenBrightness(-1f) // 시스템 기본값으로 복원

        // 잠금 해제 기록 저장
        recordUnlockTime()

        // 홈 화면으로 이동
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun usePawCoins(amount: Int): Boolean {
        val currentCoins = getCurrentCoins()

        if (currentCoins >= amount) {
            val newCoins = currentCoins - amount

            sharedPreferences.edit()
                .putInt("paw_coin_count", newCoins)
                .apply()

            android.util.Log.d("LockScreen", "코인 사용: ${amount}개, 잔액: ${newCoins}개")
            return true
        }

        android.util.Log.d("LockScreen", "코인 부족: 현재 ${currentCoins}개, 필요 ${amount}개")
        return false
    }

    private fun recordUnlockTime() {
        // 잠금 해제 시간 기록
        sharedPreferences.edit()
            .putLong("last_unlock_time", System.currentTimeMillis())
            .putInt("daily_unlock_count",
                sharedPreferences.getInt("daily_unlock_count", 0) + 1)
            .apply()
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 뒤로가기 버튼 비활성화 (잠금 화면이므로)
        val currentCoins = getCurrentCoins()
        if (currentCoins < UNLOCK_COST) {
            Toast.makeText(this, "코인이 부족합니다. 알람을 해제하여 코인을 획득하세요!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "잠금 해제 버튼을 길게 눌러주세요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀시 코인 상태 업데이트
        updateCoinDisplay()
        checkUnlockAvailability()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 카운트다운 타이머 정리
        countDownTimer?.cancel()
        countDownTimer = null

        // 화면 설정 플래그 제거
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}