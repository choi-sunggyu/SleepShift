package com.example.sleepshift.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 날짜 및 시간 계산 유틸리티 (시스템 기본 타임존 기준)
 * 기존 KstTime을 범용적으로 수정
 */
object DateTimeUtils {
    // [수정] Asia/Seoul 고정 대신 시스템 기본 타임존 사용 (해외 사용자 대응 및 AlarmManager와 동기화)
    private val ZONE: ZoneId get() = ZoneId.systemDefault()

    private val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun todayYmd(): String = LocalDate.now(ZONE).format(YMD)
    fun yesterdayYmd(): String = LocalDate.now(ZONE).minusDays(1).format(YMD)

    /**
     * "HH:mm" 문자열 파싱
     */
    fun parseHHmm(s: String): LocalTime {
        return try {
            LocalTime.parse(s, HHMM)
        } catch (e: Exception) {
            LocalTime.of(0, 0) // 파싱 실패 시 기본값
        }
    }

    /**
     * "HH:mm" -> 자정 기준 분(0~1439)으로 변환
     */
    fun hhmmToMinutes(s: String): Int = parseHHmm(s).let { it.hour * 60 + it.minute }

    /**
     * 분(Int) -> "HH:mm" 변환
     * 음수 입력 시에도 하루 전으로 자동 순환 계산 (예: -10분 -> 23:50)
     */
    fun minutesToHHmm(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        val h = m / 60
        val mm = m % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, mm)
    }

    /**
     * 특정 시간(HH:mm)에 분 더하기/빼기
     */
    fun addMinutes(hhmm: String, delta: Int): String =
        minutesToHHmm(hhmmToMinutes(hhmm) + delta)

    /**
     * 목표 기상 시간과 수면 시간을 기반으로 취침 시간 계산
     * @param goalWake 목표 기상 시간 (HH:mm)
     * @param goalSleepDuration 목표 수면 시간 (HH:mm 형식, 예: "07:30"은 7시간 30분 의미)
     */
    fun computeTargetBedtime(goalWake: String, goalSleepDuration: String): String {
        // 시간 - 시간 계산이지만, goalSleepDuration을 '시간 양'으로 해석하여 계산
        val t = hhmmToMinutes(goalWake) - hhmmToMinutes(goalSleepDuration)
        return minutesToHHmm(t)
    }

    /**
     * 지금(System Zone) 기준으로 다음 hh:mm의 epochMillis 반환
     * 알람 설정 시 매우 유용
     */
    fun nextOccurrenceEpoch(hhmm: String): Long {
        val now = ZonedDateTime.now(ZONE)
        val t = parseHHmm(hhmm)

        // 오늘 해당 시간 설정
        var zdt = now.withHour(t.hour).withMinute(t.minute).withSecond(0).withNano(0)

        // 이미 지났다면 내일로 설정
        if (!zdt.isAfter(now)) {
            zdt = zdt.plusDays(1)
        }

        return zdt.toInstant().toEpochMilli()
    }

    /**
     * 다음 목표 시간까지 남은 초(Seconds) 계산
     * UI에서 "남은 시간" 카운트다운 할 때 사용
     */
    fun secondsUntil(hhmm: String): Long {
        val now = ZonedDateTime.now(ZONE).toInstant().toEpochMilli()
        val tgt = nextOccurrenceEpoch(hhmm)
        val diff = (tgt - now) / 1000
        return if (diff < 0) 0 else diff
    }
}