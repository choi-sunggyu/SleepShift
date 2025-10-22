package com.example.sleepshift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sleepshift.R
import kotlinx.coroutines.*

class LockMonitoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var running = true

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()

        // 🔹 주기적 모니터링 루프
        scope.launch {
            while (running) {
                delay(1000)
                // 잠금 상태 체크 또는 Wake 유지 로직 추가 가능
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "sleepshift_lock_channel"
        val channelName = "SleepShift Lock Monitoring"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8 이상 NotificationChannel 등록
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN // 거의 안 보이게
            ).apply {
                description = "수면 잠금 모드 활성화 상태를 표시합니다."
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 객체 생성
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("수면 잠금 모드 활성")
            .setContentText("Dozeo가 백그라운드에서 잠금을 유지하고 있습니다.")
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        // Foreground 실행
        startForeground(9999, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
