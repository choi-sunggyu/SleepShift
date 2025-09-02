package com.example.sleepshift.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

object KstTime {
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    private val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun todayYmd(): String = LocalDate.now(ZONE).format(YMD)
    fun yesterdayYmd(): String = LocalDate.now(ZONE).minusDays(1).format(YMD)

    fun parseHHmm(s: String): LocalTime = LocalTime.parse(s, HHMM)
    fun hhmmToMinutes(s: String): Int = parseHHmm(s).let { it.hour * 60 + it.minute }
    fun minutesToHHmm(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        val h = m / 60; val mm = m % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, mm)
    }
    fun addMinutes(hhmm: String, delta: Int): String =
        minutesToHHmm(hhmmToMinutes(hhmm) + delta)

    fun computeTargetBedtime(goalWake: String, goalSleep: String): String {
        val t = hhmmToMinutes(goalWake) - hhmmToMinutes(goalSleep)
        return minutesToHHmm(t)
    }

    /** 지금(KST) 기준으로 다음 hh:mm의 epochMillis */
    fun nextOccurrenceEpoch(hhmm: String): Long {
        val now = ZonedDateTime.now(ZONE)
        val t = parseHHmm(hhmm)
        var zdt = now.withHour(t.hour).withMinute(t.minute).withSecond(0).withNano(0)
        if (!zdt.isAfter(now)) zdt = zdt.plusDays(1)
        return zdt.toInstant().toEpochMilli()
    }

    fun secondsUntil(hhmm: String): Long {
        val now = ZonedDateTime.now(ZONE).toInstant().toEpochMilli()
        val tgt = nextOccurrenceEpoch(hhmm)
        val diff = (tgt - now) / 1000
        return if (diff < 0) 0 else diff
    }
}
