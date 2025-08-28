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

    private const val REQ_PRE = 1101
    private const val REQ_SLEEP = 1102
    private const val REQ_GRACE = 1103

    fun scheduleNextBedtimeAlarm(ctx: Context) {
        val ds = DataStoreManager(ctx)
        runBlocking {
            val progressS = ds.progressBedtime.first() ?: return@runBlocking
            val progress = TimeUtils.parseHHmm(progressS)
            val sleepZdt = TimeUtils.nextOccurrence(progress)
            val preZdt = sleepZdt.minusMinutes(30)
            val graceZdt = sleepZdt.plusMinutes(5)

            val am = ctx.getSystemService<AlarmManager>() ?: return@runBlocking

            am.setExactOrFallback(ctx, preZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_PRE, AlarmReceiver::class.java, "PRESLEEP"))
            am.setExactOrFallback(ctx, sleepZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_SLEEP, AlarmReceiver::class.java, "SLEEP"))
            am.setExactOrFallback(ctx, graceZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_GRACE, GraceReceiver::class.java, null))

            ds.setNextAlarmEpoch(sleepZdt.toInstant().toEpochMilli())
            ds.setSleepStarted(false)
            ds.setSleepStartedAt(null)
        }
    }

    fun scheduleTomorrowSameTime(ctx: Context) {
        val ds = DataStoreManager(ctx)
        runBlocking {
            val progressS = ds.progressBedtime.first() ?: return@runBlocking
            val sleepZdt = TimeUtils.nextOccurrence(TimeUtils.parseHHmm(progressS)).plusDays(1)
            val preZdt = sleepZdt.minusMinutes(30)
            val graceZdt = sleepZdt.plusMinutes(5)

            val am = ctx.getSystemService<AlarmManager>() ?: return@runBlocking

            am.setExactOrFallback(ctx, preZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_PRE, AlarmReceiver::class.java, "PRESLEEP"))
            am.setExactOrFallback(ctx, sleepZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_SLEEP, AlarmReceiver::class.java, "SLEEP"))
            am.setExactOrFallback(ctx, graceZdt.toInstant().toEpochMilli(),
                broadcast(ctx, REQ_GRACE, GraceReceiver::class.java, null))

            ds.setNextAlarmEpoch(sleepZdt.toInstant().toEpochMilli())
            ds.setSleepStarted(false)
            ds.setSleepStartedAt(null)
        }
    }

    private fun broadcast(ctx: Context, req: Int, clazz: Class<*>, type: String?): PendingIntent {
        val i = Intent(ctx, clazz).apply { if (type != null) putExtra("type", type) }
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun AlarmManager.setExactOrFallback(
        ctx: Context,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (this.canScheduleExactAlarms()) {
                    this.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                } else {
                    val contentPi = PendingIntent.getActivity(
                        ctx, 0,
                        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    this.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, contentPi), operation)
                }
            } else {
                this.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (_: SecurityException) {
            val contentPi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            this.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, contentPi), operation)
        }
    }
}
