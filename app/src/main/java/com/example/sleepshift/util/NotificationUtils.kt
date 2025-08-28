package com.example.sleepshift.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.sleepshift.R

object NotificationUtils {
    const val CHANNEL_ID = "bedtime_channel"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            ch.description = ctx.getString(R.string.channel_desc)
            nm.createNotificationChannel(ch)
        }
    }
}