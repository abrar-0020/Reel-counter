package com.example.manager

import com.example.data.SessionEntity
import com.example.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SessionManager {
    private var repository: SessionRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val _currentReelCount = MutableStateFlow(0)
    val currentReelCount: StateFlow<Int> = _currentReelCount.asStateFlow()
    
    private val _sessionStartTime = MutableStateFlow(0L)
    
    // Statistics
    var retries = 0
    var totalConfirmationTime = 0L
    var totalParseTime = 0L
    var totalSignatureTime = 0L
    
    fun init(repo: SessionRepository) {
        repository = repo
    }
    
    fun startSession() {
        if (_state.value == SessionState.RUNNING) return
        _state.value = SessionState.RUNNING
        _currentReelCount.value = 0
        _sessionStartTime.value = System.currentTimeMillis()
        
        retries = 0
        totalConfirmationTime = 0L
        totalParseTime = 0L
        totalSignatureTime = 0L
    }
    
    fun pauseSession() {
        if (_state.value == SessionState.RUNNING) {
            _state.value = SessionState.PAUSED
        }
    }
    
    fun resumeSession() {
        if (_state.value == SessionState.PAUSED) {
            _state.value = SessionState.RUNNING
            // In a real app we'd handle paused duration offset, skipping for now
        }
    }
    
    fun stopSession() {
        if (_state.value == SessionState.IDLE || _state.value == SessionState.STOPPED) return
        
        val endTime = System.currentTimeMillis()
        val startTime = _sessionStartTime.value
        val durationSeconds = (endTime - startTime) / 1000
        
        val session = SessionEntity(
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            reelsViewed = _currentReelCount.value,
            retries = retries,
            totalParseTime = totalParseTime,
            totalSignatureTime = totalSignatureTime,
            totalConfirmationTime = totalConfirmationTime
        )
        
        scope.launch(Dispatchers.IO) {
            repository?.insert(session)
        }
        
        _state.value = SessionState.STOPPED
        _currentReelCount.value = 0
    }
    
    fun onReelConfirmed(retryCount: Int, parseTimeMs: Long, signatureTimeMs: Long, totalTimeMs: Long) {
        if (_state.value != SessionState.RUNNING) return
        
        _currentReelCount.value += 1
        retries += retryCount
        totalParseTime += parseTimeMs
        totalSignatureTime += signatureTimeMs
        totalConfirmationTime += totalTimeMs
    }
}
