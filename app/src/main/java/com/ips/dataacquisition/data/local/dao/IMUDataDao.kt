package com.ips.dataacquisition.data.local.dao

import androidx.room.*
import com.ips.dataacquisition.data.model.IMUData

@Dao
interface IMUDataDao {
    @Query("SELECT * FROM imu_data WHERE is_synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedIMUData(limit: Int = 1000): List<IMUData>
    
    @Query("SELECT COUNT(*) FROM imu_data WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Query("SELECT COUNT(*) FROM imu_data")
    suspend fun getTotalCount(): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIMUData(imuData: IMUData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIMUDataBatch(imuDataList: List<IMUData>)
    
    @Query("UPDATE imu_data SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markIMUDataSynced(ids: List<Long>)
    
    @Query("DELETE FROM imu_data WHERE timestamp < :timestampBefore AND is_synced = 1")
    suspend fun deleteOldSyncedData(timestampBefore: Long)
    
    @Query("DELETE FROM imu_data WHERE session_id = :sessionId")
    suspend fun deleteIMUDataBySession(sessionId: String)
    
    @Query("DELETE FROM imu_data WHERE id IN (:ids)")
    suspend fun deleteIMUDataByIds(ids: List<Long>)
}

