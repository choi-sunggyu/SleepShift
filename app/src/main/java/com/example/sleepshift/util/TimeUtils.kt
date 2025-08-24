package com.example.sleepshift.util

import android.content.Context
import com.example.sleepshift.data.DataStoreManager
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseHHmm(s: String) = LocalTime.parse(s, HHMM)
    fun hhmm(h: Int, m: Int) = LocalTime.of(h, m).format(HHMM)
    fun minutesToHHmm(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

    fun computeTargetBedtime(wakeHHmm: String, sleepMin: Int): String {
        val wake = parseHHmm(wakeHHmm)
        return wake.minusMinutes(sleepMin.toLong()).format(HHMM)
    }

    fun nextOccurrence(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val today = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    suspend fun formatNextAlarm(ctx: Context): String {
        val epoch = DataStoreManager(ctx).nextAlarmEpoch.first() ?: return "-"
        val zdt = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        return "${zdt.toLocalDate()} ${zdt.toLocalTime().format(HHMM)}"
    }
}
