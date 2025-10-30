package com.example.sleepshift.feature

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.sleepshift.R
import com.example.sleepshift.feature.adapter.MoodPagerAdapter
import com.example.sleepshift.feature.night.NightRoutineViewModel
import com.example.sleepshift.feature.survey.TimePickerUtil
import com.example.sleepshift.service.LockOverlayService
import com.example.sleepshift.util.NightRoutineConstants
import java.text.SimpleDateFormat
import java.util.*

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

    private fun startLockMode() {
        // 1. 잠금 플래그 설정
        val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
        lockPrefs.edit().putBoolean("isLocked", true).apply()

        // 2. 오버레이 권한 확인
        if (!checkOverlayPermission(this)) {
            requestOverlayPermission(this)
            return
        }

        // 3. 오버레이 서비스 시작
        LockOverlayService.start(this)

        // 4. LockScreenActivity 시작
        val intent = Intent(this, LockScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        finish()
    }

    // 권한 체크 메서드
    private fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            startActivity(intent)
            Toast.makeText(this, "다른 앱 위에 표시 권한을 허용해주세요", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * ⭐ 알람 시간 변경
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
     * ⭐⭐⭐ 알람 시간 선택 및 설정
     */
    private fun showAlarmTimePicker(currentTime: String) {
        TimePickerUtil.showAlarmTimePicker(
            context = this,
            title = "기상 시간 선택",
            initialTime = currentTime
        ) { hour, minute, timeString ->
            if (timeString != currentTime) {
                // 1. ViewModel에서 코인 차감 및 데이터 저장
                viewModel.changeAlarmTime(timeString, hour, minute)

                // 2. ⭐⭐⭐ 실제 알람 설정
                setOneTimeAlarm(hour, minute, timeString)

                Log.d(TAG, "알람 시간 변경 완료: $timeString")
            } else {
                Toast.makeText(this, "알람 시간이 동일합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ⭐⭐⭐ 일회성 알람 설정
     */
    private fun setOneTimeAlarm(hour: Int, minute: Int, timeString: String) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // AlarmReceiver Intent
            val intent = Intent(this, com.example.sleepshift.feature.alarm.AlarmReceiver::class.java).apply {
                action = "com.example.sleepshift.ALARM_TRIGGER"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                1000,  // 일회성 알람용 고유 ID
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // ⭐⭐⭐ 알람 시간 계산 (수정)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val now = Calendar.getInstance()

            // ⭐ 현재 시간과 비교하여 다음날로 설정할지 결정
            if (calendar.timeInMillis <= now.timeInMillis) {
                // 설정한 시간이 현재 시간보다 이전이면 다음날로
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                Log.d(TAG, "⚠️ 설정 시간이 과거이므로 다음날로 설정")
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "⏰ 일회성 알람 설정 시작")
            Log.d(TAG, "  - 설정 시간: $timeString")
            Log.d(TAG, "  - 현재 시간: ${dateFormat.format(now.time)}")
            Log.d(TAG, "  - 알람 울릴 시간: ${dateFormat.format(calendar.time)}")

            val timeDiff = (calendar.timeInMillis - now.timeInMillis) / 1000 / 60
            Log.d(TAG, "  - 알람까지 남은 시간: ${timeDiff}분")

            // ⭐ 알람 시간이 너무 가까우면 경고
            if (timeDiff < 5) {
                Log.w(TAG, "⚠️ 알람 시간이 너무 가깝습니다! (${timeDiff}분 후)")
                Toast.makeText(
                    this,
                    "알람이 ${timeDiff}분 후에 울립니다. 시간을 확인해주세요!",
                    Toast.LENGTH_LONG
                ).show()
            }

            // 정확한 알람 설정
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                        pendingIntent
                    )
                    Log.d(TAG, "✅ setAlarmClock 호출 완료 (Android 12+)")
                } else {
                    Log.w(TAG, "⚠️ 정확한 알람 권한 없음 - 권한 요청")
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    Toast.makeText(this, "정확한 알람 권한을 허용해주세요", Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                    pendingIntent
                )
                Log.d(TAG, "✅ setAlarmClock 호출 완료 (Android 11 이하)")
            }

            Log.d(TAG, "✅ 일회성 알람 설정 완료")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")

            Toast.makeText(
                this,
                "알람이 ${timeString}에 설정되었습니다\n(약 ${timeDiff}분 후)",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e(TAG, "❌ 알람 설정 실패", e)
            Toast.makeText(this, "알람 설정에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
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

    /**
     * ⭐⭐⭐ 수면 체크인 (잠금 화면으로 이동)
     */
    private fun handleSleepCheckIn() {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "✅ 수면 체크인 시작")

            val currentMood = moodAdapter.getMoodAt(selectedMoodPosition)
            viewModel.processSleepCheckIn(currentMood.moodName, selectedMoodPosition)

            // ✅ 잠금 상태 저장
            val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
            lockPrefs.edit {
                putBoolean("isLocked", true)
            }
            Log.d(TAG, "✅ 잠금 상태 저장 완료")

            // ⭐⭐⭐ LockMonitoringService 시작
            startLockMonitoringService()

            // ✅ 잠금 화면으로 이동
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "✅ LockScreenActivity 시작")

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")

            finish()

        } catch (e: Exception) {
            Log.e(TAG, "❌ 수면 체크인 중 오류 발생", e)
            Toast.makeText(this, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * ⭐⭐⭐ LockMonitoringService 시작
     */
    private fun startLockMonitoringService() {
        try {
            val serviceIntent = Intent(this, com.example.sleepshift.service.LockMonitoringService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                Log.d(TAG, "✅ LockMonitoringService (Foreground) 시작")
            } else {
                startService(serviceIntent)
                Log.d(TAG, "✅ LockMonitoringService 시작")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ LockMonitoringService 시작 실패", e)
            Toast.makeText(this, "모니터링 서비스 시작 실패", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Accessibility 권한 요청 다이얼로그
     */
    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("접근성 권한 필요")
            .setMessage("다른 앱 차단 기능을 사용하려면 접근성 서비스를 활성화해야 합니다.\n\n" +
                    "📱 설정 방법:\n" +
                    "1. 설정 앱 열기\n" +
                    "2. 접근성 메뉴 진입\n" +
                    "3. '다운로드한 앱' 또는 '설치된 서비스' 찾기\n" +
                    "4. 'Dozeo 수면 잠금' 또는 'SleepShift' 찾아서 활성화\n\n" +
                    "활성화 후 앱으로 돌아와 다시 시도해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "접근성 설정에서 'Dozeo 수면 잠금'을 활성화해주세요", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "설정 화면 열기 실패", e)
                    Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "접근성 권한 없이는 다른 앱 차단 기능을 사용할 수 없습니다", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }


    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        // ⭐ 코인 업데이트 (알람 변경 후 돌아왔을 때 반영)
        val sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 0)
        tvPawCoinCount.text = currentCoins.toString()

        Log.d(TAG, "onResume - 코인 업데이트: $currentCoins")
    }

    companion object {
        private const val TAG = "NightRoutineActivity"
    }
}