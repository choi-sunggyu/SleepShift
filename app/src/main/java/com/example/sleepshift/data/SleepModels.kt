package com.example.sleepshift.data

data class SleepSettings(
    val avgBedTime: String,        // "HH:mm"
    val avgWakeTime: String,       // "HH:mm"
    val goalWakeTime: String,      // "HH:mm"
    val goalSleepDuration: String, // "HH:mm"
    val targetBedtime: String,     // "HH:mm" = goalWakeTime - goalSleepDuration
    val morningGoal: String,
    val reasonToChange: String
)

data class SleepProgress(
    val scheduledBedtime: String,      // 오늘 권장 취침
    val consecutiveSuccessDays: Int,   // 연속 성공일
    val dailyShiftMinutes: Int,        // 30 / 40 / 50 / 60
    val lastProgressUpdate: String     // "YYYY-MM-DD" (KST)
)

data class DailyRecord(
    val lockStartTime: Long,   // 수면모드 시작 epochMillis
    val success: Boolean       // 목표 기상까지 유지 성공 여부
)

data class CheckinRecord(
    val emotion: String,
    val goalAchieved: Boolean
)

data class MorningRoutine(
    val date: String,    // "YYYY-MM-DD"
    val key_task: String
)
