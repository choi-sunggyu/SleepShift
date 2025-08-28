package com.example.sleepshift.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            AlarmScheduler.scheduleNextBedtimeAlarm(context)
        }
    }
}
