package com.example.sleepshift

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LockScreenService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lockIntent = Intent(this, LockScreenActivity::class.java)
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(lockIntent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
