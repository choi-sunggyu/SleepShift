package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.R
import com.example.sleepshift.feature.adapter.MoodPagerAdapter
import com.example.sleepshift.util.DailyAlarmManager

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var alarmManager: DailyAlarmManager

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

    // ⭐ 알람 시간 변경 여부 추적
    private var isAlarmTimeChanged = false
    private val ALARM_CHANGE_COST = 2  // 곰젤리 비용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night_routine)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        alarmManager = DailyAlarmManager(this)

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
        val coinCount = sharedPreferences.getInt("paw_coin_count", 10)
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

        // ⭐ 알람 변경 경고 다이얼로그
        AlertDialog.Builder(this)
            .setTitle("알람 시간 변경")
            .setMessage("알람 시간을 변경하면 수면 체크인 시\n곰젤리 ${ALARM_CHANGE_COST}개가 소모됩니다.\n\n변경하시겠습니까?")
            .setPositiveButton("변경") { _, _ ->
                // 시간 선택 다이얼로그 표시
                TimePickerDialog(this, { _, hour, minute ->
                    val newAlarmTime = String.format("%02d:%02d", hour, minute)

                    // ⭐ 알람 시간이 실제로 변경되었는지 확인
                    if (newAlarmTime != currentAlarmTime) {
                        // SharedPreferences에 저장
                        sharedPreferences.edit()
                            .putString("today_alarm_time", newAlarmTime)
                            .apply()

                        tvAlarmTime.text = newAlarmTime

                        // ⭐ 변경 플래그 설정
                        isAlarmTimeChanged = true

                        setImmediateAlarm(hour, minute)

                        Toast.makeText(
                            this,
                            "알람 시간이 $newAlarmTime 으로 변경되었습니다.\n수면 체크인 시 곰젤리 ${ALARM_CHANGE_COST}개가 차감됩니다.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "알람 시간이 동일합니다", Toast.LENGTH_SHORT).show()
                    }
                }, currentHour, currentMinute, true).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setImmediateAlarm(hour: Int, minute: Int) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            // Android 12+ 권한 체크
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Toast.makeText(
                        this,
                        "알람 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                        Toast.LENGTH_LONG
                    ).show()

                    // 권한 설정 화면으로 이동
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    ).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    return
                }
            }

            val intent = android.content.Intent(this, com.example.sleepshift.feature.alarm.AlarmReceiver::class.java).apply {
                action = "com.example.sleepshift.ALARM_TRIGGER"
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this, 1000, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)

                // ⭐ 현재 시간보다 과거면 다음날로
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                    Log.d("NightRoutine", "⏰ 설정 시간이 지나서 내일로 설정")
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            val now = java.util.Date()
            val alarmDate = java.util.Date(calendar.timeInMillis)

            Log.d("NightRoutine", """
                ========== 알람 즉시 설정 ==========
                현재 시간: $now
                알람 시간: $alarmDate
                ${if (calendar.get(java.util.Calendar.DAY_OF_MONTH) > java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)) "내일" else "오늘"}
                ====================================
            """.trimIndent())

        } catch (e: Exception) {
            Log.e("NightRoutine", "알람 설정 실패: ${e.message}")
            Toast.makeText(this, "알람 설정 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSleepCheckIn() {
        // ⭐ 알람 시간 변경 시 코인 차감
        if (isAlarmTimeChanged) {
            val currentCoins = sharedPreferences.getInt("paw_coin_count", 10)

            if (currentCoins < ALARM_CHANGE_COST) {
                // 코인 부족
                AlertDialog.Builder(this)
                    .setTitle("곰젤리 부족")
                    .setMessage("알람 시간을 변경했기 때문에\n곰젤리 ${ALARM_CHANGE_COST}개가 필요합니다.\n\n현재 보유: ${currentCoins}개")
                    .setPositiveButton("확인", null)
                    .show()
                return
            }

            // 코인 차감
            val newCoinCount = currentCoins - ALARM_CHANGE_COST
            sharedPreferences.edit()
                .putInt("paw_coin_count", newCoinCount)
                .apply()

            updateUI()  // UI 업데이트

            Toast.makeText(
                this,
                "곰젤리 ${ALARM_CHANGE_COST}개가 차감되었습니다. (잔여: ${newCoinCount}개)",
                Toast.LENGTH_SHORT
            ).show()

            Log.d("NightRoutine", "알람 변경 비용 차감: $currentCoins → $newCoinCount")
        }

        // ⭐⭐⭐ 취침 시간 체크 및 보상 지급
        checkBedtimeReward()

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

    /**
     * ⭐ 취침 시간 체크 및 보상
     */
    private fun checkBedtimeReward() {
        val todayBedtime = sharedPreferences.getString("today_bedtime", null)
            ?: sharedPreferences.getString("avg_bedtime", "23:00")
            ?: "23:00"

        val bedtimeParts = todayBedtime.split(":")
        val bedtimeHour = bedtimeParts.getOrNull(0)?.toIntOrNull() ?: 23
        val bedtimeMinute = bedtimeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)

        var bedtimeInMinutes = bedtimeHour * 60 + bedtimeMinute
        var currentTimeInMinutes = currentHour * 60 + currentMinute

        // 자정 넘김 처리
        if (bedtimeHour >= 0 && bedtimeHour < 5) {
            bedtimeInMinutes += 1440
        }
        if (currentHour >= 0 && currentHour < 5) {
            currentTimeInMinutes += 1440
        }

        // ⭐ 취침 시간과의 차이 계산
        val timeDifference = currentTimeInMinutes - bedtimeInMinutes

        val EARLY_TOLERANCE = 60  // 취침 시간 60분 전까지 허용
        val LATE_TOLERANCE = 60   // 취침 시간 60분 후까지 허용

        val rewardAmount = when {
            // 취침 시간 이전 (60분 전 ~ 정각)
            timeDifference in -EARLY_TOLERANCE..0 -> 2

            // 취침 시간 이후 (정각 ~ 15분 후)
            timeDifference in 1..LATE_TOLERANCE -> 1

            // 범위 벗어남
            else -> 0
        }

        if (rewardAmount > 0) {
            // 보상 지급
            val currentCoins = sharedPreferences.getInt("paw_coin_count", 10)
            val newCoins = currentCoins + rewardAmount

            sharedPreferences.edit()
                .putInt("paw_coin_count", newCoins)
                .putBoolean("earned_bedtime_reward_today", true)
                .apply()

            updateUI()

            val rewardMessage = if (rewardAmount == 2) {
                "✨ 완벽한 취침 시간! 곰젤리 +2 (잔여: ${newCoins}개)"
            } else {
                "😊 취침 완료! 곰젤리 +1 (잔여: ${newCoins}개)"
            }

            Toast.makeText(this, rewardMessage, Toast.LENGTH_LONG).show()

            Log.d("NightRoutine", """
                취침 보상 지급
                - 차이: ${timeDifference}분
                - 보상: ${rewardAmount}개
                - 변경: $currentCoins → $newCoins
            """.trimIndent())
        } else {
            val reason = if (timeDifference < -EARLY_TOLERANCE) {
                "너무 일찍 체크인했습니다"
            } else {
                "취침 시간($todayBedtime)을 ${timeDifference - LATE_TOLERANCE}분 넘겼습니다"
            }

            Log.d("NightRoutine", "보상 없음: $reason (차이: ${timeDifference}분)")
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
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
        // ⭐ 화면으로 돌아올 때 플래그 초기화 (설정에서 돌아온 경우 등)
        // isAlarmTimeChanged = false  // 필요시 활성화
    }
}