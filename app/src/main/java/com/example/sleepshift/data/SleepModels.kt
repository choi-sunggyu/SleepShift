package com.example.sleepshift.data

data class SleepSettings(
    val avgBedTime: String,         // "HH:mm"
    val avgWakeTime: String,        // "HH:mm"
    val goalWakeTime: String,       // "HH:mm"
    val goalSleepDuration: String,  // "HH:mm" (예: "08:00")
    val targetBedtime: String,      // "HH:mm" (goalWake - goalSleepDuration)
    val morningGoal: String,        // 자유 텍스트
    val reasonToChange: String      // 자유 텍스트
)

data class SleepProgress(
    val scheduledBedtime: String,   // "HH:mm" 오늘 권장 취침 시간
    val consecutiveSuccessDays: Int,
    val dailyShiftMinutes: Int,     // 30/40/50/60
    val lastProgressUpdate: String  // "YYYY-MM-DD"
)

data class DailyRecord(
    val lockStartTime: Long,        // 수면 모드 시작 epoch millis
    val success: Boolean
)

data class CheckinRecord(
    val emotion: String,
    val goalAchieved: Boolean
)

data class MorningRoutine(
    val date: String,               // "YYYY-MM-DD"
    val key_task: String
)
