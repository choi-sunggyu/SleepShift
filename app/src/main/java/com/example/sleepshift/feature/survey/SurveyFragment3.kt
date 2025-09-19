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
        val btnSelectTime = view.findViewById<Button>(R.id.btnSelectTime)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        // 초기 버튼 텍스트 설정
        updateButtonText(btnSelectTime)

        // 시간 선택 버튼
        btnSelectTime.setOnClickListener {
            showDurationPicker()
        }

        // 다음 버튼
        btnNext.setOnClickListener {
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage()
        }
    }

    private fun updateButtonText(button: Button) {
        button.text = minutesToHHmm(selectedSleepDuration)
    }

    private fun showDurationPicker() {
        val view = layoutInflater.inflate(R.layout.dialog_duration_picker, null)
        val npH = view.findViewById<NumberPicker>(R.id.npHours)
        val npM = view.findViewById<NumberPicker>(R.id.npMinutes)

        val initialHours = selectedSleepDuration / 60
        val initialMinutes = selectedSleepDuration % 60

        npH.minValue = 4   // 최소 4시간
        npH.maxValue = 12  // 최대 12시간
        npH.value = initialHours.coerceIn(npH.minValue, npH.maxValue)
        npH.wrapSelectorWheel = true

        // 분은 0/30만 제공
        val minuteValues = arrayOf("00", "30")
        npM.minValue = 0
        npM.maxValue = minuteValues.lastIndex
        npM.displayedValues = minuteValues
        npM.value = if (initialMinutes >= 30) 1 else 0
        npM.wrapSelectorWheel = true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("목표 수면 시간")
            .setView(view)
            .setPositiveButton("확인") { dlg, _ ->
                val h = npH.value
                val m = if (npM.value == 1) 30 else 0
                selectedSleepDuration = h * 60 + m
                updateButtonText(view.rootView.findViewById(R.id.btnSelectTime))
                dlg.dismiss()
            }
            .setNegativeButton("취소") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.goalSleepDuration = selectedSleepDuration
    }

    private fun minutesToHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}