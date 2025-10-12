package com.example.sleepshift.util

import java.util.*

object DateCalculator { //날짜 계산 유틸리티

    /**
     * 앱 설치일부터 오늘까지의 일수 계산 (자정 기준)
     */
    fun calculateDaysSinceInstall(installTimestamp: Long): Int {
        if (installTimestamp == 0L) return 1

        val installCalendar = Calendar.getInstance().apply {
            timeInMillis = installTimestamp
        }

        val todayCalendar = Calendar.getInstance()

        val installYear = installCalendar.get(Calendar.YEAR)
        val installDayOfYear = installCalendar.get(Calendar.DAY_OF_YEAR)

        val todayYear = todayCalendar.get(Calendar.YEAR)
        val todayDayOfYear = todayCalendar.get(Calendar.DAY_OF_YEAR)

        // 같은 해인 경우
        if (installYear == todayYear) {
            return (todayDayOfYear - installDayOfYear) + 1
        }

        // 다른 해인 경우
        var daysDiff = 0
        val tempCalendar = Calendar.getInstance().apply {
            timeInMillis = installTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (tempCalendar.get(Calendar.YEAR) < todayYear ||
            (tempCalendar.get(Calendar.YEAR) == todayYear &&
                    tempCalendar.get(Calendar.DAY_OF_YEAR) < todayDayOfYear)) {
            daysDiff++
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return daysDiff + 1
    }

    /**
     * 오늘 자정의 타임스탬프 반환
     */
    fun getTodayMidnightTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 오늘 날짜 문자열 (yyyy-MM-dd)
     */
    fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 어제 날짜 문자열 (yyyy-MM-dd)
     */
    fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }
        return String.format(
            "%d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}