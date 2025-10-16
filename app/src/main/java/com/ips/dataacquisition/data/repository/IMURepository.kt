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
        return imuDataDao.getUnsyncedCount()
    }
    
    suspend fun syncIMUData(): Boolean {
        return try {
            var allSuccess = true
            
            // Get unsynced data in batches
            val unsyncedData = imuDataDao.getUnsyncedIMUData(limit = 500)
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
                            // Mark successfully uploaded batch as synced
                            imuDataDao.markIMUDataSynced(dataPoints.map { it.id })
                            android.util.Log.d("IMURepository", "✓ IMU batch synced successfully (${dataPoints.size} points)")
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
            
            // Clean up old synced data (older than 7 days) - only if online
            if (allSuccess) {
                try {
                    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                    imuDataDao.deleteOldSyncedData(sevenDaysAgo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            allSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

