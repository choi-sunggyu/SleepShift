package com.example.sleepshift.feature.survey

import android.content.Context
import android.widget.NumberPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object TimePickerUtil {

    /**
     * 수면 시간(Duration) 선택 - 몇 시간 동안
     */
    fun showDurationPicker(
        context: Context,
        title: String,
        initialMinutes: Int,
        minHours: Int = 4,
        maxHours: Int = 12,
        onTimeSelected: (totalMinutes: Int) -> Unit
    ) {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(com.example.sleepshift.R.layout.dialog_time_picker, null)

        val hourPicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.hourPicker)
        val minutePicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.minutePicker)

        // Hour picker 설정
        hourPicker.minValue = minHours
        hourPicker.maxValue = maxHours
        hourPicker.value = initialMinutes / 60

        // Minute picker 설정 (0, 15, 30, 45)
        val minuteValues = arrayOf("00", "15", "30", "45")
        minutePicker.minValue = 0
        minutePicker.maxValue = minuteValues.size - 1
        minutePicker.displayedValues = minuteValues
        minutePicker.value = (initialMinutes % 60) / 15

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val hours = hourPicker.value
                val minutes = minuteValues[minutePicker.value].toInt()
                val totalMinutes = hours * 60 + minutes
                onTimeSelected(totalMinutes)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐ 알람 시간 선택 - 몇 시에 (24시간 형식)
     */
    fun showAlarmTimePicker(
        context: Context,
        title: String,
        initialTime: String, // "HH:mm" 형식
        onTimeSelected: (hour: Int, minute: Int, timeString: String) -> Unit
    ) {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(com.example.sleepshift.R.layout.dialog_time_picker, null)

        val hourPicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.hourPicker)
        val minutePicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.minutePicker)

        // 초기 시간 파싱
        val timeParts = initialTime.split(":")
        val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
        val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        // Hour picker 설정 (0~23시)
        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        hourPicker.value = initialHour
        hourPicker.setFormatter { value -> String.format("%02d시", value) }

        // Minute picker 설정 (0, 15, 30, 45)
        val minuteValues = arrayOf("00", "15", "30", "45")
        minutePicker.minValue = 0
        minutePicker.maxValue = minuteValues.size - 1

        // 가장 가까운 15분 단위로 설정
        val minuteIndex = when {
            initialMinute < 8 -> 0   // 0~7분 → 00분
            initialMinute < 23 -> 1  // 8~22분 → 15분
            initialMinute < 38 -> 2  // 23~37분 → 30분
            initialMinute < 53 -> 3  // 38~52분 → 45분
            else -> 0                // 53~59분 → 다음 시간 00분
        }
        minutePicker.value = minuteIndex
        minutePicker.displayedValues = minuteValues.map { "${it}분" }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val hour = hourPicker.value
                val minute = minuteValues[minutePicker.value].toInt()
                val timeString = String.format("%02d:%02d", hour, minute)
                onTimeSelected(hour, minute, timeString)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐ 취침 시간 선택 - 몇 시에 (저녁~새벽)
     */
    fun showBedtimePicker(
        context: Context,
        title: String,
        initialTime: String,
        onTimeSelected: (hour: Int, minute: Int, timeString: String) -> Unit
    ) {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(com.example.sleepshift.R.layout.dialog_time_picker, null)

        val hourPicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.hourPicker)
        val minutePicker = dialogView.findViewById<NumberPicker>(com.example.sleepshift.R.id.minutePicker)

        val timeParts = initialTime.split(":")
        val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 22
        val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        // Hour picker 설정 (20~23시, 0~4시)
        val hourValues = (20..23).toList() + (0..4).toList()
        val hourDisplayValues = hourValues.map { String.format("%02d시", it) }.toTypedArray()

        hourPicker.minValue = 0
        hourPicker.maxValue = hourValues.size - 1
        hourPicker.displayedValues = hourDisplayValues
        hourPicker.value = hourValues.indexOf(initialHour).takeIf { it >= 0 } ?: 2 // 기본 22시

        // Minute picker 설정
        val minuteValues = arrayOf("00", "15", "30", "45")
        minutePicker.minValue = 0
        minutePicker.maxValue = minuteValues.size - 1

        val minuteIndex = when {
            initialMinute < 8 -> 0
            initialMinute < 23 -> 1
            initialMinute < 38 -> 2
            initialMinute < 53 -> 3
            else -> 0
        }
        minutePicker.value = minuteIndex
        minutePicker.displayedValues = minuteValues.map { "${it}분" }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val hour = hourValues[hourPicker.value]
                val minute = minuteValues[minutePicker.value].toInt()
                val timeString = String.format("%02d:%02d", hour, minute)
                onTimeSelected(hour, minute, timeString)
            }
            .setNegativeButton("취소", null)
            .show()
    }
}