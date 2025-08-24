package com.example.sleepshift

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.data.DataStoreManager
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

class OnboardingActivity : AppCompatActivity() {

    private val flexibleTimeFmt: DateTimeFormatter by lazy {
        // 1:5, 01:5, 1:05, 01:05 등 유연하게 허용
        DateTimeFormatterBuilder()
            .parseLenient()
            .appendValue(java.time.temporal.ChronoField.HOUR_OF_DAY, 1, 2, java.time.format.SignStyle.NORMAL)
            .appendLiteral(':')
            .appendValue(java.time.temporal.ChronoField.MINUTE_OF_HOUR, 1, 2, java.time.format.SignStyle.NORMAL)
            .toFormatter(Locale.getDefault())
            .withResolverStyle(ResolverStyle.LENIENT)
    }
    private val strictHHmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val rgReason = findViewById<RadioGroup>(R.id.rgReason)
        val etBeforeBed = findViewById<EditText>(R.id.etBeforeBed)
        val etSleepTime = findViewById<EditText>(R.id.etSleepTime)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // 00:00 ~ 23:59 형태 유도용 간단 포맷터 (자동 콜론 삽입)
        attachTimeMask(etSleepTime)

        btnNext.setOnClickListener {
            val reasonId = rgReason.checkedRadioButtonId
            if (reasonId == -1) {
                toast("사유를 선택해주세요")
                return@setOnClickListener
            }
            val reasonText = findViewById<RadioButton>(reasonId).text.toString()
            val beforeBed = etBeforeBed.text.toString().trim()
            val sleepRaw = etSleepTime.text.toString().trim()

            val normalized = normalizeTimeOrNull(sleepRaw)
            if (normalized == null) {
                toast("취침 시간을 HH:mm 형식으로 입력해주세요 (예: 01:30)")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val ds = DataStoreManager(this@OnboardingActivity)
                ds.setOnboardingCompleted(true)
                ds.setReason(reasonText)
                ds.setBeforeBedText(beforeBed)
                ds.setReportedSleepTime(normalized)
                toast("저장되었습니다")

                // 온보딩 종료 → 메인으로 이동
                val next = Intent(this@OnboardingActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(next)
                finish()
            }
        }
    }

    /** 사용자 입력을 24시간 HH:mm으로 정규화 (유효하지 않으면 null) */
    private fun normalizeTimeOrNull(input: String): String? {
        return try {
            val t = LocalTime.parse(input, flexibleTimeFmt) // 1:5 -> 01:05 등 허용
            t.format(strictHHmm)
        } catch (e: Exception) {
            null
        }
    }

    /** 숫자 입력 시 자동으로 콜론을 넣어주는 간단 마스크(최대 5글자: HH:mm) */
    private fun attachTimeMask(editText: EditText) {
        editText.filters = arrayOf(InputFilter.LengthFilter(5))
        editText.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEditing) return
                isEditing = true
                val digits = s.toString().replace("[^0-9]".toRegex(), "")
                val formatted = when {
                    digits.length >= 3 -> digits.substring(0, 2) + ":" + digits.substring(2, minOf(4, digits.length))
                    digits.length >= 1 -> digits
                    else -> ""
                }
                editText.setText(formatted)
                editText.setSelection(formatted.length)
                isEditing = false
            }
        })
        if (editText.hint.isNullOrBlank()) {
            editText.hint = "예: 01:30"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
