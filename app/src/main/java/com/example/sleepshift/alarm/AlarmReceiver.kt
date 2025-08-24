package com.example.sleepshift.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sleepshift.MainActivity
import com.example.sleepshift.R
import com.example.sleepshift.util.NotificationUtils

class AlarmReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent?) {
        // 채널 보장
        NotificationUtils.ensureChannel(context)

        // 알림 터치 시 앱 열기
        val contentPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 액션 인텐트(완료/건너뛰기)
        val donePi = PendingIntent.getBroadcast(
            context, 2001,
            Intent(context, ActionReceiver::class.java).apply {
                action = "com.example.sleepshift.ACTION_DONE"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipPi = PendingIntent.getBroadcast(
            context, 2002,
            Intent(context, ActionReceiver::class.java).apply {
                action = "com.example.sleepshift.ACTION_SKIP"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            // 프로젝트에 ic_alarm 벡터가 없으면 아래 한 줄을 android 기본 아이콘으로 대체:
            // .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("취침 시간 알림")
            .setContentText("지금부터 휴대폰을 내려놓고 준비해볼까요?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.done), donePi)
            .addAction(0, context.getString(R.string.skip), skipPi)

        NotificationManagerCompat.from(context).notify(5001, builder.build())
    }
}
