package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        // 어댑터 설정
        moodAdapter = MoodPagerAdapter()
        viewPagerMood.adapter = moodAdapter

        // 페이지 간격 설정 (양옆 이미지가 보이도록)
        viewPagerMood.setPageTransformer { page, position ->
            page.scaleY = 0.85f + (1 - kotlin.math.abs(position)) * 0.15f
        }

        // 기본 선택 위치 (중간: 평온)
        selectedMoodPosition = 2
        viewPagerMood.setCurrentItem(selectedMoodPosition, false)

        // 인디케이터 점들 생성
        createIndicators()

        // 페이지 변경 리스너
        viewPagerMood.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                selectedMoodPosition = position
                updateMoodDisplay()
                updateIndicators()
            }
        })

        // 초기 상태 설정
        updateMoodDisplay()
        updateIndicators()
    }

    private fun createIndicators() {
        indicatorLayout.removeAllViews()

        for (i in 0 until 7) { // 7개의 감정
            val indicator = View(this)
            val size = dpToPx(8)
            val layoutParams = LinearLayout.LayoutParams(size, size)
            layoutParams.setMargins(dpToPx(4), 0, dpToPx(4), 0)

            indicator.layoutParams = layoutParams
            indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_inactive)

            indicatorLayout.addView(indicator)
        }
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

    private fun updateMoodDisplay() {
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
            showAlarmTimeDialog()
        }

        // 수면 체크인 버튼
        btnSleepCheckIn.setOnClickListener {
            startSleepCheckIn()
        }
    }

    private fun updateUI() {
        val coinCount = sharedPreferences.getInt("paw_coin_count", 130)
        tvPawCoinCount.text = coinCount.toString()

        // ⭐ 우선순위: today_alarm_time → target_wake_time → 기본값
        val alarmTime = sharedPreferences.getString("today_alarm_time", null)
            ?: sharedPreferences.getString("target_wake_time", "07:00")
            ?: "07:00"

        tvAlarmTime.text = alarmTime
    }

    private fun showAlarmTimeDialog() {
        val currentAlarmTime = sharedPreferences.getString("today_alarm_time", null)
            ?: sharedPreferences.getString("target_wake_time", "07:00")
            ?: "07:00"

        val timeParts = currentAlarmTime.split(":")
        val currentHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
        val currentMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(this, { _, hour, minute ->
            val newAlarmTime = String.format("%02d:%02d", hour, minute)
            sharedPreferences.edit()
                .putString("today_alarm_time", newAlarmTime)
                .apply()

            tvAlarmTime.text = newAlarmTime
            Toast.makeText(this, "알람 시간: $newAlarmTime", Toast.LENGTH_SHORT).show()
        }, currentHour, currentMinute, true).show()
    }

    private fun startSleepCheckIn() {
        // 감정 데이터 저장
        val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)
        sharedPreferences.edit()
            .putString("today_mood", currentMood.moodName)
            .putInt("today_mood_position", selectedMoodPosition)
            .putLong("sleep_checkin_time", System.currentTimeMillis())
            .apply()


        // 잠금 화면으로 이동
        val intent = Intent(this, LockScreenActivity::class.java)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}