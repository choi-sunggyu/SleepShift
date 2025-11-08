package com.example.sleepshift.feature

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityNightRoutineBinding
import com.example.sleepshift.feature.survey.TimePickerUtil
import com.example.sleepshift.util.DailyAlarmManager
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.*

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightRoutineBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences

    // 판다 기분 데이터
    private val moodList = listOf(
        Mood("기쁨", R.drawable.panda_happy),
        Mood("평온", R.drawable.panda_calm),
        Mood("피곤", R.drawable.panda_tired),
        Mood("우울", R.drawable.panda_sad),
        Mood("화남", R.drawable.panda_angry)
    )

    private var selectedMoodIndex = 0
    private val ALARM_CHANGE_COST = 2  // 알람 변경 비용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNightRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        setupMoodSelector()
        setupUI()

        Log.d("NightRoutine", "나이트 루틴 시작")
    }

    // 판다 기분 선택 설정
    private fun setupMoodSelector() {
        val adapter = MoodPagerAdapter(moodList)
        binding.viewPagerMood.adapter = adapter
        binding.viewPagerMood.offscreenPageLimit = 1

        selectedMoodIndex = 2
        binding.viewPagerMood.setCurrentItem(selectedMoodIndex, false)
        binding.tvSelectedMood.text = moodList[selectedMoodIndex].name

        binding.viewPagerMood.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                selectedMoodIndex = position
                binding.tvSelectedMood.text = moodList[position].name
                updateIndicators(position)

                Log.d("NightRoutine", "기분 선택: ${moodList[position].name}")
            }
        })

        setupIndicators()
        updateIndicators(selectedMoodIndex)
    }

    // 인디케이터 설정
    private fun setupIndicators() {
        binding.indicatorLayout.removeAllViews()

        for (i in moodList.indices) {
            val indicator = View(this)
            val size = dpToPx(8)
            val params = ViewGroup.MarginLayoutParams(size, size)
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0)
            indicator.layoutParams = params
            indicator.setBackgroundResource(R.drawable.indicator_inactive)

            binding.indicatorLayout.addView(indicator)
        }
    }

    // 인디케이터 업데이트
    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorLayout.childCount) {
            val indicator = binding.indicatorLayout.getChildAt(i)
            if (i == position) {
                indicator.setBackgroundResource(R.drawable.indicator_active)
            } else {
                indicator.setBackgroundResource(R.drawable.indicator_inactive)
            }
        }
    }

    private fun setupUI() {
        // 알람 시간 표시
        val alarmTime = sharedPreferences.getString("today_alarm_time", null)
            ?: sharedPreferences.getString("target_wake_time", "07:00")
            ?: "07:00"
        binding.tvAlarmTime.text = alarmTime

        // 코인 개수 표시
        val coinCount = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvPawCoinCount.text = coinCount.toString()

        // 수면 체크인 버튼
        binding.btnSleepCheckin.setOnClickListener {
            performSleepCheckin()
        }

        // 설정 버튼
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 알람 시간 섹션 클릭 (알람 수정)
        binding.alarmTimeSection.setOnClickListener {
            showAlarmEditDialog()
        }
    }

    /**
     * 알람 수정 다이얼로그
     */
    private fun showAlarmEditDialog() {
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)

        // 코인 부족 체크
        if (currentCoins < ALARM_CHANGE_COST) {
            AlertDialog.Builder(this)
                .setTitle("코인 부족")
                .setMessage("알람 변경을 위해서는 곰젤리 ${ALARM_CHANGE_COST}개가 필요합니다.\n현재 보유: ${currentCoins}개")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        // 확인 다이얼로그
        AlertDialog.Builder(this)
            .setTitle("알람 변경")
            .setMessage("알람을 변경하시겠습니까?\n곰젤리 ${ALARM_CHANGE_COST}개가 소모됩니다.")
            .setPositiveButton("변경하기") { _, _ ->
                showTimePicker()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐⭐⭐ 시간 선택 다이얼로그
     */
    private fun showTimePicker() {
        // 현재 알람 시간 불러오기
        val currentAlarmTime = sharedPreferences.getString("today_alarm_time", "07:00") ?: "07:00"
        val timeParts = currentAlarmTime.split(":")
        val currentHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
        val currentMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerUtil.showTimePicker(
            context = this,
            title = "알람 시간",
            initialTime = LocalTime.of(currentHour, currentMinute)
        ) { hour, minute ->
            val newAlarmTime = String.format("%02d:%02d", hour, minute)
            saveOneTimeAlarm(newAlarmTime)
        }
    }

    /**
     * ⭐⭐⭐ 일회성 알람 저장
     */
    private fun saveOneTimeAlarm(newAlarmTime: String) {
        // 코인 차감
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        val newCoins = currentCoins - ALARM_CHANGE_COST

        sharedPreferences.edit().apply {
            putInt("paw_coin_count", newCoins)
            putBoolean("is_one_time_alarm", true)
            putString("one_time_alarm_time", newAlarmTime)
            apply()
        }

        // UI 업데이트
        binding.tvAlarmTime.text = newAlarmTime
        binding.tvPawCoinCount.text = newCoins.toString()

        // 알람 재설정
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val alarmManager = DailyAlarmManager(this)
        alarmManager.updateDailyAlarm(currentDay)

        Toast.makeText(
            this,
            "알람이 ${newAlarmTime}로 변경되었습니다\n곰젤리 -${ALARM_CHANGE_COST}개",
            Toast.LENGTH_LONG
        ).show()

        Log.d("NightRoutine", "일회성 알람 설정: $newAlarmTime")
    }

    // 수면 체크인 수행
    private fun performSleepCheckin() {
        Log.d("NightRoutine", "==================")
        Log.d("NightRoutine", "수면 체크인 시작")

        val currentTime = System.currentTimeMillis()
        val today = getTodayDateString()
        val timeString = getCurrentTimeString()

        Log.d("NightRoutine", "체크인 시간: $timeString")
        Log.d("NightRoutine", "선택한 기분: ${moodList[selectedMoodIndex].name}")

        val bedtimeSuccess = checkBedtimeSuccess(timeString)

        if (bedtimeSuccess) {
            Log.d("NightRoutine", "✅ 목표 시간 내 체크인 - 취침 성공!")
        } else {
            Log.d("NightRoutine", "⚠️ 목표 시간 초과 - 취침 늦음")
        }

        sharedPreferences.edit().apply {
            putBoolean("bedtime_success_$today", bedtimeSuccess)
            putString("actual_bedtime_$today", timeString)
            putString("mood_$today", moodList[selectedMoodIndex].name)
            putLong("bedtime_checkin_time", currentTime)
            apply()
        }

        // 알람 설정
        setupAlarm()

        // 알람 볼륨 최대로
        setMaxAlarmVolume()

        // **정석 설계**: LockMode을 SLEEP_MODE_LOCK으로 설정 후 락스크린으로 이동
        LockPrefsHelper.setLockMode(this, com.example.sleepshift.data.LockMode.SLEEP_MODE_LOCK)
        // 락스크린으로 이동
        goToLockScreen()
    }

    // 취침 성공 판정
    private fun checkBedtimeSuccess(currentTimeString: String): Boolean {
        val targetBedtime = sharedPreferences.getString("today_bedtime", "23:00") ?: "23:00"

        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTime = sdf.parse(currentTimeString)
            val targetTime = sdf.parse(targetBedtime)

            if (currentTime != null && targetTime != null) {
                val success = currentTime.time <= targetTime.time
                Log.d("NightRoutine", "목표: $targetBedtime vs 실제: $currentTimeString = $success")
                success
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("NightRoutine", "시간 비교 실패", e)
            false
        }
    }

    // 알람 설정
    private fun setupAlarm() {
        val currentDay = sharedPreferences.getInt("current_day", 1)
        val alarmManager = DailyAlarmManager(this)

        val success = alarmManager.updateDailyAlarm(currentDay)

        if (success) {
            Log.d("NightRoutine", "알람 설정 완료 (Day $currentDay)")
            Toast.makeText(this, "알람이 설정되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("NightRoutine", "알람 설정 실패 - 권한 확인 필요")
            Toast.makeText(this, "알람 설정 실패\n권한을 확인해주세요", Toast.LENGTH_LONG).show()
        }
    }

    // 알람 볼륨 최대로 설정
    private fun setMaxAlarmVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            if (!sharedPreferences.contains("original_alarm_volume")) {
                sharedPreferences.edit().putInt("original_alarm_volume", currentVolume).apply()
                Log.d("NightRoutine", "원래 볼륨 저장: $currentVolume")
            }

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            Log.d("NightRoutine", "알람 볼륨 최대로 설정: $currentVolume -> $maxVolume")
        } catch (e: Exception) {
            Log.e("NightRoutine", "볼륨 설정 실패", e)
        }
    }

    // 락스크린으로 이동
    private fun goToLockScreen() {
        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().apply {
            putBoolean("isLocked", true)
            putBoolean("is_sleep_mode", true)
            apply()
        }

        Log.d("NightRoutine", "락스크린으로 이동 (수면 모드)")

        val intent = Intent(this, LockScreenActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()

        // 화면 복귀시 최신 데이터로 업데이트
        val alarmTime = sharedPreferences.getString("today_alarm_time", "07:00") ?: "07:00"
        binding.tvAlarmTime.text = alarmTime

        val coinCount = sharedPreferences.getInt("paw_coin_count", 0)
        binding.tvPawCoinCount.text = coinCount.toString()
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // 기분 데이터 클래스
    data class Mood(
        val name: String,
        val drawableRes: Int
    )

    // ViewPager2 어댑터
    inner class MoodPagerAdapter(private val moods: List<Mood>) :
        RecyclerView.Adapter<MoodPagerAdapter.MoodViewHolder>() {

        inner class MoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgMood: ImageView = view.findViewById(R.id.imgPandaMood)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mood_panda, parent, false)
            return MoodViewHolder(view)
        }

        override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
            holder.imgMood.setImageResource(moods[position].drawableRes)
        }

        override fun getItemCount() = moods.size
    }
}