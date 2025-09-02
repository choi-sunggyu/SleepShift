package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.launch

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val tv = findViewById<TextView>(R.id.tvReport)

        lifecycleScope.launch {
            val repo = SleepRepository(this@ReportActivity)
            val y = KstTime.yesterdayYmd()
            val today = KstTime.todayYmd()

            val yDaily = repo.getDailyRecord(y)
            val yCheck = repo.getCheckinRecord(y)
            val prog = repo.getProgress()
            val morning = repo.getMorningRoutine()

            val actual = if (yDaily != null) {
                // 대략: lockStartTime ~ 목표 기상(다음 발생) 간의 시간
                val settings = repo.getSettings()
                if (settings != null) {
                    val wakeEpoch = KstTime.nextOccurrenceEpoch(settings.goalWakeTime)
                    val durSec = ((wakeEpoch - yDaily.lockStartTime) / 1000).coerceAtLeast(0)
                    "%02d:%02d".format(durSec / 3600, (durSec % 3600) / 60)
                } else "-"
            } else "-"

            val report = buildString {
                appendLine("■ 일간 리포트 (대상: $y)")
                appendLine("기상 성공 여부: ${yDaily?.let { if (it.success) "성공" else "실패" } ?: "-"}")
                appendLine("실제 수면 시간(대략): $actual")
                appendLine("어제 감정: ${yCheck?.emotion ?: "-"}")
                appendLine("어제 목표 달성: ${yCheck?.let { if (it.goalAchieved) "예" else "아니오" } ?: "-"}")
                appendLine("현재 연속 성공 일수: ${prog?.consecutiveSuccessDays ?: "-"}")
                appendLine()
                appendLine("■ 오늘($today)의 핵심 과업: ${if (morning?.date == today) morning.key_task else "-"}")
            }
            tv.text = report
        }
    }
}
