package com.example.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.R
import com.example.manager.SessionManager
import com.example.manager.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayManager(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private var reelsText: TextView? = null
    private var timeText: TextView? = null
    private var statusText: TextView? = null

    @SuppressLint("InflateParams")
    fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200

        overlayView = LayoutInflater.from(service).inflate(R.layout.overlay_layout, null)
        reelsText = overlayView?.findViewById(R.id.overlay_reels)
        timeText = overlayView?.findViewById(R.id.overlay_time)
        statusText = overlayView?.findViewById(R.id.overlay_status)
        
        windowManager.addView(overlayView, params)

        startObserving()
        startTimer()
    }

    fun hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
            reelsText = null
            timeText = null
            statusText = null
        }
    }

    private fun startObserving() {
        scope.launch {
            SessionManager.state.combine(SessionManager.currentReelCount) { state, count ->
                Pair(state, count)
            }.collect { (state, count) ->
                reelsText?.text = "Views: $count"
                statusText?.text = "Status: ${state.name}"
                if (state == SessionState.IDLE || state == SessionState.STOPPED) {
                    timeText?.text = "Time: 00:00"
                }
            }
        }
    }
    
    private fun startTimer() {
        scope.launch {
            while (isActive) {
                if (SessionManager.state.value == SessionState.RUNNING) {
                    val field = SessionManager::class.java.getDeclaredField("_sessionStartTime")
                    field.isAccessible = true
                    val flow = field.get(SessionManager) as kotlinx.coroutines.flow.StateFlow<*>
                    val startTime = flow.value as Long
                    
                    if (startTime > 0) {
                        val duration = (System.currentTimeMillis() - startTime) / 1000
                        val m = duration / 60
                        val s = duration % 60
                        val format = String.format("Time: %02d:%02d", m, s)
                        timeText?.text = format
                    }
                }
                delay(1000)
            }
        }
    }
}
