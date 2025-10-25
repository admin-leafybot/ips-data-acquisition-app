package ai.indoorbrain.data.remote

import ai.indoorbrain.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferencesManager: PreferencesManager,
    private val tokenManager: TokenManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for login/signup/refresh-token endpoints
        val url = originalRequest.url.encodedPath
        if (url.contains("/user/login") || 
            url.contains("/user/signup") || 
            url.contains("/user/refresh-token")) {
            android.util.Log.d("AuthInterceptor", "Skipping auth for: $url")
            return chain.proceed(originalRequest)
        }
        
        // Get valid token (refreshes if needed)
        val token = runBlocking {
            tokenManager.getValidToken()
        }
        
        android.util.Log.d("AuthInterceptor", "Request to: $url")
        android.util.Log.d("AuthInterceptor", "Token present: ${token != null}, length: ${token?.length ?: 0}")
        if (token != null) {
            android.util.Log.d("AuthInterceptor", "Token preview: ${token.take(20)}...")
        }
        
        // Add Authorization header if token exists
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            android.util.Log.w("AuthInterceptor", "No token available for authenticated request!")
            originalRequest
        }
        
        // Make the request
        val response = chain.proceed(newRequest)
        
        // Handle 401 Unauthorized - Token expired
        if (response.code == 401) {
            android.util.Log.w("AuthInterceptor", "401 Unauthorized - attempting token refresh")
            
            // Close the current response
            response.close()
            
            // Try to refresh token (thread-safe)
            val refreshSuccess = runBlocking {
                tokenManager.handle401Error()
            }
            
            if (refreshSuccess) {
                android.util.Log.d("AuthInterceptor", "Token refreshed successfully, retrying request")
                
                // Get new token
                val newToken = runBlocking {
                    tokenManager.getValidToken()
                }
                
                // Retry request with new token
                val retryRequest = if (newToken != null) {
                    originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                } else {
                    originalRequest
                }
                
                return chain.proceed(retryRequest)
            } else {
                android.util.Log.e("AuthInterceptor", "Token refresh failed - user logged out")
                // Return the original 401 response - user has been logged out
                return response
            }
        }
        
        return response
    }
}

