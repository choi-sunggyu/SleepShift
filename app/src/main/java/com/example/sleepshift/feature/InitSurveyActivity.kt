package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.SleepProgress
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.data.SleepSettings
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.launch

class InitSurveyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init_survey)

        val etAvgBed = findViewById<EditText>(R.id.etAvgBed)
        val etAvgWake = findViewById<EditText>(R.id.etAvgWake)
        val etGoalWake = findViewById<EditText>(R.id.etGoalWake)
        val etGoalSleep = findViewById<EditText>(R.id.etGoalSleep)
        val etMorningGoal = findViewById<EditText>(R.id.etMorningGoal)
        val etReason = findViewById<EditText>(R.id.etReason)
        val tvAvgDur = findViewById<TextView>(R.id.tvAvgDuration)
        val tvTargetBed = findViewById<TextView>(R.id.tvTargetBedtime)
        val btnStart = findViewById<Button>(R.id.btnStartJourney)

        // 기본값
        etAvgBed.setText("04:00")
        etAvgWake.setText("13:00")
        etGoalWake.setText("23:00")
        etGoalSleep.setText("08:00")

        // 실시간 피드백
        fun updatePreview() {
            val avgBed = etAvgBed.text.toString()
            val avgWake = etAvgWake.text.toString()
            val goalWake = etGoalWake.text.toString()
            val goalSleep = etGoalSleep.text.toString()

            val avgDurMin = ((KstTime.hhmmToMinutes(avgWake) - KstTime.hhmmToMinutes(avgBed)) + 1440) % 1440
            val avgDurHHmm = KstTime.minutesToHHmm(avgDurMin)
            tvAvgDur.text = "현재 평균 수면 시간: $avgDurHHmm"

            val target = KstTime.computeTargetBedtime(goalWake, goalSleep)
            tvTargetBed.text = "계산된 목표 취침 시간: $target"

            if (KstTime.hhmmToMinutes(goalSleep) < 240) {
                tvTargetBed.append("  (※ 4시간 미만은 비현실적으로 짧습니다)")
            }
        }
        listOf(etAvgBed, etAvgWake, etGoalWake, etGoalSleep).forEach {
            it.addTextChangedListener(SimpleTextWatcher { updatePreview() })
        }
        updatePreview()

        btnStart.setOnClickListener {
            val avgBed = etAvgBed.text.toString()
            val avgWake = etAvgWake.text.toString()
            val goalWake = etGoalWake.text.toString()
            val goalSleep = etGoalSleep.text.toString()
            val morningGoal = etMorningGoal.text.toString()
            val reason = etReason.text.toString()

            val target = KstTime.computeTargetBedtime(goalWake, goalSleep)
            // 초기 scheduled = avgBed -20분, 단 target보다 이르면 target로
            val initSched = maxOfHHmm(KstTime.addMinutes(avgBed, -20), target)

            val settings = SleepSettings(
                avgBedTime = avgBed,
                avgWakeTime = avgWake,
                goalWakeTime = goalWake,
                goalSleepDuration = goalSleep,
                targetBedtime = target,
                morningGoal = morningGoal,
                reasonToChange = reason
            )
            val progress = SleepProgress(
                scheduledBedtime = initSched,
                consecutiveSuccessDays = 0,
                dailyShiftMinutes = 30,
                lastProgressUpdate = KstTime.todayYmd()
            )

            lifecycleScope.launch {
                val repo = SleepRepository(this@InitSurveyActivity)
                repo.setSettings(settings)
                repo.setProgress(progress)
                Toast.makeText(this@InitSurveyActivity, "저장되었습니다", Toast.LENGTH_SHORT).show()
                startActivity(android.content.Intent(this@InitSurveyActivity, HomeActivity::class.java))
                finish()
            }
        }
    }

    private fun maxOfHHmm(a: String, b: String): String {
        val am = KstTime.hhmmToMinutes(a)
        val bm = KstTime.hhmmToMinutes(b)
        return if (am < bm) b else a
    }

    private class SimpleTextWatcher(val on: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { on() }
    }
}
