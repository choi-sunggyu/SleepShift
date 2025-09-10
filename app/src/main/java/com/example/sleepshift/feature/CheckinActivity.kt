package com.example.sleepshift.feature

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.R
import com.example.sleepshift.data.CheckinRecord
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.launch

class CheckinActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin)

        val spEmotion = findViewById<Spinner>(R.id.spEmotion)
        val cbGoal = findViewById<CheckBox>(R.id.cbGoal)
        val btnGo = findViewById<Button>(R.id.btnGoSleep)

        spEmotion.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("😀", "🙂", "😐", "😕", "😞")
        )

        btnGo.setOnClickListener {
            lifecycleScope.launch {
                val repo = SleepRepository(this@CheckinActivity)
                repo.saveCheckinRecord(
                    KstTime.todayYmd(),
                    CheckinRecord(
                        emotion = spEmotion.selectedItem.toString(),
                        goalAchieved = cbGoal.isChecked
                    )
                )
                startActivity(android.content.Intent(this@CheckinActivity, SleepModeActivity::class.java))
                finish()
            }
        }
    }
}
