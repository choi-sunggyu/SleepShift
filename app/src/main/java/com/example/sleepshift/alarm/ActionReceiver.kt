package com.example.sleepshift.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.TimeUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ds = DataStoreManager(context)
        runBlocking {
            val action = intent.action ?: return@runBlocking
            val step = ds.stepMinutes.first()
            val progressS = ds.progressBedtime.first() ?: return@runBlocking
            val targetS = ds.targetBedtime.first() ?: return@runBlocking

            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            val progress = TimeUtils.parseHHmm(progressS)
            val target = TimeUtils.parseHHmm(targetS)

            when (action) {
                "com.example.sleepshift.ACTION_DONE" -> {
                    val newTime = progress.minusMinutes(step.toLong())
                    val nextProgress = if (newTime.isBefore(target) || newTime == target) target else newTime
                    ds.setProgressBedtime(nextProgress.format(fmt))
                    AlarmScheduler.scheduleNextBedtimeAlarm(context)
                }
                "com.example.sleepshift.ACTION_SKIP" -> {
                    AlarmScheduler.scheduleTomorrowSameTime(context)
                }
            }
        }
    }
}
