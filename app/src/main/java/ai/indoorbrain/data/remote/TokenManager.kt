package ai.indoorbrain.data.remote

import ai.indoorbrain.data.local.PreferencesManager
import ai.indoorbrain.data.model.RefreshTokenRequest
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

/**
 * Thread-safe token management with automatic refresh and logout on failure
 */
class TokenManager(
    private val preferencesManager: PreferencesManager,
    private val apiService: ApiService
) {
    
    private val refreshMutex = Mutex()
    private var isRefreshing = false
    
    companion object {
        private const val TAG = "TokenManager"
    }
    
    /**
     * Get current auth token, refreshing if needed
     * Thread-safe: only one refresh operation at a time
     */
    suspend fun getValidToken(): String? {
        return refreshMutex.withLock {
            val currentUser = preferencesManager.getUser().first() ?: return@withLock null
            
            // Check if token is expired
            if (System.currentTimeMillis() >= currentUser.tokenExpiresAt) {
                Log.d(TAG, "Token expired, refreshing...")
                
                // Prevent multiple simultaneous refresh attempts
                if (isRefreshing) {
                    Log.d(TAG, "Token refresh already in progress, waiting...")
                    // Wait for current refresh to complete
                    while (isRefreshing) {
                        kotlinx.coroutines.delay(100)
                    }
                    // Return the refreshed token
                    return@withLock preferencesManager.getAuthToken()
                }
                
                isRefreshing = true
                try {
                    val success = refreshTokenInternal()
                    if (success) {
                        Log.d(TAG, "Token refreshed successfully")
                        return@withLock preferencesManager.getAuthToken()
                    } else {
                        Log.e(TAG, "Token refresh failed, user needs to logout")
                        logoutUser()
                        return@withLock null
                    }
                } finally {
                    isRefreshing = false
                }
            }
            
            return@withLock currentUser.token
        }
    }
    
    /**
     * Handle 401 error by refreshing token and retrying
     * Thread-safe: prevents multiple simultaneous refresh attempts
     */
    suspend fun handle401Error(): Boolean {
        return refreshMutex.withLock {
            if (isRefreshing) {
                Log.d(TAG, "Token refresh already in progress, waiting...")
                // Wait for current refresh to complete
                while (isRefreshing) {
                    kotlinx.coroutines.delay(100)
                }
                return@withLock true // Assume refresh succeeded
            }
            
            isRefreshing = true
            try {
                val success = refreshTokenInternal()
                if (success) {
                    Log.d(TAG, "Token refreshed after 401 error")
                    return@withLock true
                } else {
                    Log.e(TAG, "Token refresh failed after 401, logging out user")
                    logoutUser()
                    return@withLock false
                }
            } finally {
                isRefreshing = false
            }
        }
    }
    
    /**
     * Internal token refresh logic
     */
    private suspend fun refreshTokenInternal(): Boolean {
        return try {
            val currentUser = preferencesManager.getUser().first() ?: return false
            
            val request = RefreshTokenRequest(currentUser.refreshToken)
            val response = apiService.refreshToken(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.token != null && body.refreshToken != null) {
                    val updatedUser = currentUser.copy(
                        token = body.token,
                        refreshToken = body.refreshToken,
                        tokenExpiresAt = parseExpiresAt(body.expiresAt, body.expiresIn)
                    )
                    
                    // Update stored user data
                    preferencesManager.saveUser(updatedUser)
                    Log.d(TAG, "Token refreshed and saved successfully")
                    return true
                }
            }
            
            Log.e(TAG, "Token refresh failed: ${response.code()}")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            return false
        }
    }
    
    /**
     * Logout user when token refresh fails
     */
    private suspend fun logoutUser() {
        try {
            Log.w(TAG, "Logging out user due to token refresh failure")
            preferencesManager.clearUser()
            
            // Notify background services to stop
            notifyAuthFailure()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout", e)
        }
    }
    
    /**
     * Notify background services about authentication failure
     */
    private fun notifyAuthFailure() {
        // This will be implemented to notify services
        Log.d(TAG, "Notifying services about auth failure")
    }
    
    /**
     * Parse token expiry time
     */
    private fun parseExpiresAt(expiresAt: String?, expiresIn: Int?): Long {
        if (expiresAt != null) {
            return try {
                java.time.Instant.parse(expiresAt).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis() + (expiresIn ?: 86400) * 1000L
            }
        }
        return System.currentTimeMillis() + (expiresIn ?: 86400) * 1000L
    }
    
    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        val user = preferencesManager.getUser().first()
        return user != null && System.currentTimeMillis() < user.tokenExpiresAt
    }
}
