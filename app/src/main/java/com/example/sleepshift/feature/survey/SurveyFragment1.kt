package com.example.sleepshift.feature.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SurveyFragment1 : Fragment() {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var selectedBedTime: LocalTime = LocalTime.of(4, 0)
    private var selectedWakeTime: LocalTime = LocalTime.of(13, 0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        val btnBedTime = view.findViewById<Button>(R.id.btnBedTime)
        val btnWakeTime = view.findViewById<Button>(R.id.btnWakeTime)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        // 초기 버튼 텍스트 설정
        updateButtonTexts(btnBedTime, btnWakeTime)

        // 취침 시간 선택
        btnBedTime.setOnClickListener {
            showTimePicker(selectedBedTime) { hour, minute ->
                selectedBedTime = LocalTime.of(hour, minute)
                updateButtonTexts(btnBedTime, btnWakeTime)
                updateActivityData()
            }
        }

        // 기상 시간 선택
        btnWakeTime.setOnClickListener {
            showTimePicker(selectedWakeTime) { hour, minute ->
                selectedWakeTime = LocalTime.of(hour, minute)
                updateButtonTexts(btnBedTime, btnWakeTime)
                updateActivityData()
            }
        }

        // 다음 버튼
        btnNext.setOnClickListener {
            updateActivityData()
            (activity as? SurveyActivity)?.moveToNextPage()
        }
    }

    private fun updateButtonTexts(btnBedTime: Button, btnWakeTime: Button) {
        btnBedTime.text = selectedBedTime.format(hhmm)
        btnWakeTime.text = selectedWakeTime.format(hhmm)
    }

    private fun updateActivityData() {
        val surveyActivity = activity as? SurveyActivity
        surveyActivity?.avgBedTime = selectedBedTime
        surveyActivity?.avgWakeTime = selectedWakeTime

        android.util.Log.d("SurveyFragment1", """
            avgBedTime: ${selectedBedTime.format(hhmm)}
            avgWakeTime: ${selectedWakeTime.format(hhmm)}
        """.trimIndent())
    }

    private fun showTimePicker(initialTime: LocalTime, onTimeSelected: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initialTime.hour)
            .setMinute(initialTime.minute)
            .setTitleText("시간 선택")
            .build()

        picker.addOnPositiveButtonClickListener {
            onTimeSelected(picker.hour, picker.minute)
        }

        picker.show(parentFragmentManager, "time_picker")
    }
}