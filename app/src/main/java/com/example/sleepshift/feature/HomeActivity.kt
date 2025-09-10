package com.example.sleepshift.feature

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.*
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private var ticker: Job? = null
    private var preShown = false

    private lateinit var tvSched: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var bar: ProgressBar
    private lateinit var tvPercent: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvShift: TextView
    private lateinit var tvTarget: TextView
    private lateinit var btnPlus30: Button
    private lateinit var btnEarly: Button
    private lateinit var btnReport: Button
    private lateinit var btnReset: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvSched = findViewById(R.id.tvScheduled)
        tvCountdown = findViewById(R.id.tvCountdown)
        bar = findViewById(R.id.progressBar)
        tvPercent = findViewById(R.id.tvPercent)
        tvStreak = findViewById(R.id.tvStreak)
        tvShift = findViewById(R.id.tvShift)
        tvTarget = findViewById(R.id.tvTarget)
        btnPlus30 = findViewById(R.id.btnPlus30)
        btnEarly = findViewById(R.id.btnEarly)
        btnReport = findViewById(R.id.btnReport)
        btnReset = findViewById(R.id.btnReset)

        lifecycleScope.launch {
            val repo = SleepRepository(this@HomeActivity)
            val settings = repo.getSettings() ?: run {
                startActivity(android.content.Intent(this@HomeActivity, InitSurveyActivity::class.java))
                finish()
                return@launch
            }
            var progress = repo.getProgress() ?: return@launch

            // 날짜 바뀌었으면 어제 기록 토대로 업데이트
            if (progress.lastProgressUpdate != KstTime.todayYmd()) {
                progress = updateProgressFromYesterday(progress, settings, repo)
                repo.saveProgress(progress)
            }

            // UI 바인딩
            tvSched.text = progress.scheduledBedtime
            tvStreak.text = "${progress.consecutiveSuccessDays} days"
            tvShift.text = "${progress.dailyShiftMinutes} min"
            tvTarget.text = settings.targetBedtime

            // 진행률 %
            val percent = calcPercent(settings.avgBedTime, settings.targetBedtime, progress.scheduledBedtime)
            bar.progress = percent
            tvPercent.text = "$percent%"

            // 액션
            btnPlus30.setOnClickListener {
                lifecycleScope.launch {
                    val p = repo.getProgress() ?: return@launch
                    val newSched = KstTime.addMinutes(p.scheduledBedtime, 30)
                    val updated = p.copy(scheduledBedtime = newSched, lastProgressUpdate = KstTime.todayYmd())
                    repo.saveProgress(updated)
                    tvSched.text = updated.scheduledBedtime
                    preShown = false // 다시 30분 전 모달 허용
                }
            }
            btnEarly.setOnClickListener {
                startActivity(android.content.Intent(this@HomeActivity, CheckinActivity::class.java))
            }
            btnReport.setOnClickListener {
                startActivity(android.content.Intent(this@HomeActivity, ReportActivity::class.java))
            }
            btnReset.setOnClickListener {
                // 초기화: settings/progress/기록 다 지움
                getSharedPreferences("dummy", MODE_PRIVATE).edit().clear().apply() // no-op placeholder
                // 실제로는 DataStore라서 clear가 복잡; 간단히 localStorage 유사 효과 위해 앱 데이터 재설정 유도
                // 여기서는 settings만 제거 → 다시 Init으로
                lifecycleScope.launch {
                    repo.saveSettings(
                        SleepSettings(
                            avgBedTime="", avgWakeTime="", goalWakeTime="",
                            goalSleepDuration="", targetBedtime="",
                            morningGoal="", reasonToChange=""
                        )
                    )
                    // 위 빈 값 저장 대신, 진짜 reset을 원하면 앱 데이터 삭제나 별도 clear API를 구현해야 함.
                    // 편의상 Init으로 보내며 사용자는 다시 입력.
                    startActivity(android.content.Intent(this@HomeActivity, InitSurveyActivity::class.java))
                    finish()
                }
            }

            // 카운트다운 틱
            ticker?.cancel()
            ticker = lifecycleScope.launch {
                while (true) {
                    val sec = KstTime.secondsUntil(progress.scheduledBedtime)
                    tvCountdown.text = formatHMS(sec)

                    if (sec <= 1800 && !preShown) {
                        preShown = true
                        AlertDialog.Builder(this@HomeActivity)
                            .setTitle("취침 준비 알림")
                            .setMessage("30분 뒤에 잘 시간이에요. 가볍게 준비를 시작해요.")
                            .setPositiveButton("확인") { d, _ ->
                                d.dismiss()
                                startActivity(android.content.Intent(this@HomeActivity, CheckinActivity::class.java))
                            }
                            .setNegativeButton("닫기", null)
                            .show()
                    }
                    delay(1000)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ticker?.cancel()
    }

    private fun updateProgressFromYesterday(
        p: SleepProgress,
        s: SleepSettings,
        repo: SleepRepository
    ): SleepProgress {
        val y = KstTime.yesterdayYmd()
        // runBlocking 없이 동기 호출을 피하기 위해 이미 호출측이 suspend 컨텍스트에서 실행
        var streak = p.consecutiveSuccessDays
        var shift = p.dailyShiftMinutes
        var scheduled = p.scheduledBedtime

        val yRec = runCatching { kotlinx.coroutines.runBlocking { repo.getDailyRecord(y) } }.getOrNull()

        if (yRec != null) {
            if (yRec.success) {
                streak += 1
                shift = when {
                    streak >= 7 -> 60
                    streak >= 4 -> 50
                    streak >= 2 -> 40
                    else -> 30
                }
                // scheduled = max(target, scheduled - shift)
                val cand = KstTime.addMinutes(scheduled, -shift)
                scheduled = maxOfHHmm(cand, s.targetBedtime)
            } else {
                streak = 0
                shift = 30
                // scheduled 유지
            }
        }
        return p.copy(
            scheduledBedtime = scheduled,
            consecutiveSuccessDays = streak,
            dailyShiftMinutes = shift,
            lastProgressUpdate = KstTime.todayYmd()
        )
    }

    private fun maxOfHHmm(a: String, b: String): String {
        val am = KstTime.hhmmToMinutes(a)
        val bm = KstTime.hhmmToMinutes(b)
        return if (am < bm) b else a
    }

    private fun calcPercent(from: String, to: String, cur: String): Int {
        val f = KstTime.hhmmToMinutes(from)
        val t = KstTime.hhmmToMinutes(to)
        val c = KstTime.hhmmToMinutes(cur)
        val total = (f - t + 1440) % 1440
        val done = (f - c + 1440) % 1440
        if (total == 0) return 100
        return ((done * 100.0) / total).toInt().coerceIn(0, 100)
    }

    private fun formatHMS(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
