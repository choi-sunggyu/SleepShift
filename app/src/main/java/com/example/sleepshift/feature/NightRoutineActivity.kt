package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.R
import com.example.sleepshift.feature.adapter.MoodPagerAdapter

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // Views
    private lateinit var tvPawCoinCount: TextView
    private lateinit var btnSettings: ImageView
    private lateinit var viewPagerMood: ViewPager2
    private lateinit var tvSelectedMood: TextView
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var alarmTimeSection: RelativeLayout
    private lateinit var tvAlarmTime: TextView
    private lateinit var btnSleepCheckIn: LinearLayout

    // Adapter
    private lateinit var moodAdapter: MoodPagerAdapter
    private var selectedMoodPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night_routine)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        initViews()
        setupMoodViewPager()
        setupClickListeners()
        updateUI()
    }

    private fun initViews() {
        tvPawCoinCount = findViewById(R.id.tvPawCoinCount)
        btnSettings = findViewById(R.id.btnSettings)
        viewPagerMood = findViewById(R.id.viewPagerMood)
        tvSelectedMood = findViewById(R.id.tvSelectedMood)
        indicatorLayout = findViewById(R.id.indicatorLayout)
        alarmTimeSection = findViewById(R.id.alarmTimeSection)
        tvAlarmTime = findViewById(R.id.tvAlarmTime)
        btnSleepCheckIn = findViewById(R.id.btnSleepCheckIn)
    }

    private fun setupMoodViewPager() {
        moodAdapter = MoodPagerAdapter()
        viewPagerMood.adapter = moodAdapter

        // 기본 선택 위치 (중간)
        selectedMoodPosition = 2
        viewPagerMood.setCurrentItem(selectedMoodPosition, false)

        // 인디케이터 설정
        setupIndicators()

        // ViewPager 페이지 변경 리스너
        viewPagerMood.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                selectedMoodPosition = position
                updateSelectedMood()
                updateIndicators()
            }
        })

        // 초기 상태 설정
        updateSelectedMood()
    }

    private fun setupIndicators() {
        indicatorLayout.removeAllViews()

        for (i in 0 until 7) { // 7개의 감정
            val indicator = View(this)
            val size = 8.dpToPx()
            val layoutParams = LinearLayout.LayoutParams(size, size)
            layoutParams.setMargins(4.dpToPx(), 0, 4.dpToPx(), 0)

            indicator.layoutParams = layoutParams
            indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_inactive)

            indicatorLayout.addView(indicator)
        }

        updateIndicators()
    }

    private fun updateIndicators() {
        for (i in 0 until indicatorLayout.childCount) {
            val indicator = indicatorLayout.getChildAt(i)
            if (i == selectedMoodPosition) {
                indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_active)
            } else {
                indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_inactive)
            }
        }
    }

    private fun updateSelectedMood() {
        val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)
        tvSelectedMood.text = currentMood.moodName
    }

    private fun setupClickListeners() {
        // 설정 버튼
        btnSettings.setOnClickListener {
            openSettings()
        }

        // 알람 시간 섹션
        alarmTimeSection.setOnClickListener {
            showAlarmTimePickerDialog()
        }

        // 수면 체크인 버튼
        btnSleepCheckIn.setOnClickListener {
            startSleepCheckIn()
        }
    }

    private fun updateUI() {
        // 발바닥 코인 개수 업데이트
        val coinCount = getPawCoinCount()
        tvPawCoinCount.text = coinCount.toString()

        // 알람 시간 업데이트
        val alarmHour = sharedPreferences.getInt("alarm_hour", 7)
        val alarmMinute = sharedPreferences.getInt("alarm_minute", 0)
        tvAlarmTime.text = String.format("%02d:%02d", alarmHour, alarmMinute)
    }

    private fun showAlarmTimePickerDialog() {
        val currentHour = sharedPreferences.getInt("alarm_hour", 7)
        val currentMinute = sharedPreferences.getInt("alarm_minute", 0)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                // 오늘 밤에만 적용되는 알람 시간 저장
                with(sharedPreferences.edit()) {
                    putInt("tonight_alarm_hour", hourOfDay)
                    putInt("tonight_alarm_minute", minute)
                    putBoolean("tonight_custom_alarm", true)
                    apply()
                }

                tvAlarmTime.text = String.format("%02d:%02d", hourOfDay, minute)
                Toast.makeText(this, "오늘 밤 알람 시간이 ${String.format("%02d:%02d", hourOfDay, minute)}로 설정되었습니다", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            true // 24시간 형식
        )

        timePickerDialog.setTitle("오늘 밤 알람 시간 설정")
        timePickerDialog.show()
    }

    private fun startSleepCheckIn() {
        // 선택된 감정과 시간 저장
        saveTodayMoodData()

        // 발바닥 코인 지급 (수면 체크인 보상)
        addPawCoins(10)

        // 화면 잠금 액티비티로 이동
        val intent = Intent(this, LockScreenActivity::class.java)
        startActivity(intent)

        // 버튼 클릭 애니메이션
        animateButton()
    }

    private fun saveTodayMoodData() {
        val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)
        val currentTime = System.currentTimeMillis()

        with(sharedPreferences.edit()) {
            putString("today_mood", currentMood.moodName)
            putInt("today_mood_position", selectedMoodPosition)
            putLong("sleep_checkin_time", currentTime)
            apply()
        }

        Toast.makeText(this, "오늘의 기분: ${currentMood.moodName}", Toast.LENGTH_SHORT).show()
    }

    private fun addPawCoins(amount: Int) {
        val currentCount = getPawCoinCount()
        val newCount = currentCount + amount

        with(sharedPreferences.edit()) {
            putInt("paw_coin_count", newCount)
            apply()
        }

        // UI 업데이트
        tvPawCoinCount.text = newCount.toString()

        Toast.makeText(this, "+${amount} 발바닥 코인 획득!", Toast.LENGTH_SHORT).show()
    }

    private fun getPawCoinCount(): Int {
        return sharedPreferences.getInt("paw_coin_count", 130)
    }

    private fun animateButton() {
        btnSleepCheckIn.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                btnSleepCheckIn.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}