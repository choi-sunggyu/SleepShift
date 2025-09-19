package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class SurveyFragment2 : Fragment() {

    private val selectedReasons = mutableSetOf<String>()
    private val reasonButtons = mutableListOf<TextView>()

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
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        reasonButtons.addAll(listOf(btnReason1, btnReason2, btnReason3, btnReason4, btnReason5))

        // 각 선택지에 클릭 리스너 설정
        setupReasonButton(btnReason1, "유대한 대문에 놓게 잘드는 게 싫어서")
        setupReasonButton(btnReason2, "일찍한 리듬을 만들고 싶어서")
        setupReasonButton(btnReason3, "시간 절약을 하고 싶어서")
        setupReasonButton(btnReason4, "나만의 저녁 루틴을 만들고 싶어서")
        setupReasonButton(btnReason5, "다른 이유 (직접 입력)")

        // 다음 버튼
        btnNext.setOnClickListener {
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage()
        }
    }

    private fun setupReasonButton(button: TextView, reason: String) {
        button.setOnClickListener {
            if (selectedReasons.contains(reason)) {
                selectedReasons.remove(reason)
                button.isSelected = false
                button.setBackgroundResource(R.drawable.survey_option_unselected)
            } else {
                selectedReasons.add(reason)
                button.isSelected = true
                button.setBackgroundResource(R.drawable.survey_option_selected)
            }
        }
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.reasonToChange = selectedReasons.joinToString(", ")
    }
}