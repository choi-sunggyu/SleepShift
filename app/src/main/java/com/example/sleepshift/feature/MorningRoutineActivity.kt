package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityMorningRoutineBinding
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.util.*
import androidx.core.content.edit

class MorningRoutineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMorningRoutineBinding
    private lateinit var consecutiveSuccessManager: ConsecutiveSuccessManager
    private var countDownTimer: CountDownTimer? = null
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val routineCompleted = mutableMapOf(
        1 to false,
        2 to false,
        3 to false
    )

    private val TOTAL_TIME_SECONDS = 180  // 3분 대기
    private val MAX_TIME_SECONDS = 600    // 10분 전체
    private var elapsedSeconds = 0
    private var isUnlockEnabled = false

    private val handler = Handler(Looper.getMainLooper())
    private var returnRunnable: Runnable? = null

    // ⭐⭐⭐ 재알람 트리거 플래그
    private var isGoingToReAlarm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMorningRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // 오늘 이미 했는지 체크
        val today = getTodayDateString()
        val lastCompleted = sharedPreferences.getString("last_routine_completed", "")
        val normalizedLast = normalizeDate(lastCompleted ?: "")

        Log.d("MorningRoutine", "오늘: $today, 마지막완료: $lastCompleted")

        if (today == normalizedLast) {
            Toast.makeText(this, "오늘 모닝 루틴은 이미 완료했습니다", Toast.LENGTH_LONG).show()
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MorningRoutineActivity, "모닝 루틴을 완료해주세요!", Toast.LENGTH_SHORT).show()
            }
        })

        loadRoutineStatus()
        setupUI()
        setupRoutineCards()
        setupUnlockButton()
        startTimer()

        Log.d("MorningRoutine", "모닝루틴 시작!")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_BACK -> {
                Toast.makeText(this, "모닝 루틴을 완료해주세요!", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 홈버튼 감지하면 바로 복귀
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // ⭐⭐⭐ 재알람으로 가는 중이면 복귀 안 함
        if (isGoingToReAlarm) {
            Log.d("MorningRoutine", "재알람 이동 중 - 복귀 안 함")
            return
        }

        Log.d("MorningRoutine", "홈버튼 눌림 - 복귀")

        val intent = Intent(this, MorningRoutineActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        startActivity(intent)
    }

    // 비정상 종료 감지
    override fun onPause() {
        super.onPause()

        // ⭐⭐⭐ 재알람으로 가는 중이면 복귀 안 함
        if (isGoingToReAlarm) {
            Log.d("MorningRoutine", "재알람 이동 중 - 복귀 안 함")
            return
        }

        Log.d("MorningRoutine", "onPause - 복귀 대기")

        // 기존 복귀 취소
        returnRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("MorningRoutine", "기존 복귀 취소")
        }

        // 새로 등록
        returnRunnable = Runnable {
            Log.d("MorningRoutine", "복귀 실행")
            val intent = Intent(this, MorningRoutineActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            returnRunnable = null
        }

        handler.postDelayed(returnRunnable!!, 300)
    }

    override fun onResume() {
        super.onResume()

        // 정상 복귀면 취소
        returnRunnable?.let {
            handler.removeCallbacks(it)
            returnRunnable = null
            Log.d("MorningRoutine", "onResume - 복귀 취소")
        }
    }

    // 날짜 정규화
    private fun normalizeDate(dateString: String): String {
        if (dateString.isEmpty()) return ""

        try {
            val parts = dateString.split("-")
            if (parts.size != 3) return dateString

            val year = parts[0]
            val month = parts[1].padStart(2, '0')
            val day = parts[2].padStart(2, '0')

            return "$year-$month-$day"
        } catch (e: Exception) {
            return dateString
        }
    }

    private fun loadRoutineStatus() {
        val today = getTodayDateString()
        val savedDate = sharedPreferences.getString("routine_date", "")

        if (savedDate != today) {
            resetRoutines()
            sharedPreferences.edit { putString("routine_date", today) }
            Log.d("MorningRoutine", "새로운 날 - 미션 초기화")
        } else {
            routineCompleted[1] = sharedPreferences.getBoolean("routine_1_completed", false)
            routineCompleted[2] = sharedPreferences.getBoolean("routine_2_completed", false)
            routineCompleted[3] = sharedPreferences.getBoolean("routine_3_completed", false)

            if (routineCompleted[1] == true) updateRoutineUI(1, binding.routineCard1, binding.tvRoutine1Status, true)
            if (routineCompleted[2] == true) updateRoutineUI(2, binding.routineCard2, binding.tvRoutine2Status, true)
            if (routineCompleted[3] == true) updateRoutineUI(3, binding.routineCard3, binding.tvRoutine3Status, true)

            Log.d("MorningRoutine", "미션 상태 불러옴: ${routineCompleted.values.count { it }}/3")
        }
    }

    private fun resetRoutines() {
        routineCompleted[1] = false
        routineCompleted[2] = false
        routineCompleted[3] = false

        sharedPreferences.edit().apply {
            putBoolean("routine_1_completed", false)
            putBoolean("routine_2_completed", false)
            putBoolean("routine_3_completed", false)
            apply()
        }
    }

    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun setupUI() {
        val coinCount = sharedPreferences.getInt("paw_coin_count", 10)
        binding.tvCoinCount.text = coinCount.toString()

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        disableUnlockButton()
    }

    private fun disableUnlockButton() {
        binding.btnUnlock.isEnabled = false
        binding.btnUnlock.alpha = 0.5f
        binding.btnUnlock.text = "3분 후 활성화됩니다"
    }

    private fun enableUnlockButton() {
        binding.btnUnlock.isEnabled = true
        binding.btnUnlock.alpha = 1.0f
        binding.btnUnlock.text = "잠금 해제하기"
        isUnlockEnabled = true

        Toast.makeText(this, "잠금 해제 버튼이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
    }

    private fun setupRoutineCards() {
        binding.tvRoutine1Status.setOnClickListener {
            completeRoutine(1, binding.routineCard1, binding.tvRoutine1Status)
        }

        binding.tvRoutine2Status.setOnClickListener {
            completeRoutine(2, binding.routineCard2, binding.tvRoutine2Status)
        }

        binding.tvRoutine3Status.setOnClickListener {
            completeRoutine(3, binding.routineCard3, binding.tvRoutine3Status)
        }
    }

    private fun completeRoutine(routineId: Int, card: CardView, statusButton: android.widget.Button) {
        val isCompleted = routineCompleted[routineId] ?: false

        if (isCompleted) {
            Toast.makeText(this, "이미 완료한 루틴입니다", Toast.LENGTH_SHORT).show()
            return
        }

        routineCompleted[routineId] = true

        sharedPreferences.edit {
            putBoolean("routine_${routineId}_completed", true)
        }

        updateRoutineUI(routineId, card, statusButton, true)

        // 애니메이션
        card.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                card.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()

        val routineNames = mapOf(
            1 to "물 한 잔 마시기",
            2 to "햇빛 보기",
            3 to "오늘 꿈 쓰기"
        )
        Toast.makeText(this, "✓ ${routineNames[routineId]} 완료!", Toast.LENGTH_SHORT).show()

        checkAllRoutinesCompleted()
    }

    private fun updateRoutineUI(
        routineId: Int,
        card: CardView,
        statusButton: android.widget.Button,
        isCompleted: Boolean
    ) {
        if (isCompleted) {
            card.setCardBackgroundColor(getColor(R.color.routine_completed_bg))
            statusButton.text = "✓ 완료"
            statusButton.setTextColor(getColor(R.color.routine_completed_text))
            statusButton.setBackgroundColor(getColor(android.R.color.transparent))
        } else {
            card.setCardBackgroundColor(getColor(android.R.color.white))
            statusButton.text = "터치!"
            statusButton.setTextColor(getColor(R.color.routine_incomplete_text))
        }
    }

    private fun checkAllRoutinesCompleted() {
        val allCompleted = routineCompleted.values.all { it }

        if (allCompleted) {
            Toast.makeText(
                this,
                "🎉 모든 루틴 완료! 3분 후 잠금 해제할 수 있습니다",
                Toast.LENGTH_LONG
            ).show()

            binding.btnUnlock.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(300)
                .withEndAction {
                    binding.btnUnlock.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(300)
                        .start()
                }
                .start()
        }
    }

    private fun setupUnlockButton() {
        binding.btnUnlock.setOnClickListener {
            if (!isUnlockEnabled) {
                Toast.makeText(this, "아직 활성화되지 않았습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            attemptUnlock()
        }
    }

    // 타이머
    private fun startTimer() {
        countDownTimer = object : CountDownTimer(MAX_TIME_SECONDS * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++

                if (elapsedSeconds <= TOTAL_TIME_SECONDS) {
                    // 첫 3분: 카운트다운
                    val remainingSeconds = TOTAL_TIME_SECONDS - elapsedSeconds
                    binding.tvTimer.text = formatTime(remainingSeconds)
                    binding.tvTimer.setTextColor(getColor(R.color.timer_normal))

                } else {
                    // 3분 후: 버튼 활성화
                    if (!isUnlockEnabled) {
                        enableUnlockButton()
                    }

                    // 3~10분: 남은 시간 표시
                    val remainingSeconds = MAX_TIME_SECONDS - elapsedSeconds

                    if (remainingSeconds > 60) {
                        binding.tvTimer.text = formatTime(remainingSeconds)
                        binding.tvTimer.setTextColor(getColor(R.color.timer_warning))
                    } else if (remainingSeconds > 0) {
                        binding.tvTimer.text = formatTime(remainingSeconds)
                        binding.tvTimer.setTextColor(getColor(android.R.color.holo_red_light))
                    } else {
                        binding.tvTimer.text = "0:00"
                        binding.tvTimer.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }
            }

            override fun onFinish() {
                triggerReAlarm()
            }
        }.start()
    }

    // 시간 포맷
    private fun formatTime(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun attemptUnlock() {
        val allCompleted = routineCompleted.values.all { it }

        if (!allCompleted) {
            val completedCount = routineCompleted.values.count { it }
            Toast.makeText(
                this,
                "모든 루틴을 완료해주세요! ($completedCount/3)",
                Toast.LENGTH_SHORT
            ).show()

            shakeIncompleteCards()
            return
        }

        unlockSuccess()
    }

    private fun shakeIncompleteCards() {
        val cards = listOf(
            binding.routineCard1 to 1,
            binding.routineCard2 to 2,
            binding.routineCard3 to 3
        )

        cards.forEach { (card, id) ->
            if (routineCompleted[id] == false) {
                card.animate()
                    .translationX(-10f)
                    .setDuration(50)
                    .withEndAction {
                        card.animate()
                            .translationX(10f)
                            .setDuration(50)
                            .withEndAction {
                                card.animate()
                                    .translationX(0f)
                                    .setDuration(50)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
        }
    }

    // 모닝루틴 완료!
    private fun unlockSuccess() {
        countDownTimer?.cancel()

        // 연속 성공 체크
        consecutiveSuccessManager.checkAndRecordSuccess()

        // 첫 시도인지 확인
        val isFirstTry = sharedPreferences.getBoolean("is_first_alarm_try", false)

        // 보상: 첫 시도 2개, 재알람 0개
        val coinReward = if (isFirstTry) 2 else 0

        if (coinReward > 0) {
            addPawCoins(coinReward)
            Log.d("MorningRoutine", "첫 시도 성공! 코인 +$coinReward")
        } else {
            Log.d("MorningRoutine", "재알람 완료 - 보상 없음")
        }

        // 플래그 초기화
        sharedPreferences.edit {
            putBoolean("is_first_alarm_try", false)
            remove("morning_routine_start_time")
        }

        // 오늘 완료 기록
        val today = getTodayDateString()
        Log.d("MorningRoutine", "완료날짜: $today")
        sharedPreferences.edit {
            putString("last_routine_completed", today)
        }

        resetRoutines()

        val message = if (coinReward > 0) {
            "✨ 모닝 루틴 완료!\n곰젤리 +${coinReward}개 획득!"
        } else {
            "✨ 모닝 루틴 완료!\n(재알람으로 보상 없음)"
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun addPawCoins(amount: Int) {
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 10)
        val newCount = currentCoins + amount

        sharedPreferences.edit {
            putInt("paw_coin_count", newCount)
        }

        Log.d("MorningRoutine", "코인 $amount 개 득! 총: $newCount")
    }

    // ⭐⭐⭐ 10분 초과시 재알람
    private fun triggerReAlarm() {
        countDownTimer?.cancel()

        // ⭐⭐⭐ 재알람으로 가는 중 플래그 설정
        isGoingToReAlarm = true

        // 재알람 플래그
        sharedPreferences.edit {
            putBoolean("is_first_alarm_try", false)
            remove("morning_routine_start_time")
        }

        Toast.makeText(
            this,
            "⏰ 10분이 경과했습니다.\n잠시 후 다시 알람이 울립니다.",
            Toast.LENGTH_LONG
        ).show()

        Log.d("MorningRoutine", "재알람 트리거 - AlarmActivity로 이동")

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, com.example.sleepshift.feature.alarm.AlarmActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()

        returnRunnable?.let {
            handler.removeCallbacks(it)
            returnRunnable = null
        }

        Log.d("MorningRoutine", "모닝루틴 종료")
    }
}