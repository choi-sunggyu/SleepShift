package com.example.sleepshift.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sleepshift.LockScreenActivity
import com.example.sleepshift.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ds = DataStoreManager(context)
        when (intent.action) {
            "com.example.sleepshift.ACTION_SLEEP_NOW" -> {
                runBlocking {
                    ds.setSleepStarted(true)
                    ds.setSleepStartedAt(System.currentTimeMillis())
                }
                val i = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            }
            "com.example.sleepshift.ACTION_NOT_YET" -> {
                // 5분 유예 동안 GraceReceiver가 실패를 판단
            }

            // 호환: 완료/건너뛰기
            "com.example.sleepshift.ACTION_DONE" -> {
                onReceive(context, Intent("com.example.sleepshift.ACTION_SLEEP_NOW"))
            }
            "com.example.sleepshift.ACTION_SKIP" -> {
                runBlocking {
                    ds.setConsecutiveSuccessDays(0)
                    ds.setStepMinutes(30)
                }
                AlarmScheduler.scheduleTomorrowSameTime(context)
            }
        }
    }
}
