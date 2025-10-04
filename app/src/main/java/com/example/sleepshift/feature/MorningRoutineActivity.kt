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

    // 루틴 완료 상태
    private val routineCompleted = mutableMapOf(
        1 to false,
        2 to false,
        3 to false
    )

    // 타이머 설정
    private val TOTAL_TIME_SECONDS = 180 // 3분 = 180초
    private val MAX_TIME_SECONDS = 420 // 7분 = 420초
    private var remainingSeconds = TOTAL_TIME_SECONDS
    private var elapsedSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMorningRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        // ⭐ 새로운 뒤로가기 처리 방식
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 뒤로가기 비활성화
                Toast.makeText(this@MorningRoutineActivity, "모닝 루틴을 완료해주세요!", Toast.LENGTH_SHORT).show()
            }
        })

        // 저장된 미션 상태 불러오기
        loadRoutineStatus()

        setupUI()
        setupRoutineCards()
        setupUnlockButton()
        startTimer()
    }

    /**
     * ⭐ 오늘 날짜의 미션 완료 상태 불러오기
     */
    private fun loadRoutineStatus() {
        val today = getTodayDateString()
        val savedDate = sharedPreferences.getString("routine_date", "")

        // 날짜가 바뀌었으면 미션 초기화
        if (savedDate != today) {
            resetRoutines()
            sharedPreferences.edit().putString("routine_date", today).apply()
            android.util.Log.d("MorningRoutine", "새로운 날 - 미션 초기화")
        } else {
            // 오늘 날짜면 저장된 상태 불러오기
            routineCompleted[1] = sharedPreferences.getBoolean("routine_1_completed", false)
            routineCompleted[2] = sharedPreferences.getBoolean("routine_2_completed", false)
            routineCompleted[3] = sharedPreferences.getBoolean("routine_3_completed", false)

            // UI 업데이트
            if (routineCompleted[1] == true) updateRoutineUI(1, binding.routineCard1, binding.tvRoutine1Status, true)
            if (routineCompleted[2] == true) updateRoutineUI(2, binding.routineCard2, binding.tvRoutine2Status, true)
            if (routineCompleted[3] == true) updateRoutineUI(3, binding.routineCard3, binding.tvRoutine3Status, true)

            android.util.Log.d("MorningRoutine", "미션 상태 불러옴: ${routineCompleted.values.count { it }}/3 완료")
        }
    }

    /**
     * ⭐ 미션 상태 초기화
     */
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

    /**
     * ⭐ 오늘 날짜 문자열 반환
     */
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)+1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun setupUI() {
        // 코인 개수 표시 (10개 기본값으로 수정)
        val coinCount = sharedPreferences.getInt("paw_coin_count", 10)
        binding.tvCoinCount.text = coinCount.toString()

        // 설정 버튼
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRoutineCards() {
        // 루틴 1: 물 한 잔 마시기
        binding.tvRoutine1Status.setOnClickListener {
            completeRoutine(1, binding.routineCard1, binding.tvRoutine1Status)
        }

        // 루틴 2: 햇빛 보기
        binding.tvRoutine2Status.setOnClickListener {
            completeRoutine(2, binding.routineCard2, binding.tvRoutine2Status)
        }

        // 루틴 3: 오늘 꿈 쓰기
        binding.tvRoutine3Status.setOnClickListener {
            completeRoutine(3, binding.routineCard3, binding.tvRoutine3Status)
        }
    }

    /**
     * ⭐ 미션 완료 처리 (한 번 완료하면 취소 불가)
     */
    private fun completeRoutine(routineId: Int, card: CardView, statusButton: android.widget.Button) {
        val isCompleted = routineCompleted[routineId] ?: false

        if (isCompleted) {
            // 이미 완료된 미션은 클릭해도 반응 없음
            Toast.makeText(this, "이미 완료한 루틴입니다", Toast.LENGTH_SHORT).show()
            return
        }

        // ⭐ 완료 처리
        routineCompleted[routineId] = true

        // ⭐ SharedPreferences에 저장
        sharedPreferences.edit()
            .putBoolean("routine_${routineId}_completed", true)
            .apply()

        // UI 업데이트
        updateRoutineUI(routineId, card, statusButton, true)

        // 완료 애니메이션
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

        // 완료 메시지
        val routineNames = mapOf(
            1 to "물 한 잔 마시기",
            2 to "햇빛 보기",
            3 to "오늘 꿈 쓰기"
        )
        Toast.makeText(this, "✓ ${routineNames[routineId]} 완료!", Toast.LENGTH_SHORT).show()

        // 모든 미션 완료 체크
        checkAllRoutinesCompleted()
    }

    /**
     * ⭐ 미션 UI 업데이트
     */
    private fun updateRoutineUI(
        routineId: Int,
        card: CardView,
        statusButton: android.widget.Button,
        isCompleted: Boolean
    ) {
        if (isCompleted) {
            // 완료 상태
            card.setCardBackgroundColor(getColor(R.color.routine_completed_bg))
            statusButton.text = "✓ 완료"
            statusButton.setTextColor(getColor(R.color.routine_completed_text))
            statusButton.setBackgroundColor(getColor(android.R.color.transparent))
        } else {
            // 미완료 상태
            card.setCardBackgroundColor(getColor(android.R.color.white))
            statusButton.text = "터치!"
            statusButton.setTextColor(getColor(R.color.routine_incomplete_text))
        }
    }

    /**
     * ⭐ 모든 미션 완료 시 자동 안내
     */
    private fun checkAllRoutinesCompleted() {
        val allCompleted = routineCompleted.values.all { it }

        if (allCompleted) {
            Toast.makeText(
                this,
                "🎉 모든 루틴 완료! 이제 잠금 해제할 수 있습니다",
                Toast.LENGTH_LONG
            ).show()

            // 잠금 해제 버튼 강조 애니메이션
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
            attemptUnlock()
        }
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(MAX_TIME_SECONDS * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++
                remainingSeconds = TOTAL_TIME_SECONDS - elapsedSeconds

                if (remainingSeconds > 0) {
                    // 3분 이내: 남은 시간 표시
                    binding.tvTimer.text = "${remainingSeconds}s"
                    binding.tvTimer.setTextColor(getColor(R.color.timer_normal))
                } else {
                    // 3분 초과: 경과 시간 표시 (빨간색)
                    val overTime = elapsedSeconds - TOTAL_TIME_SECONDS
                    binding.tvTimer.text = "+${overTime}s"
                    binding.tvTimer.setTextColor(getColor(R.color.timer_warning))
                }
            }

            override fun onFinish() {
                // 7분 초과 - 재알람
                triggerReAlarm()
            }
        }.start()
    }

    private fun attemptUnlock() {
        // 모든 루틴 완료 체크
        val allCompleted = routineCompleted.values.all { it }

        if (!allCompleted) {
            val completedCount = routineCompleted.values.count { it }
            Toast.makeText(
                this,
                "모든 루틴을 완료해주세요! ($completedCount/3)",
                Toast.LENGTH_SHORT
            ).show()

            // 미완료 카드 흔들기 애니메이션
            shakeIncompleteCards()
            return
        }

        // 잠금 해제 성공
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

        // 알람 해제 기록
        consecutiveSuccessManager.recordAlarmDismissed()

        // 성공 메시지
        val timeTaken = elapsedSeconds
        val message = if (timeTaken <= TOTAL_TIME_SECONDS) {
            "완벽합니다! ${timeTaken}초 만에 완료"
        } else {
            "루틴 완료! ${timeTaken}초 소요"
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // 홈 화면으로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1000)
    }

    private fun triggerReAlarm() {
        countDownTimer?.cancel()

        Toast.makeText(
            this,
            "7분이 초과되었습니다.\n다시 알람이 울립니다.",
            Toast.LENGTH_LONG
        ).show()

        // 알람 액티비티로 다시 이동
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