package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class SurveyFragment2 : Fragment() {

    private val selectedReasons = mutableSetOf<String>()
    private val reasonButtons = mutableMapOf<String, TextView>()
    private var customReasonInput: EditText? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        val btnReason1 = view.findViewById<TextView>(R.id.btnReason1)
        val btnReason2 = view.findViewById<TextView>(R.id.btnReason2)
        val btnReason3 = view.findViewById<TextView>(R.id.btnReason3)
        val btnReason4 = view.findViewById<TextView>(R.id.btnReason4)
        val btnReason5 = view.findViewById<TextView>(R.id.btnReason5)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        // 각 선택지에 클릭 리스너 설정
        setupReasonButton(btnReason1, "휴대폰 때문에 늦게 잠드는 게 싫어서")
        setupReasonButton(btnReason2, "일정한 리듬을 만들고 싶어서")
        setupReasonButton(btnReason3, "시차 적응을 하고 싶어서")
        setupReasonButton(btnReason4, "나만의 저녁 루틴을 만들고 싶어서")
        setupReasonButton(btnReason5, "다른 이유 (직접 입력)")

        // XML에서 미리 선택된 항목 처리 (btnReason2가 selected 상태)
        if (btnReason2.isSelected || btnReason2.background.constantState ==
            resources.getDrawable(R.drawable.survey_option_selected, null).constantState) {
            val reason2Text = "일찍한 리듬을 만들고 싶어서"
            selectedReasons.add(reason2Text)
            btnReason2.isSelected = true
        }

        // 이전 버튼
        btnBack.setOnClickListener {
            (activity as? SurveyActivity)?.moveToPreviousPage()
        }

        // 다음 버튼
        btnNext.setOnClickListener {
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage()
        }
    }

    private fun setupReasonButton(button: TextView, reason: String) {
        reasonButtons[reason] = button

        button.setOnClickListener {
            toggleReason(button, reason)
        }
    }

    private fun toggleReason(button: TextView, reason: String) {
        if (selectedReasons.contains(reason)) {
            // 선택 해제
            selectedReasons.remove(reason)
            button.isSelected = false
            button.setBackgroundResource(R.drawable.survey_option_unselected)

            // "직접 입력"인 경우 입력 필드 숨기기
            if (reason == "다른 이유 (직접 입력)") {
                hideCustomInput()
            }
        } else {
            // 선택
            selectedReasons.add(reason)
            button.isSelected = true
            button.setBackgroundResource(R.drawable.survey_option_selected)

            // "직접 입력"인 경우 입력 필드 보이기
            if (reason == "다른 이유 (직접 입력)") {
                showCustomInput(button)
            }
        }
    }

    private fun showCustomInput(afterView: TextView) {
        if (customReasonInput != null) return // 이미 있으면 무시

        val parent = afterView.parent as? LinearLayout ?: return

        customReasonInput = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx()
                bottomMargin = 12.dpToPx()
            }
            hint = "직접 입력해주세요"
            setBackgroundResource(R.drawable.survey_edittext_background)
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            textSize = 16f
            maxLines = 3
        }

        val index = parent.indexOfChild(afterView)
        parent.addView(customReasonInput, index + 1)
    }

    private fun hideCustomInput() {
        customReasonInput?.let { input ->
            val parent = input.parent as? ViewGroup
            parent?.removeView(input)
            customReasonInput = null
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity

        // "직접 입력"이 선택되었고 텍스트가 있으면 그것을 사용
        val customText = customReasonInput?.text?.toString()?.trim()
        val finalReasons = if (!customText.isNullOrEmpty() &&
            selectedReasons.contains("다른 이유 (직접 입력)")) {
            selectedReasons.filter { it != "다른 이유 (직접 입력)" }.toMutableSet().apply {
                add(customText)
            }
        } else {
            selectedReasons.filter { it != "다른 이유 (직접 입력)" }.toSet()
        }

        surveyActivity?.reasonToChange = finalReasons.joinToString(", ")
    }
}