package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.sleepshift.R
import com.example.sleepshift.databinding.ActivityMorningRoutineBinding
import com.example.sleepshift.feature.home.HomeActivity
import com.example.sleepshift.util.ConsecutiveSuccessManager
import java.util.*

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

    private val TOTAL_TIME_SECONDS = 180
    private val MAX_TIME_SECONDS = 420
    private var elapsedSeconds = 0
    private var isUnlockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMorningRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)

        // ⭐ 오늘 이미 완료했는지 체크
        val today = getTodayDateString()
        val lastCompleted = sharedPreferences.getString("last_routine_completed", "")

        // ⭐ 마이그레이션: 기존 "2025-10-4" 형식을 "2025-10-04"로 변환
        val normalizedLastCompleted = normalizeDate(lastCompleted ?: "")

        // ⭐ 디버깅 로그
        android.util.Log.d("MorningRoutine", "오늘: $today")
        android.util.Log.d("MorningRoutine", "마지막 완료: $lastCompleted")
        android.util.Log.d("MorningRoutine", "같은가? ${today == lastCompleted}")

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
    }

    /**
     * ⭐ 날짜 형식 정규화 (2025-10-4 → 2025-10-04)
     */
    private fun normalizeDate(dateString: String): String {
        if (dateString.isEmpty()) return ""

        try {
            val parts = dateString.split("-")
            if (parts.size != 3) return dateString

            val year = parts[0]
            val month = parts[1].padStart(2, '0')  // 한 자리면 앞에 0 추가
            val day = parts[2].padStart(2, '0')    // 한 자리면 앞에 0 추가

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
            sharedPreferences.edit().putString("routine_date", today).apply()
            android.util.Log.d("MorningRoutine", "새로운 날 - 미션 초기화")
        } else {
            routineCompleted[1] = sharedPreferences.getBoolean("routine_1_completed", false)
            routineCompleted[2] = sharedPreferences.getBoolean("routine_2_completed", false)
            routineCompleted[3] = sharedPreferences.getBoolean("routine_3_completed", false)

            if (routineCompleted[1] == true) updateRoutineUI(1, binding.routineCard1, binding.tvRoutine1Status, true)
            if (routineCompleted[2] == true) updateRoutineUI(2, binding.routineCard2, binding.tvRoutine2Status, true)
            if (routineCompleted[3] == true) updateRoutineUI(3, binding.routineCard3, binding.tvRoutine3Status, true)

            android.util.Log.d("MorningRoutine", "미션 상태 불러옴: ${routineCompleted.values.count { it }}/3 완료")
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

        sharedPreferences.edit()
            .putBoolean("routine_${routineId}_completed", true)
            .apply()

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

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(MAX_TIME_SECONDS * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++

                if (elapsedSeconds <= TOTAL_TIME_SECONDS) {
                    val remainingSeconds = TOTAL_TIME_SECONDS - elapsedSeconds
                    binding.tvTimer.text = "${remainingSeconds}s"
                    binding.tvTimer.setTextColor(getColor(R.color.timer_normal))

                } else {
                    if (!isUnlockEnabled) {
                        enableUnlockButton()
                    }

                    val overTime = elapsedSeconds - TOTAL_TIME_SECONDS
                    binding.tvTimer.text = "+${overTime}s"
                    binding.tvTimer.setTextColor(getColor(R.color.timer_warning))
                }
            }

            override fun onFinish() {
                triggerReAlarm()
            }
        }.start()
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

    private fun unlockSuccess() {
        countDownTimer?.cancel()

        consecutiveSuccessManager.recordAlarmDismissed()

        val timeTaken = elapsedSeconds
        val coinReward = calculateCoinReward(timeTaken)
        addPawCoins(coinReward)

        // ⭐ 오늘 완료 기록
        val today = getTodayDateString()
        android.util.Log.d("MorningRoutine", "완료 날짜 저장: $today")
        sharedPreferences.edit()
            .putString("last_routine_completed", today)
            .apply()

        // 미션 초기화 (다음 날을 위해)
        resetRoutines()

        val message = if (timeTaken <= TOTAL_TIME_SECONDS) {
            "완벽합니다! ${timeTaken}초 만에 완료\n발바닥 코인 ${coinReward}개 획득!"
        } else {
            "루틴 완료! ${timeTaken}초 소요\n발바닥 코인 ${coinReward}개 획득!"
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun calculateCoinReward(timeTaken: Int): Int {
        val currentDay = getCurrentDay()
        val dayBonus = currentDay / 5
        val baseReward = 3
        val speedBonus = if (timeTaken <= TOTAL_TIME_SECONDS) 1 else 0
        return baseReward + dayBonus + speedBonus
    }

    private fun getCurrentDay(): Int {
        return sharedPreferences.getInt("current_day", 1)
    }

    private fun addPawCoins(amount: Int) {
        val currentCoins = sharedPreferences.getInt("paw_coin_count", 10)
        val newCount = currentCoins + amount

        sharedPreferences.edit()
            .putInt("paw_coin_count", newCount)
            .apply()

        android.util.Log.d("MorningRoutine", "발바닥 코인 $amount 개 획득! 총: $newCount")
    }

    private fun triggerReAlarm() {
        countDownTimer?.cancel()

        Toast.makeText(
            this,
            "7분이 초과되었습니다.\n다시 알람이 울립니다.",
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
    }
}