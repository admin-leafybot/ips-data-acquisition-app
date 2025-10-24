package ai.indoorbrain.data.remote

import ai.indoorbrain.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferencesManager: PreferencesManager
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
        
        // Get auth token
        val token = runBlocking {
            preferencesManager.getAuthToken()
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
        
        return chain.proceed(newRequest)
    }
}

