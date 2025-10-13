package com.example.sleepshift.feature

import android.content.Intent
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
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.R
import com.example.sleepshift.feature.adapter.MoodPagerAdapter
import com.example.sleepshift.feature.night.NightRoutineViewModel
import com.example.sleepshift.feature.survey.TimePickerUtil
import com.example.sleepshift.util.NightRoutineConstants

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var viewModel: NightRoutineViewModel

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
    private var selectedMoodPosition = NightRoutineConstants.DEFAULT_MOOD_POSITION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night_routine)

        viewModel = ViewModelProvider(this)[NightRoutineViewModel::class.java]

        initViews()
        setupMoodViewPager()
        setupClickListeners()
        observeViewModel()
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

        // 페이지 트랜스포머
        viewPagerMood.setPageTransformer { page, position ->
            val scale = NightRoutineConstants.PAGE_SCALE_MIN +
                    (1 - kotlin.math.abs(position)) *
                    (NightRoutineConstants.PAGE_SCALE_MAX - NightRoutineConstants.PAGE_SCALE_MIN)
            page.scaleY = scale
        }

        viewPagerMood.setCurrentItem(selectedMoodPosition, false)

        createIndicators()

        viewPagerMood.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                selectedMoodPosition = position
                updateMoodDisplay()
                updateIndicators()
            }
        })

        updateMoodDisplay()
        updateIndicators()
    }

    private fun createIndicators() {
        indicatorLayout.removeAllViews()

        for (i in 0 until NightRoutineConstants.MOOD_COUNT) {
            val indicator = View(this)
            val size = dpToPx(NightRoutineConstants.INDICATOR_SIZE_DP)
            val margin = dpToPx(NightRoutineConstants.INDICATOR_MARGIN_DP)

            val layoutParams = LinearLayout.LayoutParams(size, size)
            layoutParams.setMargins(margin, 0, margin, 0)

            indicator.layoutParams = layoutParams
            indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_inactive)

            indicatorLayout.addView(indicator)
        }
    }

    private fun updateIndicators() {
        for (i in 0 until indicatorLayout.childCount) {
            val indicator = indicatorLayout.getChildAt(i)
            val drawable = if (i == selectedMoodPosition) {
                R.drawable.indicator_active
            } else {
                R.drawable.indicator_inactive
            }
            indicator.background = ContextCompat.getDrawable(this, drawable)
        }
    }

    private fun updateMoodDisplay() {
        val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)
        tvSelectedMood.text = currentMood.moodName
    }

    private fun setupClickListeners() {
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        alarmTimeSection.setOnClickListener {
            handleAlarmTimeChange()
        }

        btnSleepCheckIn.setOnClickListener {
            handleSleepCheckIn()
        }
    }

    private fun observeViewModel() {
        viewModel.coinCount.observe(this) { count ->
            tvPawCoinCount.text = count.toString()
        }

        viewModel.alarmTime.observe(this) { time ->
            tvAlarmTime.text = time
        }

        viewModel.showInsufficientCoinsDialog.observe(this) { shortage ->
            showInsufficientCoinsDialog(shortage)
        }

        viewModel.showAlarmChangeSuccess.observe(this) { (coins, time) ->
            showAlarmChangeSuccess(coins, time)
        }

        viewModel.showRewardMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * ⭐ 알람 시간 변경 - TimePickerUtil 사용
     */
    private fun handleAlarmTimeChange() {
        if (!viewModel.canChangeAlarm()) {
            return
        }

        val currentTime = viewModel.alarmTime.value ?: "07:00"
        val currentCoins = viewModel.getCoinCount()

        AlertDialog.Builder(this)
            .setTitle("알람 시간 변경")
            .setMessage(
                "알람 시간을 변경하시겠습니까?\n\n" +
                        "💰 곰젤리 ${NightRoutineConstants.ALARM_CHANGE_COST}개 즉시 차감\n" +
                        "현재 보유: ${currentCoins}개 → ${currentCoins - NightRoutineConstants.ALARM_CHANGE_COST}개"
            )
            .setPositiveButton("변경") { _, _ ->
                showAlarmTimePicker(currentTime)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐ TimePickerUtil을 사용한 알람 시간 선택
     */
    private fun showAlarmTimePicker(currentTime: String) {
        TimePickerUtil.showAlarmTimePicker(
            context = this,
            title = "기상 시간 선택",
            initialTime = currentTime
        ) { hour, minute, timeString ->
            if (timeString != currentTime) {
                viewModel.changeAlarmTime(timeString, hour, minute)
            } else {
                Toast.makeText(this, "알람 시간이 동일합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInsufficientCoinsDialog(shortage: Int) {
        val currentCoins = viewModel.getCoinCount()

        AlertDialog.Builder(this)
            .setTitle("곰젤리 부족")
            .setMessage(
                "알람 시간을 변경하려면\n곰젤리 ${NightRoutineConstants.ALARM_CHANGE_COST}개가 필요합니다.\n\n" +
                        "현재 보유: ${currentCoins}개\n" +
                        "부족: ${shortage}개"
            )
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showAlarmChangeSuccess(coins: Int, time: String) {
        Toast.makeText(
            this,
            "✅ 알람 시간 변경 완료!\n곰젤리 -${NightRoutineConstants.ALARM_CHANGE_COST}개 (잔여: ${coins}개)",
            Toast.LENGTH_LONG
        ).show()

        Log.d(TAG, "알람 변경 완료: $time, 잔여 코인: $coins")
    }

    private fun handleSleepCheckIn() {
        val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)

        viewModel.processSleepCheckIn(currentMood.moodName, selectedMoodPosition)

        // 잠금 화면으로 이동
        val intent = Intent(this, LockScreenActivity::class.java)
        startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        // ViewModel이 자동으로 데이터 로드
    }

    companion object {
        private const val TAG = "NightRoutineActivity"
    }
}