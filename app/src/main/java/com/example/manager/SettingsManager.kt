package com.example.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val RETRY_DELAY = longPreferencesKey("retry_delay")
        val CONFIRMATION_DELAY = longPreferencesKey("confirmation_delay")
    }

    val overlayEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[OVERLAY_ENABLED] ?: false }
    val retryDelayFlow: Flow<Long> = context.dataStore.data.map { it[RETRY_DELAY] ?: 500L }
    val confirmationDelayFlow: Flow<Long> = context.dataStore.data.map { it[CONFIRMATION_DELAY] ?: 1500L }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OVERLAY_ENABLED] = enabled }
    }

    suspend fun setRetryDelay(delay: Long) {
        context.dataStore.edit { it[RETRY_DELAY] = delay }
    }

    suspend fun setConfirmationDelay(delay: Long) {
        context.dataStore.edit { it[CONFIRMATION_DELAY] = delay }
    }
}
