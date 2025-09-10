package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.max

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val tv = findViewById<TextView>(R.id.tvReport)

        lifecycleScope.launch {
            val repo = SleepRepository(this@ReportActivity)
            val settings = repo.getSettings()
            val y = KstTime.yesterdayYmd()
            val today = KstTime.todayYmd()

            val yDaily = repo.getDailyRecord(y)
            val yCheck = repo.getCheckinRecord(y)
            val prog = repo.getProgress()
            val morning = repo.getMorningRoutine()

            val actualDaily = if (yDaily != null && settings != null) {
                val durSec = estimateSleepSecondsFrom(yDaily.lockStartTime, settings.goalWakeTime)
                formatHM(durSec)
            } else "-"

            // ===== 월간 리포트(이번 달 1일 ~ 어제) =====
            val monthly = buildMonthlyReport(repo, settings)

            val report = buildString {
                appendLine("■ 일간 리포트 (대상: $y)")
                appendLine("기상 성공 여부: ${yDaily?.let { if (it.success) "성공" else "실패" } ?: "-"}")
                appendLine("실제 수면 시간(대략): $actualDaily")
                appendLine("어제 감정: ${yCheck?.emotion ?: "-"}")
                appendLine("어제 목표 달성: ${yCheck?.let { if (it.goalAchieved) "예" else "아니오" } ?: "-"}")
                appendLine("현재 연속 성공 일수: ${prog?.consecutiveSuccessDays ?: "-"}")
                appendLine()
                appendLine("■ 오늘($today)의 핵심 과업: ${if (morning?.date == today) morning.key_task else "-"}")
                appendLine()
                appendLine(monthly)
            }
            tv.text = report
        }
    }

    // ---- 월간 리포트 구성 ----
    private suspend fun buildMonthlyReport(
        repo: SleepRepository,
        settings: com.example.sleepshift.data.SleepSettings?
    ): String {
        val zone = ZoneId.of("Asia/Seoul")
        val now = LocalDate.now(zone)
        val monthStart = now.withDayOfMonth(1)
        val monthEnd = now.minusDays(1) // 안정적으로 어제까지 집계

        if (monthEnd.isBefore(monthStart)) {
            return "■ 월간 리포트 (${now.year}-${"%02d".format(now.monthValue)})\n집계할 데이터가 없습니다."
        }

        var daysWithRecord = 0
        var successDays = 0
        var longestStreak = 0
        var runningStreak = 0
        var sumSec = 0L
        var cntSec = 0

        val emotionCounts = mutableMapOf<String, Int>()
        var goalsAchievedCount = 0
        var goalsCheckedCount = 0

        val fmtYMD = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        var d = monthStart
        while (!d.isAfter(monthEnd)) {
            val key = d.format(fmtYMD)

            val rec = repo.getDailyRecord(key)
            if (rec != null) {
                daysWithRecord++
                if (rec.success) {
                    successDays++
                    runningStreak++
                    longestStreak = max(longestStreak, runningStreak)
                } else {
                    runningStreak = 0
                }

                if (settings != null) {
                    val durSec = estimateSleepSecondsFrom(rec.lockStartTime, settings.goalWakeTime, d)
                    if (durSec > 0) {
                        sumSec += durSec
                        cntSec++
                    }
                }
            } else {
                // 기록 없으면 연속 성공 끊김으로 간주하지 않음(선택사항)
            }

            val check = repo.getCheckinRecord(key)
            if (check != null) {
                emotionCounts[check.emotion] = (emotionCounts[check.emotion] ?: 0) + 1
                goalsCheckedCount++
                if (check.goalAchieved) goalsAchievedCount++
            }

            d = d.plusDays(1)
        }

        val successRate =
            if (daysWithRecord > 0) (successDays * 100.0 / daysWithRecord).let { String.format("%.1f", it) }
            else "-"

        val avgSleep =
            if (cntSec > 0) formatHM(sumSec / cntSec) else "-"

        val topEmotion = emotionCounts.entries.maxByOrNull { it.value }?.let { "${it.key} (${it.value})" } ?: "-"

        val monthLabel = "${now.year}-${"%02d".format(now.monthValue)}"

        return buildString {
            appendLine("■ 월간 리포트 ($monthLabel)")
            appendLine("집계 기간: ${monthStart.format(fmtYMD)} ~ ${monthEnd.format(fmtYMD)}")
            appendLine("기록 일수: ${daysWithRecord}일")
            appendLine("성공 일수: ${successDays}일")
            appendLine("성공률: $successRate%")
            appendLine("최대 연속 성공: ${if (longestStreak == 0) "-" else "${longestStreak}일"}")
            appendLine("평균 수면 시간(대략): $avgSleep")
            appendLine("가장 자주 선택된 감정: $topEmotion")
            appendLine("목표 달성 체크: ${if (goalsCheckedCount == 0) "-" else "$goalsAchievedCount/$goalsCheckedCount"}")
        }
    }

    /**
     * lockStartTime(수면 모드 시작) 기준으로 '다음 발생하는 목표 기상 시각'까지의 대략적인 수면 시간을 초 단위로 반환.
     * 기본적으로 lockStartTime이 속한 날짜를 기준으로 목표 기상시각을 계산.
     */
    private fun estimateSleepSecondsFrom(lockStartMillis: Long, goalWakeHHmm: String, startDate: LocalDate? = null): Long {
        val zone = ZoneId.of("Asia/Seoul")
        val startZdt = Instant.ofEpochMilli(lockStartMillis).atZone(zone)
        val baseDate = startDate ?: startZdt.toLocalDate()
        val goalTime = LocalTime.parse(goalWakeHHmm)
        var wakeZdt = baseDate.atTime(goalTime).atZone(zone)
        if (!wakeZdt.isAfter(startZdt)) {
            wakeZdt = wakeZdt.plusDays(1)
        }
        val diff = (wakeZdt.toInstant().toEpochMilli() - lockStartMillis) / 1000
        return if (diff > 0) diff else 0
    }

    private fun formatHM(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return "%02d:%02d".format(h, m)
    }
}
