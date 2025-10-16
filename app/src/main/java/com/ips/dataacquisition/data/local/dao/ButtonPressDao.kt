package com.ips.dataacquisition.data.local.dao

import androidx.room.*
import com.ips.dataacquisition.data.model.ButtonPress
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonPressDao {
    @Query("SELECT * FROM button_presses WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getButtonPressesBySession(sessionId: String): Flow<List<ButtonPress>>
    
    @Query("SELECT * FROM button_presses WHERE is_synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedButtonPresses(): List<ButtonPress>
    
    @Query("SELECT * FROM button_presses WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastButtonPress(sessionId: String): ButtonPress?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButtonPress(buttonPress: ButtonPress)
    
    @Query("UPDATE button_presses SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markButtonPressesSynced(ids: List<Long>)
    
    @Query("DELETE FROM button_presses WHERE session_id = :sessionId")
    suspend fun deleteButtonPressesBySession(sessionId: String)
}

