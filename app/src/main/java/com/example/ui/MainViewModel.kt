package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ReelApplication
import com.example.manager.SessionManager
import com.example.manager.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ReelApplication
    private val repository = app.repository
    val settingsManager = app.settingsManager

    val sessionState: StateFlow<SessionState> = SessionManager.state
    val currentReelCount: StateFlow<Int> = SessionManager.currentReelCount
    
    val allSessions = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun startSession() {
        SessionManager.startSession()
    }

    fun stopSession() {
        SessionManager.stopSession()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}
