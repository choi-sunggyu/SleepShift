package com.example.sleepshift.service

import android.app.*
import android.app.usage.UsageStatsManager
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

    /**
     * ⭐⭐⭐ 개선된 모니터링 로직 - 더 빠른 복귀
     */
    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive && isMonitoring && isLockScreenActive()) {
                delay(300) // ⭐ 0.3초마다 체크 (기존 0.5초에서 단축)

                if (!isLockScreenInForeground()) {
                    android.util.Log.d("LockMonitoring", "⚠️ 다른 앱 감지! 복귀 시도")

                    // ⭐⭐⭐ 연속 3번 복귀 시도 (더 확실한 복귀)
                    repeat(3) {
                        bringLockScreenToFront()
                        delay(100)
                    }
                }
            }
        }
    }

    private fun isLockScreenActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    /**
     * ⭐ 포그라운드 앱 체크
     */
    private fun isLockScreenInForeground(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                isLockScreenInForegroundUsingUsageStats()
            } else {
                @Suppress("DEPRECATION")
                isLockScreenInForegroundLegacy()
            }
        } catch (e: Exception) {
            android.util.Log.e("LockMonitoring", "포그라운드 체크 실패: ${e.message}")
            false
        }
    }

    /**
     * ⭐ UsageStatsManager로 포그라운드 앱 확인
     */
    private fun isLockScreenInForegroundUsingUsageStats(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false

        val currentTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 3000,
            currentTime
        )

        if (stats.isNullOrEmpty()) {
            android.util.Log.w("LockMonitoring", "⚠️ UsageStats 권한이 없을 수 있음")
            return false
        }

        val recentApp = stats.maxByOrNull { it.lastTimeUsed }
        val foregroundPackage = recentApp?.packageName

        // ⭐ 자기 앱이면 어떤 Activity든 허용 (AlarmActivity도 포함)
        val isOwnApp = foregroundPackage == packageName

        if (!isOwnApp) {
            android.util.Log.d("LockMonitoring", "포그라운드 앱: $foregroundPackage")
        }

        return isOwnApp
    }

    /**
     * ⭐ ActivityManager로 포그라운드 앱 확인 (Android 5.0 이하)
     */
    @Suppress("DEPRECATION")
    private fun isLockScreenInForegroundLegacy(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = activityManager.getRunningTasks(1)

        if (tasks.isNotEmpty()) {
            val topActivity = tasks[0].topActivity
            return topActivity?.packageName == packageName
        }

        return false
    }

    /**
     * ⭐ LockScreen으로 복귀
     */
    private fun bringLockScreenToFront() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            android.util.Log.d("LockMonitoring", "✅ LockScreen으로 복귀")
        } catch (e: Exception) {
            android.util.Log.e("LockMonitoring", "❌ LockScreen 복귀 실패: ${e.message}")
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