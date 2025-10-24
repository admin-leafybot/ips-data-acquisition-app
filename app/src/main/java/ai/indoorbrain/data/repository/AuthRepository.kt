package ai.indoorbrain.data.repository

import android.util.Log
import ai.indoorbrain.data.local.PreferencesManager
import ai.indoorbrain.data.model.*
import ai.indoorbrain.data.remote.ApiService
import kotlinx.coroutines.flow.first
import java.time.Instant

class AuthRepository(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) {
    
    suspend fun signup(phone: String, password: String, fullName: String): Result<SignupResponse> {
        return try {
            val request = SignupRequest(phone, password, fullName)
            Log.d("AuthRepository", "Signup request: phone=$phone, fullName=$fullName")
            
            val response = apiService.signup(request)
            Log.d("AuthRepository", "Signup response code: ${response.code()}")
            Log.d("AuthRepository", "Signup response body: ${response.body()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d("AuthRepository", "Signup success field: ${body.success}")
                Log.d("AuthRepository", "Signup message: ${body.message}")
                
                if (body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("AuthRepository", "Signup failed: ${response.code()}, error: $errorBody")
                Result.failure(Exception("Signup failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Signup exception", e)
            Result.failure(e)
        }
    }
    
    suspend fun login(phone: String, password: String): Result<User> {
        return try {
            val request = LoginRequest(phone, password)
            Log.d("AuthRepository", "Login request: phone=$phone")
            
            val response = apiService.login(request)
            Log.d("AuthRepository", "Login response code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d("AuthRepository", "Login response: success=${body.success}")
                Log.d("AuthRepository", "Token present: ${body.token != null}, length: ${body.token?.length ?: 0}")
                Log.d("AuthRepository", "RefreshToken present: ${body.refreshToken != null}")
                
                if (body.success && body.token != null && body.refreshToken != null) {
                    val user = User(
                        userId = body.userId ?: "",
                        phone = phone,
                        fullName = body.fullName ?: "",
                        token = body.token,
                        refreshToken = body.refreshToken,
                        tokenExpiresAt = parseExpiresAt(body.expiresAt, body.expiresIn)
                    )
                    
                    Log.d("AuthRepository", "Saving user with token: ${user.token.take(20)}...")
                    
                    // Save user data and tokens
                    preferencesManager.saveUser(user)
                    
                    Log.d("AuthRepository", "User saved successfully")
                    
                    // Verify token was saved
                    val savedToken = preferencesManager.getAuthToken()
                    Log.d("AuthRepository", "Verification - Token saved: ${savedToken != null}, length: ${savedToken?.length ?: 0}")
                    if (savedToken != null) {
                        Log.d("AuthRepository", "Saved token preview: ${savedToken.take(20)}...")
                    }
                    
                    Result.success(user)
                } else {
                    Log.e("AuthRepository", "Login failed: success=${body.success}, message=${body.message}")
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("AuthRepository", "Login HTTP error: ${response.code()}, error: $errorBody")
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login exception", e)
            Result.failure(e)
        }
    }
    
    suspend fun refreshToken(): Result<User> {
        return try {
            val currentUser = preferencesManager.getUser().first() ?: return Result.failure(Exception("No user logged in"))
            
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
                    
                    Result.success(updatedUser)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                Result.failure(Exception("Token refresh failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        preferencesManager.clearUser()
    }
    
    suspend fun getCurrentUser(): User? {
        return preferencesManager.getUser().first()
    }
    
    suspend fun isTokenExpired(): Boolean {
        val user = getCurrentUser() ?: return true
        return System.currentTimeMillis() >= user.tokenExpiresAt
    }
    
    private fun parseExpiresAt(expiresAt: String?, expiresIn: Int?): Long {
        // Try parsing ExpiresAt (ISO 8601)
        if (expiresAt != null) {
            return try {
                Instant.parse(expiresAt).toEpochMilli()
            } catch (e: Exception) {
                // Fallback to ExpiresIn
                System.currentTimeMillis() + (expiresIn ?: 86400) * 1000L
            }
        }
        
        // Fallback to ExpiresIn (seconds)
        return System.currentTimeMillis() + (expiresIn ?: 86400) * 1000L
    }
}

