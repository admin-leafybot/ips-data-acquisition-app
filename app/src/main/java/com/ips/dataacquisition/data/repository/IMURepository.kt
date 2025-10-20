package com.ips.dataacquisition.data.repository

import com.ips.dataacquisition.data.local.dao.IMUDataDao
import com.ips.dataacquisition.data.model.IMUData
import com.ips.dataacquisition.data.remote.ApiService
import com.ips.dataacquisition.data.remote.dto.IMUDataUploadRequest

class IMURepository(
    private val imuDataDao: IMUDataDao,
    private val apiService: ApiService
) {
    
    suspend fun saveIMUData(imuData: IMUData) {
        imuDataDao.insertIMUData(imuData)
    }
    
    suspend fun saveIMUDataBatch(imuDataList: List<IMUData>) {
        imuDataDao.insertIMUDataBatch(imuDataList)
    }
    
    suspend fun getUnsyncedCount(): Int {
        val count = imuDataDao.getUnsyncedCount()
        android.util.Log.d("IMURepository", "Queried unsynced IMU data count: $count")
        return count
    }
    
    suspend fun syncIMUData(): Boolean {
        return try {
            var allSuccess = true
            
            // Get unsynced data in batches (100 Hz × 3s = 300 records, fetch 1000 to handle backlog)
            val unsyncedData = imuDataDao.getUnsyncedIMUData(limit = 1000)
            android.util.Log.d("IMURepository", "=== IMU QUEUE STATUS ===")
            android.util.Log.d("IMURepository", "Unsynced IMU data points in queue: ${unsyncedData.size}")
            
            if (unsyncedData.isEmpty()) {
                android.util.Log.d("IMURepository", "IMU queue is empty, nothing to sync")
                return true
            }
            
            if (unsyncedData.isNotEmpty()) {
                // Group by session and send batch by batch
                val groupedBySession = unsyncedData.groupBy { it.sessionId }
                android.util.Log.d("IMURepository", "Grouped into ${groupedBySession.size} batches by session")
                
                for ((index, entry) in groupedBySession.entries.withIndex()) {
                    val (sessionId, dataPoints) = entry
                    try {
                        android.util.Log.d("IMURepository", "Processing IMU batch ${index + 1}/${groupedBySession.size}")
                        android.util.Log.d("IMURepository", "  SessionId: $sessionId")
                        android.util.Log.d("IMURepository", "  Data points: ${dataPoints.size}")
                        
                        val request = IMUDataUploadRequest(
                            sessionId = sessionId,
                            dataPoints = dataPoints
                        )
                        
                        android.util.Log.d("IMURepository", "Sending IMU batch to API...")
                        val response = apiService.uploadIMUData(request)
                        android.util.Log.d("IMURepository", "API Response Code: ${response.code()}")
                        
                        if (response.isSuccessful && response.body()?.success == true) {
                            // Delete from database after successful sync (save memory)
                            imuDataDao.deleteIMUDataByIds(dataPoints.map { it.id })
                            android.util.Log.d("IMURepository", "✓ IMU batch synced and deleted from local DB (${dataPoints.size} points)")
                            
                            // Small delay between batches to prevent overwhelming backend
                            if (index < groupedBySession.size - 1) {
                                kotlinx.coroutines.delay(100) // 100ms between batches
                            }
                        } else {
                            val errorBody = response.errorBody()?.string()
                            android.util.Log.e("IMURepository", "✗ API failed for IMU batch: ${response.code()}")
                            android.util.Log.e("IMURepository", "  Error body: $errorBody")
                            allSuccess = false
                            // Stop processing if API fails
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("IMURepository", "✗ Network error syncing IMU batch", e)
                        e.printStackTrace()
                        allSuccess = false
                        // Stop if network error
                        break
                    }
                }
            }
            
            // No need to clean old data - we delete immediately after sync
            
            allSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

