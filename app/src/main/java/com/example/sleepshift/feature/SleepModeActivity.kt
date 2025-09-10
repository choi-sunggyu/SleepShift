package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.DailyRecord
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SleepModeActivity : AppCompatActivity() {

    private lateinit var tvRemain: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_mode)
        tvRemain = findViewById(R.id.tvRemain)

        lifecycleScope.launch {
            val repo = SleepRepository(this@SleepModeActivity)
            val settings = repo.getSettings() ?: return@launch
            val today = KstTime.todayYmd()

            // 오늘 기록 생성 success=false
            repo.setDailyRecord(today, DailyRecord(lockStartTime = System.currentTimeMillis(), success = false))

            val wakeEpoch = KstTime.nextOccurrenceEpoch(settings.goalWakeTime)

            while (true) {
                val left = ((wakeEpoch - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                tvRemain.text = formatHMS(left)
                if (left <= 0) {
                    startActivity(android.content.Intent(this@SleepModeActivity, MorningGateActivity::class.java))
                    finish()
                    return@launch
                }
                delay(1000)
            }
        }
    }

    private fun formatHMS(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
