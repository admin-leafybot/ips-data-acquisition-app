package ai.indoorbrain.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.indoorbrain.data.model.User
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
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
        
        // Auth keys
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_PHONE_KEY = stringPreferencesKey("user_phone")
        private val USER_FULL_NAME_KEY = stringPreferencesKey("user_full_name")
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRES_AT_KEY = longPreferencesKey("token_expires_at")
        
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
    
    suspend fun getLastSessionTime(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_ACTIVITY_KEY] ?: System.currentTimeMillis()
        }.first()
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
    
    // Language preference
    fun getLanguage(): Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[LANGUAGE_KEY] ?: "en"  // Default to English
        }
    
    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
    }
    
    // Auth / User management
    fun getUser(): Flow<User?> = context.dataStore.data
        .map { preferences ->
            val userId = preferences[USER_ID_KEY]
            val phone = preferences[USER_PHONE_KEY]
            val fullName = preferences[USER_FULL_NAME_KEY]
            val token = preferences[AUTH_TOKEN_KEY]
            val refreshToken = preferences[REFRESH_TOKEN_KEY]
            val expiresAt = preferences[TOKEN_EXPIRES_AT_KEY]
            
            if (userId != null && phone != null && fullName != null && 
                token != null && refreshToken != null && expiresAt != null) {
                User(userId, phone, fullName, token, refreshToken, expiresAt)
            } else {
                null
            }
        }
    
    // Get userId synchronously for logging purposes
    suspend fun getUserIdForLogging(): String {
        return context.dataStore.data.map { preferences ->
            preferences[USER_ID_KEY] ?: "unknown"
        }.first()
    }
    
    suspend fun saveUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = user.userId
            preferences[USER_PHONE_KEY] = user.phone
            preferences[USER_FULL_NAME_KEY] = user.fullName
            preferences[AUTH_TOKEN_KEY] = user.token
            preferences[REFRESH_TOKEN_KEY] = user.refreshToken
            preferences[TOKEN_EXPIRES_AT_KEY] = user.tokenExpiresAt
        }
    }
    
    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_PHONE_KEY)
            preferences.remove(USER_FULL_NAME_KEY)
            preferences.remove(AUTH_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(TOKEN_EXPIRES_AT_KEY)
        }
    }
    
    suspend fun getAuthToken(): String? {
        return context.dataStore.data.map { it[AUTH_TOKEN_KEY] }.first()
    }
}
