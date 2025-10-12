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

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive && isMonitoring && isLockScreenActive()) {
                delay(1000) // 1초마다 체크

                // LockScreen이 활성화되어 있는지 확인
                if (!isLockScreenInForeground()) {
                    android.util.Log.d("LockMonitoring", "⚠️ 다른 앱 감지! LockScreen으로 복귀 시도")
                    bringLockScreenToFront()
                }
            }
            android.util.Log.d("LockMonitoring", "모니터링 루프 종료")
        }
    }

    private fun isLockScreenActive(): Boolean {
        val sharedPref = getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("lock_screen_active", false)
    }

    /**
     * ⭐ 개선: UsageStatsManager로 포그라운드 앱 체크
     */
    private fun isLockScreenInForeground(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Android 5.1 이상: UsageStatsManager 사용
                isLockScreenInForegroundUsingUsageStats()
            } else {
                // Android 5.0 이하: ActivityManager 사용
                @Suppress("DEPRECATION")
                isLockScreenInForegroundLegacy()
            }
        } catch (e: Exception) {
            android.util.Log.e("LockMonitoring", "포그라운드 체크 실패: ${e.message}")
            // 에러 시 복귀 시도 (안전장치)
            false
        }
    }

    /**
     * ⭐ UsageStatsManager로 포그라운드 앱 확인 (Android 5.1+)
     */
    private fun isLockScreenInForegroundUsingUsageStats(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false

        val currentTime = System.currentTimeMillis()

        // 최근 3초간의 사용 통계 조회
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 3000,
            currentTime
        )

        if (stats.isNullOrEmpty()) {
            android.util.Log.w("LockMonitoring", "⚠️ UsageStats 권한이 없을 수 있음")
            return false
        }

        // 가장 최근에 사용된 앱 찾기
        val recentApp = stats.maxByOrNull { it.lastTimeUsed }
        val foregroundPackage = recentApp?.packageName

        val isLockScreen = foregroundPackage == packageName

        if (!isLockScreen) {
            android.util.Log.d("LockMonitoring", "포그라운드 앱: $foregroundPackage")
        }

        return isLockScreen
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
            return topActivity?.className?.contains("LockScreenActivity") == true
        }

        return false
    }

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