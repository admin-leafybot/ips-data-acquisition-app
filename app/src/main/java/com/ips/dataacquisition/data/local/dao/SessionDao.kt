package com.ips.dataacquisition.data.local.dao

import androidx.room.*
import com.ips.dataacquisition.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY start_timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>
    
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId")
    suspend fun getSessionById(sessionId: String): Session?
    
    @Query("SELECT * FROM sessions WHERE end_timestamp IS NULL ORDER BY start_timestamp DESC LIMIT 1")
    suspend fun getActiveSession(): Session?
    
    @Query("SELECT * FROM sessions WHERE is_synced = 0")
    suspend fun getUnsyncedSessions(): List<Session>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)
    
    @Update
    suspend fun updateSession(session: Session)
    
    @Query("UPDATE sessions SET is_synced = 1 WHERE session_id = :sessionId")
    suspend fun markSessionSynced(sessionId: String)
    
    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

