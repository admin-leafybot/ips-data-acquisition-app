package com.ips.dataacquisition.data.repository

import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.data.model.*
import com.ips.dataacquisition.data.remote.ApiService
import kotlinx.coroutines.flow.first
import java.time.Instant

class AuthRepository(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) {
    
    suspend fun signup(phone: String, password: String, fullName: String): Result<SignupResponse> {
        return try {
            val request = SignupRequest(phone, password, fullName)
            android.util.Log.d("AuthRepository", "Signup request: phone=$phone, fullName=$fullName")
            
            val response = apiService.signup(request)
            android.util.Log.d("AuthRepository", "Signup response code: ${response.code()}")
            android.util.Log.d("AuthRepository", "Signup response body: ${response.body()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("AuthRepository", "Signup success field: ${body.success}")
                android.util.Log.d("AuthRepository", "Signup message: ${body.message}")
                
                if (body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("AuthRepository", "Signup failed: ${response.code()}, error: $errorBody")
                Result.failure(Exception("Signup failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Signup exception", e)
            Result.failure(e)
        }
    }
    
    suspend fun login(phone: String, password: String): Result<User> {
        return try {
            val request = LoginRequest(phone, password)
            android.util.Log.d("AuthRepository", "Login request: phone=$phone")
            
            val response = apiService.login(request)
            android.util.Log.d("AuthRepository", "Login response code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("AuthRepository", "Login response: success=${body.success}")
                android.util.Log.d("AuthRepository", "Token present: ${body.token != null}, length: ${body.token?.length ?: 0}")
                android.util.Log.d("AuthRepository", "RefreshToken present: ${body.refreshToken != null}")
                
                if (body.success && body.token != null && body.refreshToken != null) {
                    val user = User(
                        userId = body.userId ?: "",
                        phone = phone,
                        fullName = body.fullName ?: "",
                        token = body.token,
                        refreshToken = body.refreshToken,
                        tokenExpiresAt = parseExpiresAt(body.expiresAt, body.expiresIn)
                    )
                    
                    android.util.Log.d("AuthRepository", "Saving user with token: ${user.token.take(20)}...")
                    
                    // Save user data and tokens
                    preferencesManager.saveUser(user)
                    
                    android.util.Log.d("AuthRepository", "User saved successfully")
                    
                    // Verify token was saved
                    val savedToken = preferencesManager.getAuthToken()
                    android.util.Log.d("AuthRepository", "Verification - Token saved: ${savedToken != null}, length: ${savedToken?.length ?: 0}")
                    if (savedToken != null) {
                        android.util.Log.d("AuthRepository", "Saved token preview: ${savedToken.take(20)}...")
                    }
                    
                    Result.success(user)
                } else {
                    android.util.Log.e("AuthRepository", "Login failed: success=${body.success}, message=${body.message}")
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("AuthRepository", "Login HTTP error: ${response.code()}, error: $errorBody")
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Login exception", e)
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

