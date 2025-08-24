package com.example.sleepshift.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.example.sleepshift.MainActivity
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object AlarmScheduler {

    fun scheduleNextBedtimeAlarm(ctx: Context) {
        val ds = DataStoreManager(ctx)
        runBlocking {
            val progressS = ds.progressBedtime.first() ?: return@runBlocking
            val nextTime = TimeUtils.nextOccurrence(TimeUtils.parseHHmm(progressS)).toInstant().toEpochMilli()

            val am = ctx.getSystemService<AlarmManager>() ?: return@runBlocking
            val op = PendingIntent.getBroadcast(
                ctx, 1001, Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactOrFallback(ctx, am, nextTime, op)
            ds.setNextAlarmEpoch(nextTime)
        }
    }

    fun scheduleTomorrowSameTime(ctx: Context) {
        val ds = DataStoreManager(ctx)
        runBlocking {
            val progressS = ds.progressBedtime.first() ?: return@runBlocking
            val next = TimeUtils.nextOccurrence(TimeUtils.parseHHmm(progressS)).plusDays(1).toInstant().toEpochMilli()

            val am = ctx.getSystemService<AlarmManager>() ?: return@runBlocking
            val op = PendingIntent.getBroadcast(
                ctx, 1001, Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactOrFallback(ctx, am, next, op)
            ds.setNextAlarmEpoch(next)
        }
    }

    private fun scheduleExactOrFallback(
        ctx: Context,
        am: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        try {
            // Android 12+는 정확 알람 권한이 있는지 점검
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                    return
                }
                // 권한이 없다면 아래 fallback으로 진행
            } else {
                // 31 미만은 그대로 가능
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                return
            }
        } catch (_: SecurityException) {
            // 아래 fallback으로 진행
        }

        // ✅ Fallback: setAlarmClock (정확/허용됨, 사용자 가시성 필요)
        val contentPi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, contentPi)
        am.setAlarmClock(info, operation)
    }
}
