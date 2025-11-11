package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sleepshift.R
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var tvMonthYear: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var btnHome: ImageView
    private lateinit var btnGoToBed: LinearLayout
    private lateinit var btnSettings: ImageView
    private lateinit var tvPawCoinCount: TextView
    private lateinit var tvDayCount: TextView
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val calendar = Calendar.getInstance()
    private val today = Calendar.getInstance()

    // 수면 기록 데이터
    data class DailySleepRecord(
        val bedtimeSuccess: Boolean,
        val wakeSuccess: Boolean,
        val actualBedtime: String?,
        val actualWaketime: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        initViews()
        setupClickListeners()
        updateCalendar()
        updateDayCount()
        updateCoinCount()

        Log.d("ReportActivity", "리포트 화면 시작")
    }

    private fun initViews() {
        tvMonthYear = findViewById(R.id.tvMonthYear)
        calendarGrid = findViewById(R.id.calendarGrid)
        btnHome = findViewById(R.id.btnHome)
        btnGoToBed = findViewById(R.id.btnGoToBed)
        btnSettings = findViewById(R.id.btnSettings)
        tvPawCoinCount = findViewById(R.id.tvPawCoinCount)
        tvDayCount = findViewById(R.id.tvDayCount)
    }

    private fun updateDayCount() {
        val currentDay = getCurrentDay()
        tvDayCount.text = "Day $currentDay"
        Log.d("ReportActivity", "Day: $currentDay")
    }

    private fun getCurrentDay(): Int {
        val installDate = sharedPreferences.getLong("app_install_date", System.currentTimeMillis())
        val currentDate = System.currentTimeMillis()
        val daysDiff = ((currentDate - installDate) / (24 * 60 * 60 * 1000)).toInt() + 1

        return when {
            daysDiff <= 0 -> 1
            else -> daysDiff
        }
    }

    private fun updateCoinCount() {
        val coinCount = sharedPreferences.getInt("paw_coin_count", 10)
        tvPawCoinCount.text = coinCount.toString()
        Log.d("ReportActivity", "코인: $coinCount")
    }

    private fun setupClickListeners() {
        btnHome.setOnClickListener {
            finish()
        }

        btnGoToBed.setOnClickListener {
            val intent = Intent(this, NightRoutineActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateCalendar() {
        val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.getDefault())
        tvMonthYear.text = monthFormat.format(calendar.time)

        calendarGrid.removeAllViews()

        val firstDayOfMonth = calendar.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1
        val lastDayOfMonth = firstDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 빈 칸 추가
        for (i in 0 until firstDayOfWeek) {
            addEmptyDayView()
        }

        // 날짜 추가
        for (day in 1..lastDayOfMonth) {
            addDayView(day)
        }
    }

    private fun addEmptyDayView() {
        val emptyView = android.view.View(this)
        val layoutParams = GridLayout.LayoutParams()
        layoutParams.width = 0
        layoutParams.height = dpToPx(60)
        layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        emptyView.layoutParams = layoutParams
        calendarGrid.addView(emptyView)
    }

    private fun addDayView(day: Int) {
        val dayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = 0
            layoutParams.height = dpToPx(60)
            layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            layoutParams.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            this.layoutParams = layoutParams

            background = ContextCompat.getDrawable(this@ReportActivity, R.drawable.calendar_day_background)

            isClickable = true
            isFocusable = true
            setOnClickListener {
                onDayClicked(day)
            }
        }

        // 날짜 텍스트
        val dayText = TextView(this).apply {
            text = day.toString()
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@ReportActivity, R.color.text_primary))

            if (isToday(day)) {
                background = ContextCompat.getDrawable(this@ReportActivity, R.drawable.calendar_day_today)
                setTextColor(ContextCompat.getColor(this@ReportActivity, R.color.button_primary))
            }
        }

        dayContainer.addView(dayText)

        // 수면 성공 기록 표시
        val dateKey = getDateKey(day)
        val sleepRecord = getSleepRecord(dateKey)
        addSleepSuccessIndicators(dayContainer, sleepRecord)

        calendarGrid.addView(dayContainer)
    }

    // 수면 기록 가져오기
    private fun getSleepRecord(dateKey: String): DailySleepRecord {
        val bedtimeSuccess = sharedPreferences.getBoolean("bedtime_success_$dateKey", false)
        val wakeSuccess = sharedPreferences.getBoolean("wake_success_$dateKey", false)
        val actualBedtime = sharedPreferences.getString("actual_bedtime_$dateKey", null)
        val actualWaketime = sharedPreferences.getString("actual_waketime_$dateKey", null)

        return DailySleepRecord(bedtimeSuccess, wakeSuccess, actualBedtime, actualWaketime)
    }

    // 취침/기상 성공 아이콘 표시
    private fun addSleepSuccessIndicators(container: LinearLayout, record: DailySleepRecord) {
        if (!record.bedtimeSuccess && !record.wakeSuccess) {
            // 기록 없음
            return
        }

        val indicatorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = dpToPx(4)
            this.layoutParams = layoutParams
        }

        // 취침 성공 아이콘
        if (record.bedtimeSuccess) {
            val bedtimeIcon = TextView(this).apply {
                text = "🌙"
                textSize = 12f
                setPadding(dpToPx(2), 0, dpToPx(2), 0)
            }
            indicatorContainer.addView(bedtimeIcon)
        }

        // 기상 성공 아이콘
        if (record.wakeSuccess) {
            val wakeIcon = TextView(this).apply {
                text = "☀️"
                textSize = 12f
                setPadding(dpToPx(2), 0, dpToPx(2), 0)
            }
            indicatorContainer.addView(wakeIcon)
        }

        container.addView(indicatorContainer)
    }

    // 날짜 클릭 시 상세 정보
    private fun onDayClicked(day: Int) {
        val dateKey = getDateKey(day)
        val record = getSleepRecord(dateKey)

        if (!record.bedtimeSuccess && !record.wakeSuccess) {
            Toast.makeText(this, "${day}일에는 수면 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val message = buildString {
            append("${day}일 수면 기록\n\n")

            if (record.bedtimeSuccess) {
                append("✅ 취침 성공 🌙\n")
                if (record.actualBedtime != null) {
                    append("   취침 시간: ${record.actualBedtime}\n")
                }
            } else {
                append("❌ 취침 미완료\n")
            }

            append("\n")

            if (record.wakeSuccess) {
                append("✅ 기상 성공 ☀️\n")
                if (record.actualWaketime != null) {
                    append("   기상 시간: ${record.actualWaketime}\n")
                }
            } else {
                append("❌ 기상 미완료 (재알람)\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("📊 수면 리포트")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun isToday(day: Int): Boolean {
        return today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) == day
    }

    private fun getDateKey(day: Int): String {
        val dateCalendar = calendar.clone() as Calendar
        dateCalendar.set(Calendar.DAY_OF_MONTH, day)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(dateCalendar.time)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        updateDayCount()
        updateCoinCount()
        updateCalendar()
    }
}