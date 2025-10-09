package com.example.sleepshift.feature.survey

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import com.example.sleepshift.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalTime

object TimePickerUtil {

    /**
     * 수면 시간(시간:분) 선택 다이얼로그
     * @param context Context
     * @param title 다이얼로그 제목
     * @param initialMinutes 초기 시간 (분 단위)
     * @param onTimeSelected 선택 완료 콜백 (분 단위)
     */
    fun showDurationPicker(
        context: Context,
        title: String = "시간 선택",
        initialMinutes: Int = 480, // 기본 8시간
        minHours: Int = 4,
        maxHours: Int = 12,
        onTimeSelected: (totalMinutes: Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_duration_picker, null)
        val npH = dialogView.findViewById<NumberPicker>(R.id.npHours)
        val npM = dialogView.findViewById<NumberPicker>(R.id.npMinutes)

        val initialHours = initialMinutes / 60
        val initialMins = initialMinutes % 60

        npH.minValue = minHours
        npH.maxValue = maxHours
        npH.value = initialHours.coerceIn(npH.minValue, npH.maxValue)
        npH.wrapSelectorWheel = true

        val minuteValues = arrayOf("00", "30")
        npM.minValue = 0
        npM.maxValue = minuteValues.lastIndex
        npM.displayedValues = minuteValues
        npM.value = if (initialMins >= 30) 1 else 0
        npM.wrapSelectorWheel = true

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("확인") { dlg, _ ->
                val h = npH.value
                val m = if (npM.value == 1) 30 else 0
                onTimeSelected(h * 60 + m)
                dlg.dismiss()
            }
            .setNegativeButton("취소") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    /**
     * 취침/기상 시간 선택 다이얼로그 (24시간 형식)
     * @param context Context
     * @param title 다이얼로그 제목
     * @param initialTime 초기 시간
     * @param onTimeSelected 선택 완료 콜백
     */
    fun showTimePicker(
        context: Context,
        title: String = "시간 선택",
        initialTime: LocalTime = LocalTime.of(0, 0),
        onTimeSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
        val npHour = dialogView.findViewById<NumberPicker>(R.id.npHour)
        val npMinute = dialogView.findViewById<NumberPicker>(R.id.npMinute)

        // 시간 설정 (0-23)
        npHour.minValue = 0
        npHour.maxValue = 23
        npHour.value = initialTime.hour
        npHour.wrapSelectorWheel = true
        npHour.setFormatter { value -> "%02d".format(value) }

        // 분 설정 (0-59, 1분 단위)
        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.value = initialTime.minute
        npMinute.wrapSelectorWheel = true
        npMinute.setFormatter { value -> "%02d".format(value) }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("확인") { dlg, _ ->
                val h = npHour.value
                val m = npMinute.value
                onTimeSelected(h, m)
                dlg.dismiss()
            }
            .setNegativeButton("취소") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    /**
     * 분을 HH:mm 형식으로 변환
     */
    fun minutesToHHmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}