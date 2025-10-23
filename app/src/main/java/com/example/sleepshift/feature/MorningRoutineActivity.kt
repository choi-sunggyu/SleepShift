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

    private val TOTAL_TIME_SECONDS = 180  // 3분 (대기 시간)
    private val MAX_TIME_SECONDS = 600    // 10분 (총 시간: 3분 대기 + 7분 활성화)
    private var elapsedSeconds = 0
    private var isUnlockEnabled = false

    // ⭐ Handler 중복 방지
    private val handler = Handler(Looper.getMainLooper())
    private var returnRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMorningRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // ⭐ 오늘 이미 완료했는지 체크
        val today = getTodayDateString()
        val lastCompleted = sharedPreferences.getString("last_routine_completed", "")
        val normalizedLastCompleted = normalizeDate(lastCompleted ?: "")

        Log.d("MorningRoutine", "오늘: $today")
        Log.d("MorningRoutine", "마지막 완료: $lastCompleted")

        if (today == normalizedLastCompleted) {
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

        Log.d("MorningRoutine", "✅ 모닝 루틴 시작")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,  // ⭐ 오버뷰 버튼 차단 추가
            KeyEvent.KEYCODE_BACK -> {
                Toast.makeText(this, "모닝 루틴을 완료해주세요!", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * ⭐⭐⭐ 홈 버튼 감지 (문제 4 해결)
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Log.d("MorningRoutine", "홈 버튼 감지 - 즉시 복귀")

        // ⭐ 즉시 복귀
        val intent = Intent(this, MorningRoutineActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        startActivity(intent)
    }

    /**
     * ⭐⭐⭐ 비정상 종료 감지 (문제 4 해결)
     */
    override fun onPause() {
        super.onPause()

        Log.d("MorningRoutine", "onPause 호출 - 복귀 대기")

        // ⭐ 기존 Runnable 취소
        returnRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("MorningRoutine", "기존 복귀 Runnable 취소됨")
        }

        // ⭐ 새 Runnable 생성
        returnRunnable = Runnable {
            Log.d("MorningRoutine", "복귀 실행")
            val intent = Intent(this, MorningRoutineActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            returnRunnable = null
        }

        // ⭐ 300ms 후 실행
        handler.postDelayed(returnRunnable!!, 300)
    }

    override fun onResume() {
        super.onResume()

        // ⭐ 복귀 Runnable 취소 (정상 복귀)
        returnRunnable?.let {
            handler.removeCallbacks(it)
            returnRunnable = null
            Log.d("MorningRoutine", "onResume - 복귀 Runnable 취소")
        }
    }

    /**
     * 날짜 형식 정규화
     */
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

            Log.d("MorningRoutine", "미션 상태 불러옴: ${routineCompleted.values.count { it }}/3 완료")
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

    /**
     * ⭐⭐⭐ 타이머 시작
     * - 0~3분: 대기 시간 (버튼 비활성화)
     * - 3~10분: 활성화 시간 (7분간 루틴 완료 가능)
     * - 10분 초과: 재알람
     */
    private fun startTimer() {
        countDownTimer = object : CountDownTimer(MAX_TIME_SECONDS * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++

                if (elapsedSeconds <= TOTAL_TIME_SECONDS) {
                    // ⭐ 첫 3분: 카운트다운 (3:00 → 2:59 → ... → 0:00)
                    val remainingSeconds = TOTAL_TIME_SECONDS - elapsedSeconds
                    binding.tvTimer.text = formatTime(remainingSeconds)
                    binding.tvTimer.setTextColor(getColor(R.color.timer_normal))

                } else {
                    // ⭐ 3분 후: 버튼 활성화
                    if (!isUnlockEnabled) {
                        enableUnlockButton()
                    }

                    // ⭐ 3분~10분: 남은 시간 카운트다운 (4:00 → 3:59 → ... → 0:00)
                    val remainingSeconds = MAX_TIME_SECONDS - elapsedSeconds

                    if (remainingSeconds > 60) {
                        // 1분 이상 남음: 일반 표시
                        binding.tvTimer.text = formatTime(remainingSeconds)
                        binding.tvTimer.setTextColor(getColor(R.color.timer_warning))
                    } else if (remainingSeconds > 0) {
                        // 1분 미만: 경고 색상
                        binding.tvTimer.text = formatTime(remainingSeconds)
                        binding.tvTimer.setTextColor(getColor(android.R.color.holo_red_light))
                    } else {
                        // 시간 초과
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

    /**
     * 초를 분:초 형식으로 변환
     */
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

    /**
     * ⭐⭐⭐ 모닝 루틴 완료 (문제 2 해결)
     */
    private fun unlockSuccess() {
        countDownTimer?.cancel()

        consecutiveSuccessManager.recordAlarmDismissed()

        // ⭐⭐⭐ 한 번에 알람 해제했는지 확인 (재울림 없음)
        val isFirstTry = sharedPreferences.getBoolean("is_in_morning_routine", false)

        // ⭐⭐⭐ 보상: 한 번에 해제하면 2개, 재울림 후엔 0개
        val coinReward = if (isFirstTry) 2 else 0

        if (coinReward > 0) {
            addPawCoins(coinReward)
            Log.d("MorningRoutine", "✅ 한 번에 알람 해제 성공! 곰젤리 +$coinReward")
        } else {
            Log.d("MorningRoutine", "⚠️ 재알람 후 완료 - 보상 없음")
        }

        // ⭐ 모닝 루틴 플래그 해제
        sharedPreferences.edit {
            putBoolean("is_in_morning_routine", false)
            remove("morning_routine_start_time")
        }

        // ⭐ 오늘 완료 기록
        val today = getTodayDateString()
        Log.d("MorningRoutine", "완료 날짜 저장: $today")
        sharedPreferences.edit {
            putString("last_routine_completed", today)
        }

        // 미션 초기화 (다음 날을 위해)
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

        Log.d("MorningRoutine", "곰젤리 $amount 개 획득! 총: $newCount")
    }

    /**
     * ⭐ 7분 초과 시 다시 알람
     */
    private fun triggerReAlarm() {
        countDownTimer?.cancel()

        // ⭐ 재알람 플래그 (다음엔 보상 없음)
        sharedPreferences.edit {
            putBoolean("is_in_morning_routine", false)
        }

        Toast.makeText(
            this,
            "⏰ 7분이 경과했습니다.\n잠시 후 다시 알람이 울립니다.",
            Toast.LENGTH_LONG
        ).show()

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

        // ⭐ 복귀 Runnable 정리
        returnRunnable?.let {
            handler.removeCallbacks(it)
            returnRunnable = null
        }

        Log.d("MorningRoutine", "MorningRoutineActivity 종료")
    }
}