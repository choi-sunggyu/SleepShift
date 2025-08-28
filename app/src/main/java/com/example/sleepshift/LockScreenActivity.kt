package com.example.sleepshift

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.alarm.AlarmScheduler
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class LockScreenActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        if (Build.VERSION.SDK_INT >= 27) {
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

        val tv = findViewById<TextView>(R.id.tvCountdown)
        val btn = findViewById<Button>(R.id.btnGoodMorning)
        btn.isEnabled = false

        val ds = DataStoreManager(this)

        lifecycleScope.launch {
            val minutes = ds.targetSleepDurationMinutes.first() ?: (8 * 60)
            startCountdown(minutes * 60L * 1000L, tv, btn)
        }

        btn.setOnClickListener {
            lifecycleScope.launch {
                handleSuccessAndReschedule(DataStoreManager(this@LockScreenActivity))
                finish()
            }
        }
    }

    private fun startCountdown(totalMillis: Long, tv: TextView, btn: Button) {
        timer?.cancel()
        timer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(ms: Long) {
                val h = ms / 3600000
                val m = (ms % 3600000) / 60000
                val s = (ms % 60000) / 1000
                tv.text = String.format("%02d:%02d:%02d", h, m, s)
            }

            override fun onFinish() {
                tv.text = "00:00:00"
                btn.isEnabled = true
                btn.text = "Good Morning!"
            }
        }.start()
    }

    override fun onBackPressed() {
        // 뒤로가기 UX 방지
    }

    private suspend fun handleSuccessAndReschedule(ds: DataStoreManager) {
        val streak = ds.consecutiveSuccessDays.first() + 1
        ds.setConsecutiveSuccessDays(streak)

        val newShift = when {
            streak >= 7 -> 60
            streak >= 4 -> 50
            streak >= 2 -> 40
            else -> 30
        }
        ds.setStepMinutes(newShift)

        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val progressS = ds.progressBedtime.first() ?: return
        val targetS = ds.targetBedtime.first() ?: return
        val progress = TimeUtils.parseHHmm(progressS)
        val target = TimeUtils.parseHHmm(targetS)
        val newProgress = progress.minusMinutes(newShift.toLong())
        val clamped = if (newProgress.isBefore(target)) target else newProgress
        ds.setProgressBedtime(clamped.format(fmt))

        ds.setSleepStarted(false)
        ds.setSleepStartedAt(null)
        AlarmScheduler.scheduleNextBedtimeAlarm(this@LockScreenActivity)
    }
}
