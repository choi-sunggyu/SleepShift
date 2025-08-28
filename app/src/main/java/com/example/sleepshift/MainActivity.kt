package com.example.sleepshift

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.alarm.AlarmScheduler
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.NotificationUtils
import com.example.sleepshift.util.TimeUtils
import com.example.sleepshift.LockScreenActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "알림 권한이 거부되어 알람 표시가 제한될 수 있습니다", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationUtils.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        val btnPickCurrentBed = findViewById<Button>(R.id.btnPickCurrentBed)
        val btnPickTargetWake = findViewById<Button>(R.id.btnPickTargetWake)
        val btnPickSleepDur = findViewById<Button>(R.id.btnPickSleepDur)
        val tvComputedTarget = findViewById<TextView>(R.id.tvComputedTarget)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val tvNextAlarm = findViewById<TextView>(R.id.tvNextAlarm)

        val ds = DataStoreManager(this)

        lifecycleScope.launch {
            val cur = ds.currentBedtime.first()
            val wake = ds.targetWakeTime.first()
            val durMin = ds.targetSleepDurationMinutes.first()

            cur?.let { btnPickCurrentBed.text = it }
            wake?.let { btnPickTargetWake.text = it }
            durMin?.let { btnPickSleepDur.text = TimeUtils.minutesToHHmm(it) }

            updateComputedTarget(tvComputedTarget, ds)
            tvNextAlarm.text = "다음 알람: ${TimeUtils.formatNextAlarm(this@MainActivity)}"
        }

        btnPickCurrentBed.setOnClickListener {
            pickTime { hh, mm ->
                val t = TimeUtils.hhmm(hh, mm)
                btnPickCurrentBed.text = t
                lifecycleScope.launch {
                    ds.setCurrentBedtime(t)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        btnPickTargetWake.setOnClickListener {
            pickTime { hh, mm ->
                val t = TimeUtils.hhmm(hh, mm)
                btnPickTargetWake.text = t
                lifecycleScope.launch {
                    ds.setTargetWakeTime(t)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        btnPickSleepDur.setOnClickListener {
            pickTime { hh, mm ->
                val minutes = hh * 60 + mm
                btnPickSleepDur.text = TimeUtils.minutesToHHmm(minutes)
                lifecycleScope.launch {
                    ds.setTargetSleepDurationMinutes(minutes)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        val btnSleepNow = findViewById<Button>(R.id.btnSleepNow)

        btnSleepNow.setOnClickListener {
            lifecycleScope.launch {
                val ds = DataStoreManager(this@MainActivity)
                // 수면 시작 플래그 세팅 (Sleepy 성공 로직과 동일한 시작점)
                ds.setSleepStarted(true)
                ds.setSleepStartedAt(System.currentTimeMillis())
            }
            // 바로 락스크린으로 진입
            startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
        }

        btnStart.setOnClickListener {
            lifecycleScope.launch {
                val curS = ds.currentBedtime.first()
                val wakeS = ds.targetWakeTime.first()
                val durMin = ds.targetSleepDurationMinutes.first()

                if (curS == null || wakeS == null || durMin == null) {
                    Toast.makeText(this@MainActivity, "필요한 시간을 모두 설정해주세요", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val targetBedtime = TimeUtils.computeTargetBedtime(wakeS, durMin)
                ds.setTargetBedtime(targetBedtime)
                if (ds.progressBedtime.first() == null) ds.setProgressBedtime(curS)

                AlarmScheduler.scheduleNextBedtimeAlarm(this@MainActivity)
                Toast.makeText(this@MainActivity, "알람을 예약했습니다", Toast.LENGTH_SHORT).show()
                tvNextAlarm.text = "다음 알람: ${TimeUtils.formatNextAlarm(this@MainActivity)}"
            }
        }
    }

    private suspend fun updateComputedTarget(tv: TextView, ds: DataStoreManager) {
        val wakeS = ds.targetWakeTime.first()
        val durMin = ds.targetSleepDurationMinutes.first()
        if (wakeS != null && durMin != null) {
            val target = TimeUtils.computeTargetBedtime(wakeS, durMin)
            tv.text = target
        }
    }

    private fun pickTime(onPicked: (Int, Int) -> Unit) {
        val now = LocalTime.now()
        TimePickerDialog(this, { _, h, m -> onPicked(h, m) }, now.hour, now.minute, true).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
