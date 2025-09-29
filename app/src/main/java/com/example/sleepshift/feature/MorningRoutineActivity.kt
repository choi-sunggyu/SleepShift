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

class MorningRoutineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMorningRoutineBinding
    private lateinit var consecutiveSuccessManager: ConsecutiveSuccessManager
    private var countDownTimer: CountDownTimer? = null

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

        consecutiveSuccessManager = ConsecutiveSuccessManager(this)

        setupUI()
        setupRoutineCards()
        setupUnlockButton()
        startTimer()
    }

    private fun setupUI() {
        // 코인 개수 표시
        val coinCount = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
            .getInt("paw_coin_count", 130)
        binding.tvCoinCount.text = coinCount.toString()

        // 설정 버튼
        binding.btnSettings.setOnClickListener {
            // 설정 화면으로 이동 (필요시)
        }
    }

    private fun setupRoutineCards() {
        // 루틴 1: 물 한 잔 마시기
        binding.routineCard1.setOnClickListener {
            toggleRoutine(1, binding.routineCard1, binding.tvRoutine1Status)
        }

        // 루틴 2: 햇빛 보기
        binding.routineCard2.setOnClickListener {
            toggleRoutine(2, binding.routineCard2, binding.tvRoutine2Status)
        }

        // 루틴 3: 오늘 꿈 쓰기
        binding.routineCard3.setOnClickListener {
            toggleRoutine(3, binding.routineCard3, binding.tvRoutine3Status)
        }
    }

    private fun toggleRoutine(routineId: Int, card: CardView, statusText: android.widget.TextView) {
        val isCompleted = routineCompleted[routineId] ?: false

        if (!isCompleted) {
            // 완료 처리
            routineCompleted[routineId] = true
            card.setCardBackgroundColor(getColor(R.color.routine_completed_bg))
            statusText.text = "완료!"
            statusText.setTextColor(getColor(R.color.routine_completed_text))

            // 체크 애니메이션
            card.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    card.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()

            Toast.makeText(this, "루틴 완료!", Toast.LENGTH_SHORT).show()
        } else {
            // 완료 취소
            routineCompleted[routineId] = false
            card.setCardBackgroundColor(getColor(android.R.color.white))
            statusText.text = "터치시"
            statusText.setTextColor(getColor(R.color.routine_incomplete_text))
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

    override fun onBackPressed() {
        super.onBackPressed()
        // 뒤로가기 비활성화
        Toast.makeText(this, "모닝 루틴을 완료해주세요!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}