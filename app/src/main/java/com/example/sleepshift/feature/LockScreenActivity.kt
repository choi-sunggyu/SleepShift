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
        // 사용자 이름 표시 (SharedPreferences에서 가져오기 또는 기본값)
        val userName = sharedPreferences.getString("user_name", "성규")
        tvGoodNightMessage.text = "${userName}님 잘자요!"

        // 발바닥 코인 개수 표시
        val coinCount = sharedPreferences.getInt("paw_coin_count", 130)
        tvCoinCount.text = coinCount.toString()
    }

    private fun setupUnlockButton() {
        btnUnlock.setOnTouchListener { _, event ->
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

        Toast.makeText(this, "잠금 해제가 취소되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun updateLockIcon(countdown: Int) {
        when (countdown) {
            3 -> {
                // 자물쇠 아이콘을 3으로 변경하거나 특별한 표시
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
        // 성공 메시지
        Toast.makeText(this, "잠금이 해제되었습니다! 좋은 밤 되세요", Toast.LENGTH_LONG).show()

        // 화면 밝기 복원
        setScreenBrightness(-1f) // 시스템 기본값으로 복원

        // 발바닥 코인 지급 (수면 완료 보상)
        giveWakeUpReward()

        // 홈 화면으로 이동
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun giveWakeUpReward() {
        // 기상 보상으로 발바닥 코인 지급
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 130)
        val newCoins = currentCoins + 5 // 기상 보상 5코인

        sharedPreferences.edit()
            .putInt("paw_coin_count", newCoins)
            .putLong("last_wakeup_time", System.currentTimeMillis())
            .apply()

        Toast.makeText(this, "+5 발바닥 코인 (기상 보상)", Toast.LENGTH_SHORT).show()
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    override fun onBackPressed() {
        // 뒤로가기 버튼 비활성화 (잠금 화면이므로)
        Toast.makeText(this, "잠금 해제 버튼을 길게 눌러주세요", Toast.LENGTH_SHORT).show()
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