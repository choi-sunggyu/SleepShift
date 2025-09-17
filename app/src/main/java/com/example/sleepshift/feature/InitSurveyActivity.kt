package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.data.SleepSettings
import com.example.sleepshift.util.KstTime
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class InitSurveyActivity : AppCompatActivity() {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 기본값
    private var avgBed: LocalTime = LocalTime.of(4, 0)
    private var avgWake: LocalTime = LocalTime.of(13, 0)
    private var goalWake: LocalTime = LocalTime.of(7, 0)
    private var sleepDurMinutes: Int = 8 * 60 // 8시간

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init_survey)

        val btnAvgBed = findViewById<Button>(R.id.btnAvgBed)
        val btnAvgWake = findViewById<Button>(R.id.btnAvgWake)
        val btnGoalWake = findViewById<Button>(R.id.btnGoalWake)
        val btnGoalSleepDur = findViewById<Button>(R.id.btnGoalSleepDur)
        val tvAvgSleepNow = findViewById<TextView>(R.id.tvAvgSleepNow)
        val tvComputedTarget = findViewById<TextView>(R.id.tvComputedTarget)
        val btnStart = findViewById<Button>(R.id.btnStartJourney)

        // 초기 라벨
        btnAvgBed.text = "선택 (${avgBed.format(hhmm)})"
        btnAvgWake.text = "선택 (${avgWake.format(hhmm)})"
        btnGoalWake.text = "선택 (${goalWake.format(hhmm)})"
        btnGoalSleepDur.text = "선택 (${minutesToHHmm(sleepDurMinutes)})"

        // 실시간 프리뷰
        fun refreshPreview() {
            val avgSleep = computeSleepMinutes(avgBed, avgWake)
            val targetBed = goalWake.minusMinutes(sleepDurMinutes.toLong()).format(hhmm)
            tvAvgSleepNow.text = "현재 평균 수면 시간: ${minutesToHHmm(avgSleep)}"
            tvComputedTarget.text = "계산된 목표 취침 시간: $targetBed"
        }
        refreshPreview()

        // ====== 다이얼형 피커 연결 ======
        btnAvgBed.setOnClickListener {
            showTimeDial(avgBed) { h, m ->
                avgBed = LocalTime.of(h, m)
                btnAvgBed.text = "선택 (${avgBed.format(hhmm)})"
                refreshPreview()
            }
        }

        btnAvgWake.setOnClickListener {
            showTimeDial(avgWake) { h, m ->
                avgWake = LocalTime.of(h, m)
                btnAvgWake.text = "선택 (${avgWake.format(hhmm)})"
                refreshPreview()
            }
        }

        btnGoalWake.setOnClickListener {
            showTimeDial(goalWake) { h, m ->
                goalWake = LocalTime.of(h, m)
                btnGoalWake.text = "선택 (${goalWake.format(hhmm)})"
                refreshPreview()
            }
        }

        btnGoalSleepDur.setOnClickListener {
            showDurationWheel(
                initialHours = sleepDurMinutes / 60,
                initialMinutes = sleepDurMinutes % 60
            ) { h, m ->
                // 유효 범위: 4~12시간 권장 (필요 시 강제)
                val total = h * 60 + m
                if (h < 4 || h > 12) {
                    Toast.makeText(this, "권장 범위는 4~12시간입니다.", Toast.LENGTH_SHORT).show()
                }
                sleepDurMinutes = total
                btnGoalSleepDur.text = "선택 (${minutesToHHmm(sleepDurMinutes)})"
                refreshPreview()
            }
        }

        // ====== 저장 ======
        btnStart.setOnClickListener {
            lifecycleScope.launch {
                val repo = SleepRepository(this@InitSurveyActivity)

                val targetBedtime = goalWake.minusMinutes(sleepDurMinutes.toLong())
                // 초기 scheduled = max(target, avgBed - 20분)
                var scheduled = avgBed.minusMinutes(20)
                if (isBefore(scheduled, targetBedtime)) {
                    scheduled = targetBedtime
                }

                val settings = SleepSettings(
                    avgBedTime = avgBed.format(hhmm),
                    avgWakeTime = avgWake.format(hhmm),
                    goalWakeTime = goalWake.format(hhmm),
                    goalSleepDuration = minutesToHHmm(sleepDurMinutes),
                    targetBedtime = targetBedtime.format(hhmm),
                    morningGoal = "",
                    reasonToChange = ""
                )
                val progress = com.example.sleepshift.data.SleepProgress(
                    scheduledBedtime = scheduled.format(hhmm),
                    consecutiveSuccessDays = 0,
                    dailyShiftMinutes = 30,
                    lastProgressUpdate = KstTime.todayYmd()
                )

                repo.saveSettings(settings)
                repo.saveProgress(progress)

                Toast.makeText(this@InitSurveyActivity, "설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
                // 홈으로 이동
                startActivity(android.content.Intent(this@InitSurveyActivity, HomeActivity::class.java))
                finish()
            }
        }
    }

    // ===== Helpers =====

    private fun showTimeDial(initial: LocalTime, onPicked: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .setTitleText("시간 선택")
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "time_dial")
    }

    /**
     * 목표 수면시간 다이얼(휠): 시간(4~12), 분(00/30) 기본
     * 필요하면 분 휠을 0..55(5분 단위)로 확장 가능
     */
    private fun showDurationWheel(
        initialHours: Int,
        initialMinutes: Int,
        onPicked: (Int, Int) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_duration_picker, null)
        val npH = view.findViewById<android.widget.NumberPicker>(R.id.npHours)
        val npM = view.findViewById<android.widget.NumberPicker>(R.id.npMinutes)

        npH.minValue = 1   // 최소 1시간 허용(경고만 띄우고 저장은 막지 않으려면 이렇게)
        npH.maxValue = 23  // 상한 넉넉히
        npH.value = initialHours.coerceIn(npH.minValue, npH.maxValue)
        npH.wrapSelectorWheel = true

        // 분은 0/30만 제공 (원하면 0,5,10…55로 확장)
        val minuteValues = arrayOf("00", "30")
        npM.minValue = 0
        npM.maxValue = minuteValues.lastIndex
        npM.displayedValues = minuteValues
        npM.value = if (initialMinutes >= 30) 1 else 0
        npM.wrapSelectorWheel = true

        MaterialAlertDialogBuilder(this)
            .setTitle("목표 수면 시간")
            .setView(view)
            .setPositiveButton("확인") { dlg, _ ->
                val h = npH.value
                val m = if (npM.value == 1) 30 else 0
                onPicked(h, m)
                dlg.dismiss()
            }
            .setNegativeButton("취소") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    /** 평균 수면 시간(분) 계산: bed→wake로 넘어갈 때 자정 경계 보정 */
    private fun computeSleepMinutes(bed: LocalTime, wake: LocalTime): Int {
        var endMin = wake.hour * 60 + wake.minute
        var startMin = bed.hour * 60 + bed.minute
        if (endMin <= startMin) endMin += 24 * 60
        return endMin - startMin
    }

    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private fun isBefore(a: LocalTime, b: LocalTime): Boolean = a.isBefore(b)
}
