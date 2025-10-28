package com.example.sleepshift.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sleepshift.feature.LockScreenActivity
import com.example.sleepshift.R
import kotlinx.coroutines.*

class LockMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    private val allowedPackages = setOf(
        "com.example.sleepshift", // 자신의 앱
        "com.android.systemui"    // 시스템 UI
    )

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "✅ LockMonitoringService onCreate")

        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            android.util.Log.d(TAG, "✅ Foreground Service 시작 성공")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Foreground Service 시작 실패", e)
            stopSelf()
            return
        }

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "onStartCommand 호출")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        android.util.Log.d(TAG, "✅ 앱 모니터링 시작")

        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    val lockPrefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)
                    val isLocked = lockPrefs.getBoolean("isLocked", false)

                    if (isLocked) {
                        val currentApp = getCurrentForegroundApp()

                        if (currentApp != null) {
                            android.util.Log.d(TAG, "현재 실행 중인 앱: $currentApp")

                            // 현재 실행 중인 앱이 허용되지 않은 앱이면 LockScreenActivity 실행
                            if (!allowedPackages.contains(currentApp)) {
                                android.util.Log.w(TAG, "⚠️ 허용되지 않은 앱 감지: $currentApp")
                                showLockScreen()
                            }
                        }
                    } else {
                        android.util.Log.d(TAG, "잠금 상태 아님 - 모니터링 중지 예정")
                        stopSelf()
                        break
                    }

                    delay(1000) // 1초마다 체크
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "모니터링 중 오류", e)
                    delay(1000)
                }
            }
        }
    }

    private fun getCurrentForegroundApp(): String? {
        try {
            // 권한 체크
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )

                if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                    android.util.Log.w(TAG, "⚠️ 사용 통계 권한 없음")
                    return null
                }
            }

            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 10, // 최근 10초
                time
            )

            if (stats != null && stats.isNotEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                return sortedStats.firstOrNull()?.packageName
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "앱 감지 실패", e)
        }
        return null
    }

    private fun showLockScreen() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            android.util.Log.d(TAG, "✅ LockScreenActivity 실행")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ LockScreenActivity 실행 실패", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "수면 잠금 모니터링",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "수면 시간 동안 앱 사용을 모니터링합니다"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            android.util.Log.d(TAG, "✅ 알림 채널 생성 완료")
        }
    }

    private fun createNotification(): Notification {
        // ✅ 아이콘이 없을 경우를 대비한 안전한 처리
        val iconResId = try {
            R.drawable.ic_notification
        } catch (e: Exception) {
            android.R.drawable.ic_lock_idle_lock // 시스템 기본 아이콘 사용
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("수면 잠금 활성화")
            .setContentText("수면 시간입니다. 다른 앱 사용이 제한됩니다.")
            .setSmallIcon(iconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "✅ LockMonitoringService onDestroy")
        monitoringJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "LockMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lock_monitoring_channel"
    }
}