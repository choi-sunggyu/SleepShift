package com.example.sleepshift.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

object DateCalculator { //날짜 계산 유틸리티

    /**
     * 앱 설치일부터 오늘까지의 일수 계산 (자정 기준)
     * 결과: 1일차, 2일차... (0이 아닌 1부터 시작)
     */
    fun calculateDaysSinceInstall(installTimestamp: Long): Int {
        if (installTimestamp == 0L) return 1

        // 타임스탬프를 시스템 기본 타임존의 LocalDate로 변환 (시간 정보 제거, 날짜만 남김)
        val installDate = Instant.ofEpochMilli(installTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val today = LocalDate.now()

        // 두 날짜 사이의 일수 차이 계산 (ChronoUnit 사용으로 루프 제거 및 정확도 향상)
        val daysBetween = ChronoUnit.DAYS.between(installDate, today)

        // 설치 당일이 1일차여야 하므로 +1 (음수 방지를 위해 max 처리)
        return (daysBetween.toInt() + 1).coerceAtLeast(1)
    }

    /**
     * 오늘 자정의 타임스탬프 반환
     * 기존 Calendar 로직 유지하되 java.time과 호환성 고려
     */
    fun getTodayMidnightTimestamp(): Long {
        // LocalDate를 이용해 오늘 자정(Start of Day)의 타임스탬프를 구함
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * 오늘 날짜 문자열 (yyyy-MM-dd)
     */
    fun getTodayDateString(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * 어제 날짜 문자열 (yyyy-MM-dd)
     */
    fun getYesterdayDateString(): String {
        return LocalDate.now()
            .minusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}