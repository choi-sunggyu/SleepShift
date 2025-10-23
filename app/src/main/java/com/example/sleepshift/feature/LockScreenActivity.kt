package com.example.sleepshift.feature.lockscreen

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.util.NightRoutineConstants

class LockScreenActivity : AppCompatActivity() {

    private lateinit var tvGoodNightMessage: TextView
    private lateinit var tvWakeTimeMessage: TextView
    private lateinit var tvUnlockHint: TextView

    private val UNLOCK_COST = NightRoutineConstants.UNLOCK_COST
    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        tvGoodNightMessage = findViewById(R.id.tvGoodNightMessage)
        tvWakeTimeMessage = findViewById(R.id.tvWakeTimeMessage)
        tvUnlockHint = findViewById(R.id.tvUnlockHint)

        setupWindowFlags()
        updateAllDisplays()
        setupUnlockListener()
    }

    /**
     * 잠금 화면 윈도우 설정 (홈/오버뷰 차단 유지)
     */
    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 홈/오버뷰 차단
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    /**
     * SharedPreferences에서 사용자 이름과 알람 시간 가져와서 UI 업데이트
     */
    private fun updateAllDisplays() {
        val prefs = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "사용자") ?: "사용자"
        tvGoodNightMessage.text = "${userName}님 잘자요!"

        val alarmHour = prefs.getInt("alarm_hour", 7)
        val alarmMinute = prefs.getInt("alarm_minute", 0)
        val formattedTime = String.format("%02d:%02d", alarmHour, alarmMinute)
        tvWakeTimeMessage.text = "${formattedTime}에 깨워드릴게요"

        tvUnlockHint.text = "해제를 원하시면 3초간 누르세요 (코인 ${UNLOCK_COST}개 소모)"
    }

    /**
     * 3초간 길게 눌러 잠금 해제
     */
    private fun setupUnlockListener() {
        tvUnlockHint.setOnLongClickListener {
            if (!isUnlocked) {
                unlockScreen()
            }
            true
        }
    }

    private fun unlockScreen() {
        isUnlocked = true
        val lockPrefs = getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
        lockPrefs.edit().putBoolean("isLocked", false).apply()

        Toast.makeText(this, "잠금 해제!", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * 뒤로가기, 홈, 메뉴 버튼 막기
     */
    override fun onBackPressed() {
        // Do nothing
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllDisplays() // 나이트루틴에서 변경한 알람 시간 반영
    }
}
