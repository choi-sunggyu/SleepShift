package com.example.sleepshift.feature

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.feature.onboarding.OnboardingActivity

class PermissionActivity : AppCompatActivity() {

    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvExactAlarmStatus: TextView
    private lateinit var btnUsageStats: Button
    private lateinit var btnExactAlarm: Button
    private lateinit var btnComplete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d(TAG, "✅ PermissionActivity 시작")

        initViews()
        setupButtons()
        updatePermissionStatus()

        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun initViews() {
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvExactAlarmStatus = findViewById(R.id.tvExactAlarmStatus)
        btnUsageStats = findViewById(R.id.btnUsageStats)
        btnExactAlarm = findViewById(R.id.btnExactAlarm)
        btnComplete = findViewById(R.id.btnComplete)
    }

    private fun setupButtons() {
        // 사용 정보 접근 권한
        btnUsageStats.setOnClickListener {
            requestUsageStatsPermission()
        }

        // 정확한 알람 권한
        btnExactAlarm.setOnClickListener {
            requestExactAlarmPermission()
        }

        // 완료 버튼
        btnComplete.setOnClickListener {
            if (allPermissionsGranted()) {
                android.util.Log.d(TAG, "✅ 모든 필수 권한 허용됨 - 다음 화면으로 이동")
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                android.util.Log.w(TAG, "⚠️ 필수 권한 미허용")
                Toast.makeText(this, "모든 필수 권한을 허용해주세요", Toast.LENGTH_LONG).show()

                // 어떤 권한이 부족한지 표시
                val missingPermissions = mutableListOf<String>()
                if (!hasUsageStatsPermission()) missingPermissions.add("사용 정보 접근")
                if (!hasExactAlarmPermission()) missingPermissions.add("정확한 알람")

                if (missingPermissions.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "필요한 권한: ${missingPermissions.joinToString(", ")}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 사용 정보 접근 권한 요청
     */
    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "'Dozeo' 또는 'SleepShift'를 찾아서 활성화해주세요",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.d(TAG, "사용 정보 접근 설정 화면 열기")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "사용 정보 접근 설정 화면 열기 실패", e)
            Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 정확한 알람 권한 요청
     */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                android.util.Log.d(TAG, "정확한 알람 설정 화면 열기")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "정확한 알람 설정 화면 열기 실패", e)
                Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ⭐ 모든 필수 권한 확인
     */
    private fun allPermissionsGranted(): Boolean {
        val usageStats = hasUsageStatsPermission()
        val exactAlarm = hasExactAlarmPermission()

        android.util.Log.d(TAG, "권한 체크: 사용정보=$usageStats, 알람=$exactAlarm")

        return usageStats && exactAlarm
    }

    /**
     * 사용 정보 접근 권한 확인
     */
    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true
        }

        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 정확한 알람 권한 확인
     */
    private fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 권한 상태 UI 업데이트
     */
    private fun updatePermissionStatus() {
        android.util.Log.d(TAG, "UI 업데이트 시작")

        // 1. 사용 정보 접근
        if (hasUsageStatsPermission()) {
            tvUsageStatsStatus.text = "✅ 허용됨"
            tvUsageStatsStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnUsageStats.isEnabled = false
            btnUsageStats.alpha = 0.5f
        } else {
            tvUsageStatsStatus.text = "❌ 필요"
            tvUsageStatsStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnUsageStats.isEnabled = true
            btnUsageStats.alpha = 1.0f
        }

        // 2. 정확한 알람
        if (hasExactAlarmPermission()) {
            tvExactAlarmStatus.text = "✅ 허용됨"
            tvExactAlarmStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnExactAlarm.isEnabled = false
            btnExactAlarm.alpha = 0.5f
        } else {
            tvExactAlarmStatus.text = "❌ 필요"
            tvExactAlarmStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnExactAlarm.isEnabled = true
            btnExactAlarm.alpha = 1.0f
        }

        // ⭐ 모든 필수 권한 확인 후 완료 버튼 활성화
        val allGranted = allPermissionsGranted()
        btnComplete.isEnabled = allGranted
        btnComplete.alpha = if (allGranted) 1.0f else 0.5f

        android.util.Log.d(TAG, "UI 업데이트 완료 - 완료 버튼 활성화: $allGranted")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d(TAG, "onResume - 권한 상태 재확인")
        window.decorView.postDelayed({
            updatePermissionStatus()
        }, 800) // 0.8초 후 재확인
    }

    companion object {
        private const val TAG = "PermissionActivity"
    }
}