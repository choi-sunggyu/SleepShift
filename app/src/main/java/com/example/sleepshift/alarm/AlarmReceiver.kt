package com.example.sleepshift.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sleepshift.MainActivity
import com.example.sleepshift.R
import com.example.sleepshift.util.NotificationUtils

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        NotificationUtils.ensureChannel(context)

        val type = intent?.getStringExtra("type") ?: "SLEEP"

        val contentPi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            // 아이콘이 없으면 아래 임시 시스템 아이콘으로 교체 가능:
            // .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setSmallIcon(R.drawable.ic_alarm)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (type == "PRESLEEP") {
            builder
                .setContentTitle("취침 준비 알림")
                .setContentText("30분 뒤에 잘 시간이에요. 가볍게 준비를 시작해요.")
                .setContentIntent(contentPi)
        } else {
            val sleepNow = PendingIntent.getBroadcast(
                context, 2001,
                Intent(context, ActionReceiver::class.java).apply {
                    action = "com.example.sleepshift.ACTION_SLEEP_NOW"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notYet = PendingIntent.getBroadcast(
                context, 2002,
                Intent(context, ActionReceiver::class.java).apply {
                    action = "com.example.sleepshift.ACTION_NOT_YET"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setContentTitle("잘 시간입니다")
                .setContentText("지금 주무시겠습니까?")
                .setContentIntent(contentPi)
                .addAction(0, "네, 지금 잘래요", sleepNow)
                .addAction(0, "아직이에요", notYet)
        }

        val notificationId = if (type == "PRESLEEP") 5000 else 5001

        // ✅ 권한 체크 (API 33+), 없으면 조용히 종료
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.w("AlarmReceiver", "POST_NOTIFICATIONS not granted; skipping notify()")
                return
            }
        }

        // ✅ 예외 대비
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (se: SecurityException) {
            Log.e("AlarmReceiver", "SecurityException while notifying: ${se.message}")
        }
    }
}
