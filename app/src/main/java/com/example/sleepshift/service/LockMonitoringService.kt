package com.example.sleepshift.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sleepshift.R
import com.example.sleepshift.feature.LockScreenActivity
import kotlinx.coroutines.*

class LockMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isMonitoring = true

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val channelId = "lock_monitoring_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "수면 모드 활성",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "잠금 화면이 활성화되어 있습니다"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("수면 모드 활성")
            .setContentText("잠금 화면이 활성화되어 있습니다")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(9999, notification)
        android.util.Log.d("LockMonitoring", "Foreground Service 시작됨")
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive && isMonitoring && isLockScreenActive()) {
                delay(1000) // 1초마다 체크

                // LockScreen이 활성화되어 있는지 확인
                if (!isLockScreenInForeground()) {
                    // 다른 앱이 포그라운드에 있으면 LockScreen으로 복귀
                    bringLockScreenToFront()
                }
            }
        }
    }

    private fun isLockScreenActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    private fun isLockScreenInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 이상은 제한적이므로 항상 true 반환 (Screen Pinning에 의존)
                true
            } else {
                @Suppress("DEPRECATION")
                val tasks = activityManager.getRunningTasks(1)
                if (tasks.isNotEmpty()) {
                    val topActivity = tasks[0].topActivity
                    topActivity?.className?.contains("LockScreenActivity") == true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LockMonitoring", "포그라운드 체크 실패: ${e.message}")
            true
        }
    }

    private fun bringLockScreenToFront() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            android.util.Log.d("LockMonitoring", "LockScreen으로 복귀")
        } catch (e: Exception) {
            android.util.Log.e("LockMonitoring", "LockScreen 복귀 실패: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 서비스가 종료되면 자동 재시작
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        serviceScope.cancel()
        android.util.Log.d("LockMonitoring", "Service 종료됨")
    }
}