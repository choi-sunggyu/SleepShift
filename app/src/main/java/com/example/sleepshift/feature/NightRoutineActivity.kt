package com.example.sleepshift.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sleepshift.R

class NightRoutineActivity : AppCompatActivity() {

    private lateinit var tvSelectedEmotion: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnStartSleep: LinearLayout

    // 감정 선택 버튼들
    private lateinit var btnEmotionExcellent: LinearLayout
    private lateinit var btnEmotionGood: LinearLayout
    private lateinit var btnEmotionNeutral: LinearLayout
    private lateinit var btnEmotionBad: LinearLayout

    private var selectedEmotion = "좋음" // 기본 선택

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night_routine)

        initViews()
        setupClickListeners()
        setupEmotionSelection()
    }

    private fun initViews() {
        tvSelectedEmotion = findViewById(R.id.tvSelectedEmotion)
        btnSettings = findViewById(R.id.btnSettings)
        btnStartSleep = findViewById(R.id.btnStartSleep)

        btnEmotionExcellent = findViewById(R.id.btnEmotionExcellent)
        btnEmotionGood = findViewById(R.id.btnEmotionGood)
        btnEmotionNeutral = findViewById(R.id.btnEmotionNeutral)
        btnEmotionBad = findViewById(R.id.btnEmotionBad)
    }

    private fun setupClickListeners() {
        btnSettings.setOnClickListener {
            // 설정 화면으로 이동
            openSettings()
        }

        btnStartSleep.setOnClickListener {
            startSleepMode()
        }
    }

    private fun setupEmotionSelection() {
        // 감정 선택 버튼들 설정
        btnEmotionExcellent.setOnClickListener {
            selectEmotion("매우 좋음", btnEmotionExcellent)
        }

        btnEmotionGood.setOnClickListener {
            selectEmotion("좋음", btnEmotionGood)
        }

        btnEmotionNeutral.setOnClickListener {
            selectEmotion("보통", btnEmotionNeutral)
        }

        btnEmotionBad.setOnClickListener {
            selectEmotion("나쁨", btnEmotionBad)
        }

        // 기본 선택 상태 설정
        selectEmotion("좋음", btnEmotionGood)
    }

    private fun selectEmotion(emotion: String, selectedButton: LinearLayout) {
        selectedEmotion = emotion
        tvSelectedEmotion.text = emotion

        // 모든 버튼을 기본 상태로 리셋
        resetEmotionButtons()

        // 선택된 버튼만 하이라이트
        selectedButton.background = ContextCompat.getDrawable(this, R.drawable.emotion_selected_background)

        // 선택된 버튼의 텍스트 색상 변경
        val textView = selectedButton.getChildAt(1) as TextView
        textView.setTextColor(ContextCompat.getColor(this, R.color.button_primary))
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun resetEmotionButtons() {
        val buttons = listOf(btnEmotionExcellent, btnEmotionGood, btnEmotionNeutral, btnEmotionBad)

        buttons.forEach { button ->
            button.background = ContextCompat.getDrawable(this, R.drawable.emotion_unselected_background)
            val textView = button.getChildAt(1) as TextView
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            textView.typeface = android.graphics.Typeface.DEFAULT
        }
    }

    private fun startSleepMode() {
        // 감정 데이터 저장
        saveEmotionData()

        // 화면 밝기 최소화
        dimScreen()

        // 수면 모드 시작 안내
        Toast.makeText(this, "수면 모드가 시작됩니다. 좋은 밤 되세요! 🌙", Toast.LENGTH_LONG).show()

        // 3초 후 화면 잠금 시도
        window.decorView.postDelayed({
            lockScreen()
        }, 3000)
    }

    private fun saveEmotionData() {
        // SharedPreferences 또는 데이터베이스에 감정 데이터 저장
        val sharedPref = getSharedPreferences("sleep_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("today_emotion", selectedEmotion)
            putLong("sleep_start_time", System.currentTimeMillis())
            apply()
        }
    }

    private fun dimScreen() {
        // 화면 밝기를 최소로 설정
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 0.01f // 최소 밝기
        window.attributes = layoutParams

        // 화면이 꺼지지 않도록 설정 (선택사항)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun lockScreen() {
        try {
            // 디바이스 관리자 권한이 있는 경우 화면 잠금
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            devicePolicyManager.lockNow()
        } catch (e: Exception) {
            // 권한이 없는 경우 대체 방법
            alternativeLockScreen()
        }
    }

    private fun alternativeLockScreen() {
        // 대체 방법: 화면을 완전히 어둡게 하고 홈으로 이동
        try {
            // 홈 화면으로 이동
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)

            // 또는 전원 버튼 누르기 시뮬레이션 (권한 필요)
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // powerManager.goToSleep(SystemClock.uptimeMillis()) // API 28부터 권한 필요

        } catch (e: Exception) {
            Toast.makeText(this, "화면 잠금을 위해 전원 버튼을 눌러주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSettings() {
        // 설정 화면으로 이동
        Toast.makeText(this, "설정 화면으로 이동", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        // 뒤로가기 버튼으로 홈 화면으로 돌아가기
        super.onBackPressed()
        finish()
    }
}