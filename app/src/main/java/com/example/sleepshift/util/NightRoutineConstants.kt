package com.example.sleepshift.util

object NightRoutineConstants {
    // 코인 비용
    const val ALARM_CHANGE_COST = 2

    // 보상
    const val PERFECT_BEDTIME_REWARD = 2
    const val GOOD_BEDTIME_REWARD = 1

    // 허용 시간 (분)
    const val EARLY_TOLERANCE_MINUTES = 60  // 취침 60분 전까지
    const val LATE_TOLERANCE_MINUTES = 60   // 취침 60분 후까지

    // 기분 개수
    const val MOOD_COUNT = 7
    const val DEFAULT_MOOD_POSITION = 2  // 중간: 평온

    // 애니메이션
    const val INDICATOR_SIZE_DP = 8
    const val INDICATOR_MARGIN_DP = 4
    const val PAGE_SCALE_MIN = 0.85f
    const val PAGE_SCALE_MAX = 1.0f
}