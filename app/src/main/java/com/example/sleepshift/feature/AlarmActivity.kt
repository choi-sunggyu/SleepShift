package com.example.sleepshift.feature

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sleepshift.R
import com.example.sleepshift.data.SleepRepository
import com.example.sleepshift.util.KstTime
import kotlinx.coroutines.runBlocking

class AlarmActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 잠금화면 위로 표시 & 화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        val tvTime = findViewById<TextView>(R.id.tvAlarmTime)
        val tvLabel = findViewById<TextView>(R.id.tvLabel)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)

        // 화면에 표시할 목표 기상시간(설정값) 로드
        val goalWake = runBlocking {
            SleepRepository(this@AlarmActivity).getSettings()?.goalWakeTime
        } ?: "--:--"
        tvTime.text = goalWake
        tvLabel.text = "기상 알람"

        startAlarmSound()

        btnSnooze.setOnClickListener {
            stopAlarmSound()
            // 10분 뒤 다시 울림
            scheduleSnooze(this, minutes = 10)
            Toast.makeText(this, "10분 뒤 다시 알림", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnDismiss.setOnClickListener {
            stopAlarmSound()
            // 다음 기상시간에 맞춰 다시 예약(내일)
            scheduleNextWakeAlarm(this)
            Toast.makeText(this, "좋은 아침! 내일 알람을 예약했어요.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }

    override fun onBackPressed() {
        // 뒤로가기로 종료 못 하게 막고 싶은 경우 주석 해제
        // return
        super.onBackPressed()
    }

    // ===== 알람 사운드 =====
    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                isLoopingCompat(true)
                play()
            }
        } catch (e: Exception) {
            // 장치에 알람 소리 리소스가 없거나 재생 실패 시 무시
        }
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {}
        ringtone = null
    }

    // Ringtone.looping은 SDK별 동작 편차가 있어 보조 메서드로 래핑
    private fun Ringtone.isLoopingCompat(loop: Boolean) {
        try {
            val m = Ringtone::class.java.getMethod("setLooping", Boolean::class.javaPrimitiveType)
            m.invoke(this, loop)
        } catch (_: Exception) {
            // 일부 기기/버전에서 미지원: 시스템 기본 반복에 의존
        }
    }

    // ===== 스케줄링 유틸 =====

    /** 다음 '목표 기상시간(goalWakeTime)'에 알람 액티비티를 전체화면으로 실행 */
    companion object {
        private const val REQ_CODE_WAKE = 7001

        fun scheduleNextWakeAlarm(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val goal = runBlocking { SleepRepository(ctx).getSettings()?.goalWakeTime }
            if (goal.isNullOrBlank()) {
                Toast.makeText(ctx, "목표 기상시간 설정이 필요합니다", Toast.LENGTH_SHORT).show()
                return
            }
            val triggerAt = KstTime.nextOccurrenceEpoch(goal)

            val pi = PendingIntent.getActivity(
                ctx,
                REQ_CODE_WAKE,
                Intent(ctx, AlarmActivity::class.java)
                    .setAction("WAKE_ALARM")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // Android 12+에서는 정확 알람 권한이 필요할 수 있음
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    // 권한이 없어도 시도는 하지만, 보안 예외에 대비
                }
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (se: SecurityException) {
                Toast.makeText(ctx, "정확한 알람 권한이 필요합니다(설정에서 허용)", Toast.LENGTH_LONG).show()
                // 설정 화면 유도 (가능한 경우)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val i = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }
                }
            }
        }

        /** 지금 시각 기준 N분 뒤로 스누즈 예약 */
        fun scheduleSnooze(ctx: Context, minutes: Int = 10) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + minutes * 60_000L
            val pi = PendingIntent.getActivity(
                ctx,
                REQ_CODE_WAKE,
                Intent(ctx, AlarmActivity::class.java)
                    .setAction("WAKE_ALARM")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (se: SecurityException) {
                Toast.makeText(ctx, "정확한 알람 권한이 필요합니다(설정에서 허용)", Toast.LENGTH_LONG).show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val i = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }
                }
            }
        }
    }
}
