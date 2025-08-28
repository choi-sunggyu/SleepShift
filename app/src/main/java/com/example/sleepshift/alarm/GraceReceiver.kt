package com.example.sleepshift.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sleepshift.R
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.NotificationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class GraceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ds = DataStoreManager(context)
        runBlocking {
            val started = ds.sleepStarted.first()
            if (!started) {
                ds.setConsecutiveSuccessDays(0)
                ds.setStepMinutes(30)

                NotificationUtils.ensureChannel(context)
                val n = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
                    // .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle("오늘은 패스되었습니다")
                    .setContentText("정각 5분 내 수면 시작 확인이 없어 내일 다시 시도할게요.")
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(5002, n)

                AlarmScheduler.scheduleTomorrowSameTime(context)
            }
        }
    }
}
