package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.feature.home.HomeActivity

class LoadingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentProgress = 0

    // Views
    private lateinit var tvLoadingTitle: TextView
    private lateinit var circularProgressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvLoadingMessage: TextView

    // 로딩 단계별 메시지
    private val loadingMessages = listOf(
        "데이터 준비중입니다",
        "수면 패턴 분석중입니다",
        "알람 설정중입니다",
        "거의 완료되었습니다"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        initViews()
        setupUI()
        startLoading()
    }

    private fun initViews() {
        tvLoadingTitle = findViewById(R.id.tvLoadingTitle)
        circularProgressBar = findViewById(R.id.circularProgressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
    }

    private fun setupUI() {
        // 사용자 이름 가져오기
        val sharedPreferences = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val userName = sharedPreferences.getString("user_name", "사용자") ?: "사용자"

        tvLoadingTitle.text = "${userName}님의 수면 패턴 생성중"

        // 초기 진행률 설정
        circularProgressBar.progress = 0
        tvProgress.text = "0%"
    }

    private fun startLoading() {
        // 프로그레스바를 0에서 100까지 부드럽게 증가
        val totalDuration = 5000L // 5초
        val updateInterval = 30L // 30ms마다 업데이트
        val totalSteps = (totalDuration / updateInterval).toInt()
        val progressPerStep = 100f / totalSteps

        var currentStep = 0

        val runnable = object : Runnable {
            override fun run() {
                if (currentStep <= totalSteps) {
                    currentProgress = (progressPerStep * currentStep).toInt()

                    // 프로그레스바 업데이트
                    circularProgressBar.progress = currentProgress
                    tvProgress.text = "${currentProgress}%"

                    // 메시지 업데이트 (25%, 50%, 75%, 100%)
                    updateLoadingMessage(currentProgress)

                    currentStep++
                    handler.postDelayed(this, updateInterval)
                } else {
                    // 로딩 완료
                    finishLoading()
                }
            }
        }

        handler.post(runnable)
    }

    private fun updateLoadingMessage(progress: Int) {
        val messageIndex = when {
            progress < 25 -> 0
            progress < 50 -> 1
            progress < 75 -> 2
            else -> 3
        }

        if (tvLoadingMessage.text != loadingMessages[messageIndex]) {
            tvLoadingMessage.text = loadingMessages[messageIndex]

            // 텍스트 변경시 페이드 애니메이션
            tvLoadingMessage.alpha = 0f
            tvLoadingMessage.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    private fun finishLoading() {
        // 완료 후 잠시 대기
        handler.postDelayed({
            if (!isFinishing) {
                // 홈 화면으로 이동
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

                // 페이드 아웃 효과
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 로딩 중에는 뒤로가기 비활성화
    }
}