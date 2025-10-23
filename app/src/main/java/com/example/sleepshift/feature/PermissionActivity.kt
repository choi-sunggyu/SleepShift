package com.example.sleepshift.feature

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.feature.onboarding.OnboardingActivity
import com.example.sleepshift.service.AccessibilityLockService

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

        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d(TAG, "✅ PermissionActivity 시작")

        checkAllFiles()
        initViews()
        setupButtons()
        updatePermissionStatus()

        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * ⭐ 모든 파일과 설정 확인
     */
    private fun checkAllFiles() {
        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d(TAG, "🔍 파일 및 설정 체크 시작")
        android.util.Log.d(TAG, "패키지: $packageName")

        // 1. XML 리소스 확인
        try {
            val xmlId = R.xml.accessibility_service_config
            val parser = resources.getXml(xmlId)
            android.util.Log.d(TAG, "✅ accessibility_service_config.xml 존재 (ID: $xmlId)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ accessibility_service_config.xml 없음!")
            android.util.Log.e(TAG, "에러: ${e.message}")
            showError("XML 파일이 없습니다!\n\nres/xml/accessibility_service_config.xml\n파일을 생성하세요.")
        }

        // 2. strings.xml 확인
        try {
            val desc = getString(R.string.accessibility_service_description)
            android.util.Log.d(TAG, "✅ accessibility_service_description 존재")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ accessibility_service_description 없음!")
            android.util.Log.e(TAG, "에러: ${e.message}")
            showError("strings.xml에\naccessibility_service_description이\n없습니다!")
        }

        // 3. Service 클래스 확인
        try {
            val clazz = Class.forName("com.example.sleepshift.service.AccessibilityLockService")
            android.util.Log.d(TAG, "✅ AccessibilityLockService 클래스 존재")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ AccessibilityLockService 클래스 없음!")
            android.util.Log.e(TAG, "에러: ${e.message}")
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
                    android.util.Log.d(TAG, "✅ Manifest에 AccessibilityLockService 등록됨")
                    android.util.Log.d(TAG, "   이름: ${accessibilityService.name}")
                    android.util.Log.d(TAG, "   exported: ${accessibilityService.exported}")
                    android.util.Log.d(TAG, "   enabled: ${accessibilityService.enabled}")

                    if (!accessibilityService.exported) {
                        android.util.Log.e(TAG, "❌ exported=false!")
                        showError("AndroidManifest.xml에서\nAccessibilityLockService의\nexported를 true로 변경하세요!")
                    }
                } else {
                    android.util.Log.e(TAG, "❌ Manifest에 AccessibilityLockService 없음!")
                    showError("AndroidManifest.xml에\nAccessibilityLockService가\n등록되지 않았습니다!")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Manifest 확인 실패: ${e.message}")
        }

        android.util.Log.d(TAG, "🔍 파일 및 설정 체크 완료")
        android.util.Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun showError(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
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

    private fun setupButtons() {
        // 사용 정보 접근 권한
        btnUsageStats.setOnClickListener {
            requestUsageStatsPermission()
        }

        // ⭐ 접근성 서비스 - 필수 권한
        btnAccessibility.setOnClickListener {
            requestAccessibilityPermission()
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
                if (!isAccessibilityServiceEnabled()) missingPermissions.add("접근성 서비스")
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
     * 접근성 서비스 권한 요청
     */
    private fun requestAccessibilityPermission() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "다운로드한 서비스에서\n'Dozeo 수면 잠금' 또는 'SleepShift'를\n찾아서 활성화해주세요",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.d(TAG, "접근성 설정 화면 열기")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "접근성 설정 화면 열기 실패", e)
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
     * ⭐ 모든 필수 권한 확인 (Accessibility 포함)
     */
    private fun allPermissionsGranted(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                android.util.Log.d(TAG, "✅ 접근성 서비스 활성화: ${service.resolveInfo.serviceInfo.name}")
                return true
            }
        }

        android.util.Log.d(TAG, "❌ 접근성 서비스 비활성화")
        return false
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
     * 접근성 서비스 활성화 확인
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val myServiceId = "$packageName/${AccessibilityLockService::class.java.canonicalName}"
        android.util.Log.d(TAG, "내 서비스 ID: $myServiceId")
        val possibleServiceNames = listOf(
            "$packageName/.service.AccessibilityLockService",
            "$packageName/com.example.sleepshift.service.AccessibilityLockService",
            "com.example.sleepshift/.service.AccessibilityLockService",
            "com.example.sleepshift/com.example.sleepshift.service.AccessibilityLockService"
        )

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        android.util.Log.d(TAG, "활성화된 접근성 서비스: $enabledServices")

        for (serviceName in possibleServiceNames) {
            if (enabledServices.contains(serviceName)) {
                android.util.Log.d(TAG, "✅ 접근성 서비스 활성화 확인: $serviceName")
                return true
            }
        }

        android.util.Log.d(TAG, "❌ 접근성 서비스 비활성화")
        return false
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

        // 2. ⭐ 접근성 서비스 (필수)
        if (isAccessibilityServiceEnabled()) {
            tvAccessibilityStatus.text = "✅ 허용됨"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnAccessibility.isEnabled = false
            btnAccessibility.alpha = 0.5f
        } else {
            tvAccessibilityStatus.text = "❌ 필요"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnAccessibility.isEnabled = true
            btnAccessibility.alpha = 1.0f
        }

        // 3. 정확한 알람
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