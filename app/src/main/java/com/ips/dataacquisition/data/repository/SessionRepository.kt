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
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepository(
    private val sessionDao: SessionDao,
    private val buttonPressDao: ButtonPressDao,
    private val apiService: ApiService
) {
    
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()
    
    suspend fun getActiveSession(): Session? = sessionDao.getActiveSession()
    
    suspend fun getUnsyncedButtonPressCount(): Int {
        val count = buttonPressDao.getUnsyncedCount()
        android.util.Log.d("SessionRepo", "Queried unsynced button press count: $count")
        return count
    }
    
    suspend fun createSession(): Result<String> {
        return try {
            val sessionId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val session = Session(
                sessionId = sessionId,
                startTimestamp = timestamp,
                status = SessionStatus.IN_PROGRESS
            )
            
            // Save locally first
            sessionDao.insertSession(session)
            
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
            Result.failure(e)
        }
    }
    
    suspend fun closeSession(sessionId: String): Result<Unit> {
        return try {
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
                
                // Clean up synced button presses for this session
                // Wait a bit to ensure UI has time to show final state
                kotlinx.coroutines.delay(1000)
                buttonPressDao.deleteButtonPressesBySession(sessionId)
                android.util.Log.d("SessionRepo", "Cleaned up button presses for closed session: $sessionId (synced: $syncedSuccessfully)")
                
                Result.success(Unit)
            } else {
                Result.failure(Exception("Session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            android.util.Log.d("SessionRepo", "Button press saved to queue: ${action.name} for session $sessionId, floor: $floorIndex")
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SessionRepo", "Failed to save button press", e)
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
        return try {
            var allSuccess = true
            
            // Sync button presses ONE BY ONE from queue
            val unsyncedButtonPresses = buttonPressDao.getUnsyncedButtonPresses()
            android.util.Log.d("SessionRepo", "=== QUEUE STATUS ===")
            android.util.Log.d("SessionRepo", "Unsynced button presses in queue: ${unsyncedButtonPresses.size}")
            
            if (unsyncedButtonPresses.isEmpty()) {
                android.util.Log.d("SessionRepo", "Queue is empty, nothing to sync")
            }
            
            for ((index, buttonPress) in unsyncedButtonPresses.withIndex()) {
                try {
                    android.util.Log.d("SessionRepo", "Processing queue item ${index + 1}/${unsyncedButtonPresses.size}")
                    android.util.Log.d("SessionRepo", "  ID: ${buttonPress.id}")
                    android.util.Log.d("SessionRepo", "  SessionId: ${buttonPress.sessionId}")
                    android.util.Log.d("SessionRepo", "  Action: ${buttonPress.action}")
                    android.util.Log.d("SessionRepo", "  Timestamp: ${buttonPress.timestamp}")
                    android.util.Log.d("SessionRepo", "  FloorIndex: ${buttonPress.floorIndex}")
                    android.util.Log.d("SessionRepo", "  IsSynced: ${buttonPress.isSynced}")
                    
                    val request = ButtonPressRequest(
                        buttonPress.sessionId,
                        buttonPress.action,
                        buttonPress.timestamp,
                        buttonPress.floorIndex  // Include floor number
                    )
                    
                    // Log the JSON being sent
                    val gson = com.google.gson.Gson()
                    val jsonRequest = gson.toJson(request)
                    android.util.Log.d("SessionRepo", "Sending JSON to API: $jsonRequest")
                    
                    val response = apiService.submitButtonPress(request)
                    android.util.Log.d("SessionRepo", "API Response Code: ${response.code()}")
                    android.util.Log.d("SessionRepo", "API Response Body: ${response.body()}")
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        // Mark as synced but DON'T delete (need for UI flow in active sessions)
                        buttonPressDao.markButtonPressSynced(buttonPress.id)
                        android.util.Log.d("SessionRepo", "✓ Button press synced: ${buttonPress.action}")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("SessionRepo", "✗ API failed for button press")
                        android.util.Log.e("SessionRepo", "  Status: ${response.code()}")
                        android.util.Log.e("SessionRepo", "  Message: ${response.message()}")
                        android.util.Log.e("SessionRepo", "  Error body: $errorBody")
                        allSuccess = false
                        // Stop processing queue if API fails
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SessionRepo", "✗ Network error syncing button press", e)
                    android.util.Log.e("SessionRepo", "  Error type: ${e.javaClass.simpleName}")
                    android.util.Log.e("SessionRepo", "  Error message: ${e.message}")
                    e.printStackTrace()
                    allSuccess = false
                    // Stop if network error
                    break
                }
            }
            
            // Sync sessions ONE BY ONE from queue
            val unsyncedSessions = sessionDao.getUnsyncedSessions()
            for (session in unsyncedSessions) {
                try {
                    if (session.endTimestamp != null) {
                        val response = apiService.closeSession(
                            SessionUpdateRequest(session.sessionId, session.endTimestamp)
                        )
                        if (response.isSuccessful && response.body()?.success == true) {
                            sessionDao.markSessionSynced(session.sessionId)
                        } else {
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
                            allSuccess = false
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    allSuccess = false
                    break
                }
            }
            
            allSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

