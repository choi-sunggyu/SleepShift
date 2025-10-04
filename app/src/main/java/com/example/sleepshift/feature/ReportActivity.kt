package com.example.sleepshift.feature

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.*
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

    // ⭐ 변수명 변경
    private lateinit var tvPawCoinCount: TextView
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val calendar = Calendar.getInstance()
    private val today = Calendar.getInstance()

    // 수면 데이터
    private val sleepData = mutableMapOf<String, SleepInfo>()

    data class SleepInfo(
        val bedTime: String,
        val wakeTime: String,
        val quality: SleepQuality
    )

    enum class SleepQuality {
        GOOD, AVERAGE, POOR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        initViews()
        setupClickListeners()
        initSampleData()
        updateCalendar()

        // 코인 개수 표시
        updateCoinCount()
    }

    private fun initViews() {
        tvMonthYear = findViewById(R.id.tvMonthYear)
        calendarGrid = findViewById(R.id.calendarGrid)
        btnHome = findViewById(R.id.btnHome)
        btnGoToBed = findViewById(R.id.btnGoToBed)
        btnSettings = findViewById(R.id.btnSettings)

        // ⭐ 올바른 ID로 변경
        tvPawCoinCount = findViewById(R.id.tvPawCoinCount)
    }

    /**
     * 코인 개수 업데이트
     */
    private fun updateCoinCount() {
        val coinCount = sharedPreferences.getInt("paw_coin_count", 10)
        tvPawCoinCount.text = coinCount.toString()

        android.util.Log.d("ReportActivity", "코인 개수: $coinCount")
    }

    private fun setupClickListeners() {
        btnHome.setOnClickListener {
            finish() // 메인 화면으로 돌아가기
        }

        btnGoToBed.setOnClickListener {
            // 자러가기 기능
            val intent = android.content.Intent(this, NightRoutineActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            // 설정 화면으로 이동
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    // ... 나머지 코드는 동일 ...

    private fun initSampleData() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        for (i in 1..31) {
            cal.set(Calendar.DAY_OF_MONTH, i)
            val dateKey = sdf.format(cal.time)

            when (i % 3) {
                0 -> sleepData[dateKey] = SleepInfo("23:30", "07:30", SleepQuality.GOOD)
                1 -> sleepData[dateKey] = SleepInfo("00:15", "08:00", SleepQuality.AVERAGE)
                2 -> sleepData[dateKey] = SleepInfo("01:30", "09:00", SleepQuality.POOR)
            }
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

        for (i in 0 until firstDayOfWeek) {
            addEmptyDayView()
        }

        for (day in 1..lastDayOfMonth) {
            addDayView(day)
        }
    }

    private fun addEmptyDayView() {
        val emptyView = android.view.View(this)
        val layoutParams = GridLayout.LayoutParams()
        layoutParams.width = 0
        layoutParams.height = dpToPx(48)
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
            layoutParams.height = dpToPx(48)
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

        val dateKey = getDateKey(day)
        sleepData[dateKey]?.let { sleepInfo ->
            addSleepIndicators(dayContainer, sleepInfo)
        }

        calendarGrid.addView(dayContainer)
    }

    private fun addSleepIndicators(container: LinearLayout, sleepInfo: SleepInfo) {
        val indicatorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = dpToPx(2)
            this.layoutParams = layoutParams
        }

        val bedtimeIndicator = android.view.View(this).apply {
            val layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6))
            layoutParams.marginEnd = dpToPx(2)
            this.layoutParams = layoutParams
            background = ContextCompat.getDrawable(this@ReportActivity, R.drawable.sleep_indicator_blue)
        }

        val waketimeIndicator = android.view.View(this).apply {
            val layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6))
            this.layoutParams = layoutParams
            background = when (sleepInfo.quality) {
                SleepQuality.GOOD -> ContextCompat.getDrawable(this@ReportActivity, R.drawable.sleep_indicator_green)
                SleepQuality.AVERAGE -> ContextCompat.getDrawable(this@ReportActivity, R.drawable.sleep_indicator_orange)
                SleepQuality.POOR -> ContextCompat.getDrawable(this@ReportActivity, R.drawable.sleep_indicator_orange)
            }
        }

        indicatorContainer.addView(bedtimeIndicator)
        indicatorContainer.addView(waketimeIndicator)
        container.addView(indicatorContainer)
    }

    private fun onDayClicked(day: Int) {
        val dateKey = getDateKey(day)
        val sleepInfo = sleepData[dateKey]

        if (sleepInfo != null) {
            showSleepDetail(day, sleepInfo)
        } else {
            Toast.makeText(this, "${day}일에는 수면 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSleepDetail(day: Int, sleepInfo: SleepInfo) {
        val message = "${day}일 수면 정보\n" +
                "취침: ${sleepInfo.bedTime}\n" +
                "기상: ${sleepInfo.wakeTime}\n" +
                "수면 질: ${when(sleepInfo.quality) {
                    SleepQuality.GOOD -> "좋음"
                    SleepQuality.AVERAGE -> "보통"
                    SleepQuality.POOR -> "나쁨"
                }}"

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        updateCoinCount()
    }
}