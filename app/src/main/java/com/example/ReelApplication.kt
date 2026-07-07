package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.SessionRepository
import com.example.manager.SessionManager
import com.example.manager.SettingsManager

class ReelApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { SessionRepository(database.sessionDao()) }
    val settingsManager by lazy { SettingsManager(this) }
    
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(repository)
    }
}
