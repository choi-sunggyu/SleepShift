package com.example.sleepshift

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.sleepshift.alarm.AlarmScheduler
import com.example.sleepshift.data.DataStoreManager
import com.example.sleepshift.util.NotificationUtils
import com.example.sleepshift.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "알림 권한이 거부되어 알람 표시가 제한될 수 있습니다",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (네 기존 코드 유지) 시스템 인셋 적용
        findViewById<View?>(R.id.main)?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
                insets
            }
        }

        // 알림 채널/권한
        NotificationUtils.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        val ds = DataStoreManager(this)

        // 우리 버튼들이 레이아웃에 있으면 연결, 없으면 자동 "퀵 설정" 실행
        val btnPickCurrentBed = findViewById<Button?>(R.id.btnPickCurrentBed)
        val btnPickTargetWake = findViewById<Button?>(R.id.btnPickTargetWake)
        val btnPickSleepDur   = findViewById<Button?>(R.id.btnPickSleepDur)
        val tvComputedTarget  = findViewById<TextView?>(R.id.tvComputedTarget)
        val btnStart          = findViewById<Button?>(R.id.btnStart)
        val tvNextAlarm       = findViewById<TextView?>(R.id.tvNextAlarm)

        lifecycleScope.launch {
            val cur = ds.currentBedtime.first()
            val wake = ds.targetWakeTime.first()
            val dur  = ds.targetSleepDurationMinutes.first()

            // 레이아웃이 있으면 초기 표시값 세팅
            btnPickCurrentBed?.text = cur ?: btnPickCurrentBed?.text
            btnPickTargetWake?.text = wake ?: btnPickTargetWake?.text
            btnPickSleepDur?.text   = dur?.let { TimeUtils.minutesToHHmm(it) } ?: btnPickSleepDur?.text
            updateComputedTarget(tvComputedTarget, ds)
            tvNextAlarm?.text = "다음 알람: ${TimeUtils.formatNextAlarm(this@MainActivity)}"

            // 버튼 UI가 없고 필수 값이 비어있다면, 자동 퀵 설정 다이얼로그 진행
            val uiMissing = btnPickCurrentBed == null || btnPickTargetWake == null || btnPickSleepDur == null || btnStart == null
            val needSetup = cur == null || wake == null || dur == null
            if (uiMissing && needSetup) {
                runQuickSetup(ds) {
                    Toast.makeText(this@MainActivity, "알람을 예약했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 버튼 이벤트 연결 (있는 경우에만)
        btnPickCurrentBed?.setOnClickListener {
            pickTime { h, m ->
                val t = TimeUtils.hhmm(h, m)
                btnPickCurrentBed.text = t
                lifecycleScope.launch {
                    ds.setCurrentBedtime(t)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        btnPickTargetWake?.setOnClickListener {
            pickTime { h, m ->
                val t = TimeUtils.hhmm(h, m)
                btnPickTargetWake.text = t
                lifecycleScope.launch {
                    ds.setTargetWakeTime(t)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        btnPickSleepDur?.setOnClickListener {
            // 수면시간도 HH:mm 형태로 받아 총 분으로 변환
            pickTime { h, m ->
                val minutes = h * 60 + m
                btnPickSleepDur.text = TimeUtils.minutesToHHmm(minutes)
                lifecycleScope.launch {
                    ds.setTargetSleepDurationMinutes(minutes)
                    updateComputedTarget(tvComputedTarget, ds)
                }
            }
        }
        btnStart?.setOnClickListener {
            lifecycleScope.launch {
                val curS  = ds.currentBedtime.first()
                val wakeS = ds.targetWakeTime.first()
                val dur   = ds.targetSleepDurationMinutes.first()

                if (curS == null || wakeS == null || dur == null) {
                    Toast.makeText(this@MainActivity, "필요한 시간을 모두 설정해주세요", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val targetBedtime = TimeUtils.computeTargetBedtime(wakeS, dur)
                ds.setTargetBedtime(targetBedtime)
                if (ds.progressBedtime.first() == null) ds.setProgressBedtime(curS)

                AlarmScheduler.scheduleNextBedtimeAlarm(this@MainActivity)
                Toast.makeText(this@MainActivity, "알람을 예약했습니다", Toast.LENGTH_SHORT).show()
                tvNextAlarm?.text = "다음 알람: ${TimeUtils.formatNextAlarm(this@MainActivity)}"
            }
        }
    }

    private suspend fun updateComputedTarget(tv: TextView?, ds: DataStoreManager) {
        if (tv == null) return
        val wakeS = ds.targetWakeTime.first()
        val dur   = ds.targetSleepDurationMinutes.first()
        if (wakeS != null && dur != null) {
            val target = TimeUtils.computeTargetBedtime(wakeS, dur)
            tv.text = "목표 취침 시간: $target"
        }
    }

    private fun pickTime(onPicked: (Int, Int) -> Unit) {
        val now = LocalTime.now()
        TimePickerDialog(this, { _, h, m -> onPicked(h, m) }, now.hour, now.minute, true).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 버튼 UI가 없어도 동작하도록 하는 3단계 "퀵 설정":
     * 1) 현재 취침시간 → 2) 목표 기상시간 → 3) 목표 수면시간 → 알람 예약
     */
    private fun runQuickSetup(ds: DataStoreManager, onDone: () -> Unit) {
        // 1) 현재 취침
        pickTime { curH, curM ->
            val cur = TimeUtils.hhmm(curH, curM)
            lifecycleScope.launch { ds.setCurrentBedtime(cur) }

            // 2) 기상시간
            pickTime { wakeH, wakeM ->
                val wake = TimeUtils.hhmm(wakeH, wakeM)
                lifecycleScope.launch { ds.setTargetWakeTime(wake) }

                // 3) 수면시간(HH:mm)
                pickTime { durH, durM ->
                    val minutes = durH * 60 + durM
                    lifecycleScope.launch {
                        ds.setTargetSleepDurationMinutes(minutes)
                        val targetBedtime = TimeUtils.computeTargetBedtime(wake, minutes)
                        ds.setTargetBedtime(targetBedtime)
                        if (ds.progressBedtime.first() == null) ds.setProgressBedtime(cur)

                        AlarmScheduler.scheduleNextBedtimeAlarm(this@MainActivity)
                        onDone()
                    }
                }
            }
        }
    }
}
