package com.example.service

import android.accessibilityservice.AccessibilityService
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
    private var overlay: FloatingCounterOverlay? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    fun showOverlay() {
        if (overlay != null) return

        overlay = FloatingCounterOverlay(service)
        overlay?.show()

        startObserving()
        startTimer()
    }

    fun hideOverlay() {
        overlay?.hide()
        overlay = null
    }

    private fun startObserving() {
        scope.launch {
            SessionManager.state.combine(SessionManager.currentReelCount) { state, count ->
                Pair(state, count)
            }.collect { (state, count) ->
                overlay?.updateCount(count)
                overlay?.updateState(state)
                if (state == SessionState.IDLE || state == SessionState.STOPPED) {
                    overlay?.updateTime("00:00")
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
                        val format = String.format("%02d:%02d", m, s)
                        overlay?.updateTime(format)
                    }
                }
                delay(1000)
            }
        }
    }
}
