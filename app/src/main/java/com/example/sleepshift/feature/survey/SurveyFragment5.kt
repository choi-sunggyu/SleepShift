package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class SurveyFragment5 : Fragment() {

    private var morningGoalText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey5, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        val etMorningGoal = view.findViewById<EditText>(R.id.etMorningGoal)
        val btnFinish = view.findViewById<Button>(R.id.btnFinish)

        // 텍스트 변경 리스너
        etMorningGoal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                morningGoalText = etMorningGoal.text.toString()
                updateActivityData()
            }
        }

        // 완료 버튼
        btnFinish.setOnClickListener {
            morningGoalText = etMorningGoal.text.toString()
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage() // 이것이 finishSurvey()를 호출함
        }
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.morningGoal = morningGoalText
    }
}