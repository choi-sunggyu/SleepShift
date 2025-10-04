package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.sleepshift.R
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SurveyFragment4 : Fragment() {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var selectedWakeTime: LocalTime = LocalTime.of(7, 0)
    private var btnSelectTime: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey4, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        btnSelectTime = view.findViewById(R.id.btnSelectTime)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        // 초기 버튼 텍스트 설정
        updateButtonText()

        // 시간 선택 버튼
        btnSelectTime?.setOnClickListener {
            showTimePicker()
        }

        // 다음 버튼
        btnNext.setOnClickListener {
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage()
        }
    }

    private fun updateButtonText() {
        btnSelectTime?.text = selectedWakeTime.format(hhmm)
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedWakeTime.hour)
            .setMinute(selectedWakeTime.minute)
            .setTitleText("기상 시간 선택")
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedWakeTime = LocalTime.of(picker.hour, picker.minute)
            updateButtonText() // 수정: Fragment의 버튼 참조
            updateActivityData()
        }

        picker.show(parentFragmentManager, "wake_time_picker")
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.goalWakeTime = selectedWakeTime

        android.util.Log.d("SurveyFragment4", "목표 기상시간: ${selectedWakeTime.format(hhmm)}")
    }
}