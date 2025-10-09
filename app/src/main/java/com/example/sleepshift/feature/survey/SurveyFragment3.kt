package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import com.example.sleepshift.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SurveyFragment3 : Fragment() {

    private var selectedSleepDuration: Int = 8 * 60 // 8시간 (분)
    private var btnSelectTime: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey3, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        btnSelectTime = view.findViewById(R.id.btnSelectTime)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        // 초기 버튼 텍스트 설정
        updateButtonText()

        // 시간 선택 버튼
        btnSelectTime?.setOnClickListener {
            showDurationPicker()
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

    private fun updateButtonText() {
        btnSelectTime?.text = minutesToHHmm(selectedSleepDuration)
    }

    private fun showDurationPicker() {
        TimePickerUtil.showDurationPicker(
            context = requireContext(),
            title = "목표 수면 시간",
            initialMinutes = selectedSleepDuration,
            minHours = 4,
            maxHours = 12
        ) { totalMinutes ->
            selectedSleepDuration = totalMinutes
            updateButtonText()
            updateActivityData()
        }
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.goalSleepDuration = selectedSleepDuration

        android.util.Log.d("SurveyFragment3", "목표 수면시간: $selectedSleepDuration 분 (${minutesToHHmm(selectedSleepDuration)})")
    }

    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}