package com.example.sleepshift

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sleepshift.feature.LockScreenActivity

class LockScreenService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "lockscreen_service"
        val channelName = "Lock Screen Background Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("잠금 화면 유지 중")
            .setContentText("앱이 백그라운드에서 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()

        // connectedDevice 대신 mediaPlayback으로 설정 → 불필요한 권한 오류 제거
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lockIntent = Intent(this, LockScreenActivity::class.java)
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(lockIntent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
