package com.example.sleepshift.feature

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvExactAlarmStatus: TextView
    private lateinit var btnUsageStats: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnExactAlarm: Button
    private lateinit var btnComplete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        // ⭐⭐⭐ 강제 파일 체크
        checkAllFiles()

        initViews()
        setupButtons()
        updatePermissionStatus()
    }

    /**
     * ⭐ 모든 파일과 설정 확인
     */
    private fun checkAllFiles() {
        android.util.Log.e("===================", "===================")
        android.util.Log.e("FILE_CHECK", "🔍 시작")
        android.util.Log.e("FILE_CHECK", "패키지: $packageName")

        // 1. XML 리소스 확인
        try {
            val xmlId = R.xml.accessibility_service_config
            val parser = resources.getXml(xmlId)
            android.util.Log.e("FILE_CHECK", "✅ XML 파일 존재 (ID: $xmlId)")
        } catch (e: Exception) {
            android.util.Log.e("FILE_CHECK", "❌❌❌ XML 파일 없음!")
            android.util.Log.e("FILE_CHECK", "에러: ${e.message}")
            showError("XML 파일이 없습니다!\n\nres/xml/accessibility_service_config.xml\n파일을 생성하세요.")
        }

        // 2. strings.xml 확인
        try {
            val desc = getString(R.string.accessibility_service_description)
            android.util.Log.e("FILE_CHECK", "✅ 설명 존재: ${desc.take(30)}...")
        } catch (e: Exception) {
            android.util.Log.e("FILE_CHECK", "❌❌❌ 설명 없음!")
            android.util.Log.e("FILE_CHECK", "에러: ${e.message}")
            showError("strings.xml에\naccessibility_service_description이\n없습니다!")
        }

        // 3. Service 클래스 확인
        try {
            val clazz = Class.forName("com.example.sleepshift.service.AccessibilityLockService")
            android.util.Log.e("FILE_CHECK", "✅ Service 클래스 존재")
        } catch (e: Exception) {
            android.util.Log.e("FILE_CHECK", "❌❌❌ Service 클래스 없음!")
            android.util.Log.e("FILE_CHECK", "에러: ${e.message}")
            showError("AccessibilityLockService.kt\n파일이 없거나 패키지가 잘못되었습니다!")
        }

        // 4. AndroidManifest에서 Service 등록 확인
        try {
            val pm = packageManager
            val services = pm.getPackageInfo(
                packageName,
                PackageManager.GET_SERVICES
            ).services

            if (services != null) {
                val accessibilityService = services.find {
                    it.name.contains("AccessibilityLockService")
                }

                if (accessibilityService != null) {
                    android.util.Log.e("FILE_CHECK", "✅ Manifest에 Service 등록됨")
                    android.util.Log.e("FILE_CHECK", "   이름: ${accessibilityService.name}")
                    android.util.Log.e("FILE_CHECK", "   exported: ${accessibilityService.exported}")
                    android.util.Log.e("FILE_CHECK", "   enabled: ${accessibilityService.enabled}")

                    if (!accessibilityService.exported) {
                        android.util.Log.e("FILE_CHECK", "❌❌❌ exported=false!")
                        showError("AndroidManifest.xml에서\nexported를 true로 변경하세요!")
                    }
                } else {
                    android.util.Log.e("FILE_CHECK", "❌❌❌ Manifest에 Service 없음!")
                    showError("AndroidManifest.xml에\nAccessibilityLockService가\n등록되지 않았습니다!")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FILE_CHECK", "Manifest 확인 실패: ${e.message}")
        }

        android.util.Log.e("FILE_CHECK", "🔍 완료")
        android.util.Log.e("===================", "===================")
    }

    private fun showError(message: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ 설정 오류")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun initViews() {
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvExactAlarmStatus = findViewById(R.id.tvExactAlarmStatus)
        btnUsageStats = findViewById(R.id.btnUsageStats)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnExactAlarm = findViewById(R.id.btnExactAlarm)
        btnComplete = findViewById(R.id.btnComplete)
    }

    // ⭐ 접근성 섹션을 "선택사항"으로 표시
    private fun setupButtons() {
        btnUsageStats.setOnClickListener {
            requestUsageStatsPermission()
        }

        // ⭐ 접근성을 선택사항으로
        btnAccessibility.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("선택 사항")
                .setMessage("접근성 서비스는 추가 보안을 위한 선택사항입니다.\n\n없어도 앱이 정상 작동합니다.")
                .setPositiveButton("설정하기") { _, _ ->
                    requestAccessibilityPermission()
                }
                .setNegativeButton("건너뛰기", null)
                .show()
        }

        btnExactAlarm.setOnClickListener {
            requestExactAlarmPermission()
        }

        btnComplete.setOnClickListener {
            if (allPermissionsGranted()) {
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "필수 권한을 허용해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Dozeo를 찾아서 활성화해주세요", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "다운로드한 서비스에서 'Dozeo 수면 잠금'을 찾아 활성화하세요",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        // ⭐ 접근성 제거
        return hasUsageStatsPermission() && hasExactAlarmPermission()
    }

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

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/.service.AccessibilityLockService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }

    private fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun updatePermissionStatus() {
        // UsageStats
        if (hasUsageStatsPermission()) {
            tvUsageStatsStatus.text = "✅ 허용됨"
            tvUsageStatsStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnUsageStats.isEnabled = false
            btnUsageStats.alpha = 0.5f
        } else {
            tvUsageStatsStatus.text = "❌ 필수"
            tvUsageStatsStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnUsageStats.isEnabled = true
            btnUsageStats.alpha = 1.0f
        }

        // ⭐ Accessibility를 선택사항으로
        if (isAccessibilityServiceEnabled()) {
            tvAccessibilityStatus.text = "✅ 활성화됨 (추가 보안)"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnAccessibility.isEnabled = false
            btnAccessibility.alpha = 0.5f
        } else {
            tvAccessibilityStatus.text = "⭕ 선택사항"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.darker_gray))
            btnAccessibility.isEnabled = true
            btnAccessibility.alpha = 1.0f
        }

        // Exact Alarm
        if (hasExactAlarmPermission()) {
            tvExactAlarmStatus.text = "✅ 허용됨"
            tvExactAlarmStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnExactAlarm.isEnabled = false
            btnExactAlarm.alpha = 0.5f
        } else {
            tvExactAlarmStatus.text = "❌ 필수"
            tvExactAlarmStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnExactAlarm.isEnabled = true
            btnExactAlarm.alpha = 1.0f
        }

        // ⭐ 필수 권한만으로 완료 가능
        btnComplete.isEnabled = allPermissionsGranted()
        btnComplete.alpha = if (allPermissionsGranted()) 1.0f else 0.5f
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}