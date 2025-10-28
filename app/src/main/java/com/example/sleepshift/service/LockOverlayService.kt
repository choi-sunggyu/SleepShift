package com.example.sleepshift.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class LockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_OVERLAY -> startOverlay()
            ACTION_STOP_OVERLAY -> stopOverlay()
        }

        return START_STICKY
    }

    private fun startOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 투명한 전체 화면 오버레이
        overlayView = View(this).apply {
            setBackgroundColor(0x01000000)  // 거의 투명

            setOnTouchListener { _, event ->
                // 모든 터치 차단 (LockScreen 외에는)
                Log.d(TAG, "터치 차단됨")
                true  // 이벤트 소비
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)
        Log.d(TAG, "✅ 오버레이 활성화")
    }

    private fun stopOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
            Log.d(TAG, "✅ 오버레이 제거")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }

    companion object {
        private const val TAG = "LockOverlayService"
        const val ACTION_START_OVERLAY = "START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, LockOverlayService::class.java).apply {
                action = ACTION_START_OVERLAY
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }
    }
}