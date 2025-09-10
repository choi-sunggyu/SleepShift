package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.MorningRoutine
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.launch

class MorningGateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_morning_gate)

        lifecycleScope.launch {
            val repo = SleepRepository(this@MorningGateActivity)
            val today = KstTime.todayYmd()
            val rec = repo.getDailyRecord(today)
            if (rec != null && !rec.success) {
                repo.saveDailyRecord(today, rec.copy(success = true))
            }
        }

        val etTask = findViewById<EditText>(R.id.etTask)
        val btnNext = findViewById<Button>(R.id.btnToReport)
        btnNext.setOnClickListener {
            lifecycleScope.launch {
                val repo = SleepRepository(this@MorningGateActivity)
                repo.saveMorningRoutine(MorningRoutine(date = KstTime.todayYmd(), key_task = etTask.text.toString()))
                startActivity(android.content.Intent(this@MorningGateActivity, ReportActivity::class.java))
                finish()
            }
        }
    }
}
