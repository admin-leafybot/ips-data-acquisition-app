package ai.indoorbrain.data.repository

import ai.indoorbrain.data.local.PreferencesManager
import android.content.Context
import android.util.Log
import ai.indoorbrain.data.local.dao.IMUDataDao
import ai.indoorbrain.data.model.IMUData
import ai.indoorbrain.data.remote.ApiService
import ai.indoorbrain.data.remote.dto.IMUDataUploadRequest
import ai.indoorbrain.util.CloudLogger
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IMURepository(
    private val imuDataDao: IMUDataDao,
    private val apiService: ApiService,
    private val context: Context
) {
    private val preferencesManager = PreferencesManager(context)
    private var userIdPrefix = ""
    
    init {
        // Initialize userId prefix for logging
        CoroutineScope(Dispatchers.IO).launch {
            userIdPrefix = "[${preferencesManager.getUserIdForLogging()}] "
        }
    }
    
    suspend fun saveIMUData(imuData: IMUData) {
        imuDataDao.insertIMUData(imuData)
    }
    
    suspend fun saveIMUDataBatch(imuDataList: List<IMUData>) {
        try {
            imuDataDao.insertIMUDataBatch(imuDataList)
        } catch (e: Exception) {
            // Critical database failure - send to Sentry
            CloudLogger.captureEvent("$userIdPrefix DB_INSERT_FAILED: IMU data batch insert failed - ${e.message}", SentryLevel.ERROR)
            throw e // Re-throw to maintain existing error handling
        }
    }
    
    suspend fun getUnsyncedCount(): Int {
        return imuDataDao.getUnsyncedCount()
    }
    
    suspend fun syncIMUData(): Boolean {
        return try {
            var allSuccess = true
            
            // Get total pending count for logging
            val totalPending = imuDataDao.getUnsyncedCount()
            
            // Get unsynced data in batches
            val unsyncedData = imuDataDao.getUnsyncedIMUData(limit = 1000)
            
            if (unsyncedData.isEmpty()) {
                return true
            }
            
            // Group by session and send batch by batch
            val groupedBySession = unsyncedData.groupBy { it.sessionId }
            Log.d("IMURepository", "$userIdPrefix 📤 Syncing ${unsyncedData.size}/$totalPending IMU points (${groupedBySession.size} batches)")
            
            for ((index, entry) in groupedBySession.entries.withIndex()) {
                val (sessionId, dataPoints) = entry
                try {
                    val request = IMUDataUploadRequest(
                        sessionId = sessionId,
                        dataPoints = dataPoints
                    )
                    
                    Log.d("IMURepository", "$userIdPrefix   → /api/v1/imu-data/upload (${dataPoints.size} points)")
                    val response = apiService.uploadIMUData(request)
                    
                    // Log status code and response
                    Log.d("IMURepository", "$userIdPrefix   ← HTTP ${response.code()}")
                    val responseBody = response.body()
                    if (responseBody != null) {
                        Log.d("IMURepository", "$userIdPrefix   ← Response: ${responseBody}")
                    }
                    
                    if (response.isSuccessful && responseBody?.success == true) {
                        // Delete in chunks to avoid SQLite parameter limit (999)
                        val ids = dataPoints.map { it.id }
                        ids.chunked(500).forEach { chunk ->
                            imuDataDao.deleteIMUDataByIds(chunk)
                        }
                        Log.d("IMURepository", "$userIdPrefix   ✓ Batch ${index + 1}: ${dataPoints.size} points synced")
                        
                        if (index < groupedBySession.size - 1) {
                            delay(100)
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("IMURepository", "$userIdPrefix   ❌ Batch ${index + 1} - HTTP ${response.code()}")
                        Log.e("IMURepository", "$userIdPrefix      Response body: ${responseBody}")
                        Log.e("IMURepository", "$userIdPrefix      Error body: $errorBody")
                        
                        // Send critical failures to Sentry
                        if (response.code() >= 500) {
                            CloudLogger.captureEvent("$userIdPrefix IMU_UPLOAD_FAILED: Server error ${response.code()} - ${errorBody}", SentryLevel.ERROR)
                        } else if (response.code() == 401) {
                            CloudLogger.captureEvent("$userIdPrefix IMU_UPLOAD_FAILED: Authentication failed", SentryLevel.ERROR)
                        }
                        
                        allSuccess = false
                        break
                    }
                } catch (e: Exception) {
                    Log.e("IMURepository", "$userIdPrefix   ❌ Batch ${index + 1} - ${e.message}")
                    
                    // Only send unexpected exceptions to Sentry (filter out common network errors)
                    val isExpectedNetworkError = e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                                 e.message?.contains("Failed to connect", ignoreCase = true) == true ||
                                                 e.message?.contains("Network is unreachable", ignoreCase = true) == true ||
                                                 e.message?.contains("was cancelled", ignoreCase = true) == true
                    
                    if (!isExpectedNetworkError) {
                        CloudLogger.captureEvent("$userIdPrefix IMU_UPLOAD_FAILED: Unexpected exception - ${e.message}", SentryLevel.ERROR)
                    }
                    
                    allSuccess = false
                    break
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

