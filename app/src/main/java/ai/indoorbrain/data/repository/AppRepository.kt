package ai.indoorbrain.data.repository

import ai.indoorbrain.data.model.AppVersionRequest
import ai.indoorbrain.data.remote.ApiService

class AppRepository(
    private val apiService: ApiService
) {
    
    suspend fun checkAppVersion(versionName: String): Result<Boolean> {
        return try {
            android.util.Log.d("AppRepository", "Checking app version: $versionName")
            
            val request = AppVersionRequest(versionName)
            val response = apiService.checkAppVersion(request)
            
            android.util.Log.d("AppRepository", "Version check response code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("AppRepository", "Version check response: isActive=${body.isActive}, message=${body.message}")
                
                // Return the isActive value from the response
                Result.success(body.isActive)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("AppRepository", "Version check HTTP error: ${response.code()}, error: $errorBody")
                
                // On error, allow the app to continue (fail-open)
                Result.success(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Version check exception - allowing app to continue", e)
            // On network error, allow the app to continue (fail-open)
            Result.success(true)
        }
    }
}

