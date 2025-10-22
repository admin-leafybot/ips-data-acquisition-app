package com.ips.dataacquisition.data.repository

import com.ips.dataacquisition.data.local.dao.ButtonPressDao
import com.ips.dataacquisition.data.local.dao.SessionDao
import com.ips.dataacquisition.data.model.ButtonAction
import com.ips.dataacquisition.data.model.ButtonPress
import com.ips.dataacquisition.data.model.Session
import com.ips.dataacquisition.data.model.SessionStatus
import com.ips.dataacquisition.data.remote.ApiService
import com.ips.dataacquisition.data.remote.dto.ButtonPressRequest
import com.ips.dataacquisition.data.remote.dto.SessionCreateRequest
import com.ips.dataacquisition.data.remote.dto.SessionUpdateRequest
import com.ips.dataacquisition.util.CloudLogger
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SessionRepository(
    private val sessionDao: SessionDao,
    private val buttonPressDao: ButtonPressDao,
    private val apiService: ApiService,
    private val context: android.content.Context
) {
    private val preferencesManager = com.ips.dataacquisition.data.local.PreferencesManager(context)
    private var userIdPrefix = ""
    
    // Mutex to prevent concurrent session creation
    private val sessionCreationMutex = Mutex()
    
    // Mutex to prevent concurrent session close/cancel operations
    private val sessionModificationMutex = Mutex()
    
    // Mutex to prevent concurrent sync operations
    private val syncMutex = Mutex()
    
    init {
        // Initialize userId prefix for logging
        CoroutineScope(Dispatchers.IO).launch {
            userIdPrefix = "[${preferencesManager.getUserIdForLogging()}] "
        }
    }
    
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()
    
    suspend fun getActiveSession(): Session? = sessionDao.getActiveSession()
    
    suspend fun getUnsyncedButtonPressCount(): Int {
        return buttonPressDao.getUnsyncedCount()
    }
    
    suspend fun createSession(): Result<String> {
        return sessionCreationMutex.withLock {
            try {
                val sessionId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                val session = Session(
                    sessionId = sessionId,
                    startTimestamp = timestamp,
                    status = SessionStatus.IN_PROGRESS
                )
                
                // Save locally first
                sessionDao.insertSession(session)
                
                // Update last activity timestamp
                preferencesManager.updateLastActivity()
                
                // Try to create on backend
                try {
                    val response = apiService.createSession(
                        SessionCreateRequest(sessionId, timestamp)
                    )
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        sessionDao.markSessionSynced(sessionId)
                    }
                } catch (e: Exception) {
                    // Network error - session will be synced later
                    e.printStackTrace()
                }
                
                Result.success(sessionId)
            } catch (e: Exception) {
                // Critical database failure - send to Sentry
                CloudLogger.captureEvent("$userIdPrefix DB_INSERT_FAILED: Session creation failed - ${e.message}", SentryLevel.ERROR)
                Result.failure(e)
            }
        }
    }
    
    suspend fun closeSession(sessionId: String): Result<Unit> {
        return sessionModificationMutex.withLock {
            try {
                val timestamp = System.currentTimeMillis()
                val session = sessionDao.getSessionById(sessionId)
            
            if (session != null) {
                // First update: Mark as completed, not synced yet
                val updatedSession = session.copy(
                    endTimestamp = timestamp,
                    status = SessionStatus.COMPLETED,
                    isSynced = false
                )
                sessionDao.updateSession(updatedSession)
                
                // Try to close on backend immediately
                var syncedSuccessfully = false
                try {
                    val response = apiService.closeSession(
                        SessionUpdateRequest(sessionId, timestamp)
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        // SUCCESS! Mark as synced to prevent duplicate close requests
                        android.util.Log.d("SessionRepo", "✓ Session closed on backend immediately: $sessionId")
                        sessionDao.markSessionSynced(sessionId)
                        syncedSuccessfully = true
                    } else {
                        android.util.Log.d("SessionRepo", "Backend close failed, will retry in background sync")
                    }
                } catch (e: Exception) {
                    // Network error - will sync later via background service
                    android.util.Log.d("SessionRepo", "Network error closing session, will retry in background sync")
                    e.printStackTrace()
                }
                
                // Clean up ONLY synced button presses for this session
                // Unsynced button presses will be retried by background sync service
                kotlinx.coroutines.delay(1000)
                buttonPressDao.deleteSyncedButtonPressesBySession(sessionId)
                android.util.Log.d("SessionRepo", "Cleaned up SYNCED button presses for closed session: $sessionId")
                
                Result.success(Unit)
            } else {
                // Session not found - send to Sentry
                CloudLogger.captureEvent("$userIdPrefix SESSION_CLOSE_FAILED: Session not found - $sessionId", SentryLevel.ERROR)
                Result.failure(Exception("Session not found"))
            }
        } catch (e: Exception) {
            // Critical session close failure - send to Sentry
            CloudLogger.captureEvent("$userIdPrefix SESSION_CLOSE_FAILED: Database error - $sessionId - ${e.message}", SentryLevel.ERROR)
            Result.failure(e)
        }
        }
    }
    
    suspend fun cancelSession(sessionId: String): Result<Unit> {
        return sessionModificationMutex.withLock {
            try {
                val timestamp = System.currentTimeMillis()
                val session = sessionDao.getSessionById(sessionId)
            
            if (session != null) {
                android.util.Log.d("SessionRepo", "Cancelling session: $sessionId")
                
                // Mark session as cancelled locally
                val cancelledSession = session.copy(
                    endTimestamp = timestamp,
                    status = SessionStatus.COMPLETED,  // Or create a CANCELLED status if backend supports it
                    isSynced = false
                )
                sessionDao.updateSession(cancelledSession)
                
                // Try to cancel on backend
                try {
                    val response = apiService.cancelSession(
                        SessionUpdateRequest(sessionId, timestamp)
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        android.util.Log.d("SessionRepo", "✓ Session cancelled on backend: $sessionId")
                        sessionDao.markSessionSynced(sessionId)
                    } else {
                        android.util.Log.d("SessionRepo", "Backend cancel failed, will retry in background sync")
                    }
                } catch (e: Exception) {
                    android.util.Log.d("SessionRepo", "Network error cancelling session, will retry in background sync")
                    e.printStackTrace()
                }
                
                // Clean up ONLY synced button presses for this cancelled session
                // Unsynced ones will be retried by background sync
                kotlinx.coroutines.delay(500)
                buttonPressDao.deleteSyncedButtonPressesBySession(sessionId)
                android.util.Log.d("SessionRepo", "Cleaned up SYNCED button presses for cancelled session: $sessionId")
                
                Result.success(Unit)
            } else {
                // Session not found - send to Sentry
                CloudLogger.captureEvent("$userIdPrefix SESSION_CANCEL_FAILED: Session not found - $sessionId", SentryLevel.ERROR)
                Result.failure(Exception("Session not found"))
            }
        } catch (e: Exception) {
            // Critical session cancel failure - send to Sentry
            CloudLogger.captureEvent("$userIdPrefix SESSION_CANCEL_FAILED: Database error - $sessionId - ${e.message}", SentryLevel.ERROR)
            android.util.Log.e("SessionRepo", "Error cancelling session", e)
            Result.failure(e)
        }
        }
    }
    
    suspend fun recordButtonPress(sessionId: String, action: ButtonAction, floorIndex: Int? = null): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            val buttonPress = ButtonPress(
                sessionId = sessionId,
                action = action.name,
                timestamp = timestamp,
                floorIndex = floorIndex,  // Include floor number
                isSynced = false  // Mark as not synced, queue will handle it
            )
            
            // Save to queue, background service will sync
            buttonPressDao.insertButtonPress(buttonPress)
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SessionRepo", "$userIdPrefix ❌ Button save failed: ${e.message}")
            
            // Critical database failure - send to Sentry
            CloudLogger.captureEvent("$userIdPrefix DB_INSERT_FAILED: Button press insert failed - ${action.name} - ${e.message}", SentryLevel.ERROR)
            
            Result.failure(e)
        }
    }
    
    suspend fun getLastButtonPress(sessionId: String): ButtonPress? {
        return buttonPressDao.getLastButtonPress(sessionId)
    }
    
    fun getButtonPressesForSession(sessionId: String): Flow<List<ButtonPress>> {
        return buttonPressDao.getButtonPressesBySession(sessionId)
    }
    
    suspend fun syncUnsyncedData(): Boolean {
        // Get unsynced data OUTSIDE the lock to minimize lock time
        val unsyncedButtonPresses = buttonPressDao.getUnsyncedButtonPresses()
        val unsyncedSessions = sessionDao.getUnsyncedSessions()
        
        // Early return if nothing to sync - don't even acquire lock
        if (unsyncedButtonPresses.isEmpty() && unsyncedSessions.isEmpty()) {
            return true
        }
        
        return syncMutex.withLock {
            try {
                var allSuccess = true
                
                // Sync button presses ONE BY ONE from queue
            
            if (unsyncedButtonPresses.isEmpty()) {
                // No buttons to sync
            } else {
                android.util.Log.d("SessionRepo", "$userIdPrefix 📤 Syncing ${unsyncedButtonPresses.size} button presses")
            }
            
            for ((index, buttonPress) in unsyncedButtonPresses.withIndex()) {
                try {
                    val request = ButtonPressRequest(
                        buttonPress.sessionId,
                        buttonPress.action,
                        buttonPress.timestamp,
                        buttonPress.floorIndex
                    )
                    
                    val response = apiService.submitButtonPress(request)
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        buttonPressDao.markButtonPressSynced(buttonPress.id)
                        android.util.Log.d("SessionRepo", "$userIdPrefix   ✓ ${buttonPress.action}")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("SessionRepo", "$userIdPrefix   ❌ ${buttonPress.action} - HTTP ${response.code()}")
                        android.util.Log.e("SessionRepo", "$userIdPrefix      Error: $errorBody")
                        
                        // Send critical failures to Sentry
                        if (response.code() >= 500) {
                            CloudLogger.captureEvent("$userIdPrefix BUTTON_UPLOAD_FAILED: Server error ${response.code()} - ${buttonPress.action}", SentryLevel.ERROR)
                        } else if (response.code() == 401) {
                            CloudLogger.captureEvent("$userIdPrefix BUTTON_UPLOAD_FAILED: Authentication failed - ${buttonPress.action}", SentryLevel.ERROR)
                        }
                        
                        allSuccess = false
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SessionRepo", "$userIdPrefix   ❌ ${buttonPress.action} - ${e.message}")
                    
                    // Send network exceptions to Sentry
                    CloudLogger.captureEvent("$userIdPrefix BUTTON_UPLOAD_FAILED: Network exception - ${buttonPress.action} - ${e.message}", SentryLevel.ERROR)
                    
                    allSuccess = false
                    break
                }
            }
            
            // Sync sessions ONE BY ONE from queue (data already fetched outside lock)
            for (session in unsyncedSessions) {
                try {
                    if (session.endTimestamp != null) {
                        val response = apiService.closeSession(
                            SessionUpdateRequest(session.sessionId, session.endTimestamp)
                        )
                        if (response.isSuccessful && response.body()?.success == true) {
                            sessionDao.markSessionSynced(session.sessionId)
                        } else {
                            // Send session close sync failures to Sentry
                            CloudLogger.captureEvent("$userIdPrefix SESSION_SYNC_FAILED: Close session failed - ${session.sessionId} - HTTP ${response.code()}", SentryLevel.ERROR)
                            allSuccess = false
                            break
                        }
                    } else {
                        val response = apiService.createSession(
                            SessionCreateRequest(session.sessionId, session.startTimestamp)
                        )
                        if (response.isSuccessful && response.body()?.success == true) {
                            sessionDao.markSessionSynced(session.sessionId)
                        } else {
                            // Send session create sync failures to Sentry
                            CloudLogger.captureEvent("$userIdPrefix SESSION_SYNC_FAILED: Create session failed - ${session.sessionId} - HTTP ${response.code()}", SentryLevel.ERROR)
                            allSuccess = false
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Send session sync network exceptions to Sentry
                    CloudLogger.captureEvent("$userIdPrefix SESSION_SYNC_FAILED: Network exception - ${session.sessionId} - ${e.message}", SentryLevel.ERROR)
                    e.printStackTrace()
                    allSuccess = false
                    break
                }
            }
            
            allSuccess
        } catch (e: Exception) {
            // Send overall sync failure to Sentry
            CloudLogger.captureEvent("$userIdPrefix SYNC_FAILED: Overall sync process failed - ${e.message}", SentryLevel.ERROR)
            e.printStackTrace()
            false
        }
        }
    }
    
    suspend fun fetchSessionsFromServer() {
        try {
            val response = apiService.getSessions()
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.forEach { session ->
                    sessionDao.insertSession(session)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

