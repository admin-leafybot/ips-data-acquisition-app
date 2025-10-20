package com.ips.dataacquisition.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val IS_ONLINE_KEY = booleanPreferencesKey("is_online")
        private val LAST_ACTIVITY_KEY = longPreferencesKey("last_activity_timestamp")
        private val IS_COLLECTING_KEY = booleanPreferencesKey("is_collecting_data")
        private val SAMPLES_COLLECTED_KEY = longPreferencesKey("samples_collected")
        private const val AUTO_OFFLINE_TIMEOUT_MS = 2 * 60 * 60 * 1000L // 2 hours
    }
    
    val isOnline: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_ONLINE_KEY] ?: false
        }
    
    val lastActivityTimestamp: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_ACTIVITY_KEY] ?: 0L
        }
    
    val isCollectingData: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_COLLECTING_KEY] ?: false
        }
    
    val samplesCollected: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[SAMPLES_COLLECTED_KEY] ?: 0L
        }
    
    suspend fun getPendingSyncCount(): Int {
        // This will be updated by the ViewModel by querying repositories
        return 0
    }
    
    suspend fun setOnline(online: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ONLINE_KEY] = online
            android.util.Log.d("PreferencesManager", "User state changed to: ${if (online) "ONLINE" else "OFFLINE"}")
        }
    }
    
    suspend fun updateLastActivity() {
        val timestamp = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences[LAST_ACTIVITY_KEY] = timestamp
            android.util.Log.d("PreferencesManager", "Last activity updated: $timestamp")
        }
    }
    
    suspend fun checkAndHandleTimeout(): Boolean {
        val lastActivity = context.dataStore.data.map { it[LAST_ACTIVITY_KEY] ?: 0L }.first()
        val currentTime = System.currentTimeMillis()
        val timeSinceActivity = currentTime - lastActivity
        
        if (lastActivity > 0 && timeSinceActivity > AUTO_OFFLINE_TIMEOUT_MS) {
            android.util.Log.w("PreferencesManager", "Auto-offline: No activity for ${timeSinceActivity / 1000 / 60} minutes")
            setOnline(false)
            return true // Was timed out
        }
        
        return false // Still active
    }
    
    suspend fun setCollectingData(collecting: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_COLLECTING_KEY] = collecting
        }
    }
    
    suspend fun incrementSamplesCollected(count: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[SAMPLES_COLLECTED_KEY] ?: 0L
            preferences[SAMPLES_COLLECTED_KEY] = current + count
        }
    }
    
    suspend fun resetSamplesCollected() {
        context.dataStore.edit { preferences ->
            preferences[SAMPLES_COLLECTED_KEY] = 0L
        }
    }
}
