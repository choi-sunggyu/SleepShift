package com.example.sleepshift.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sleepshift.R
import com.example.sleepshift.util.NotificationUtils

class BedtimeAlarmReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationUtils.ensureChannel(context)

        val doneIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "com.example.sleepshift.ACTION_DONE"
        }
        val skipIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "com.example.sleepshift.ACTION_SKIP"
        }

        val donePi = PendingIntent.getBroadcast(
            context, 2001, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipPi = PendingIntent.getBroadcast(
            context, 2002, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("취침 시간 알림")
            .setContentText("지금부터 휴대폰을 내려놓고 준비해볼까요?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.done), donePi)
            .addAction(0, context.getString(R.string.skip), skipPi)

        NotificationManagerCompat.from(context).notify(5001, builder.build())
    }
}
