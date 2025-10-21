package com.ips.dataacquisition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.data.model.User
import com.ips.dataacquisition.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _signupSuccess = MutableStateFlow(false)
    val signupSuccess: StateFlow<Boolean> = _signupSuccess.asStateFlow()
    
    private val _signupSuccessMessage = MutableStateFlow<String?>(null)
    val signupSuccessMessage: StateFlow<String?> = _signupSuccessMessage.asStateFlow()
    
    init {
        checkAuthentication()
        startTokenExpiryMonitor()
    }
    
    private fun checkAuthentication() {
        viewModelScope.launch {
            preferencesManager.getUser().collect { user ->
                _currentUser.value = user
                _isAuthenticated.value = user != null
            }
        }
    }
    
    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = authRepository.login(phone, password)
            
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
                _isAuthenticated.value = true
                android.util.Log.d("AuthViewModel", "Login successful: ${result.getOrNull()?.fullName}")
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Login failed"
                android.util.Log.e("AuthViewModel", "Login failed", result.exceptionOrNull())
            }
            
            _isLoading.value = false
        }
    }
    
    fun signup(phone: String, password: String, fullName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = authRepository.signup(phone, password, fullName)
            
            if (result.isSuccess) {
                android.util.Log.d("AuthViewModel", "Signup successful")
                _signupSuccessMessage.value = "Account created! Please wait for admin approval before logging in."
                _signupSuccess.value = true
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Signup failed"
                android.util.Log.e("AuthViewModel", "Signup failed", result.exceptionOrNull())
            }
            
            _isLoading.value = false
        }
    }
    
    fun clearSignupSuccess() {
        _signupSuccess.value = false
        _signupSuccessMessage.value = null
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _currentUser.value = null
            _isAuthenticated.value = false
            android.util.Log.d("AuthViewModel", "User logged out")
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // Auto token refresh monitor
    private fun startTokenExpiryMonitor() {
        viewModelScope.launch {
            while (true) {
                delay(60_000) // Check every minute
                
                val user = _currentUser.value
                if (user != null) {
                    val timeUntilExpiry = user.tokenExpiresAt - System.currentTimeMillis()
                    
                    // Refresh token if expiring in next 10 minutes
                    if (timeUntilExpiry < 10 * 60 * 1000L) {
                        android.util.Log.d("AuthViewModel", "Token expiring soon, refreshing...")
                        refreshTokenIfNeeded()
                    }
                }
            }
        }
    }
    
    private suspend fun refreshTokenIfNeeded() {
        val isExpired = authRepository.isTokenExpired()
        if (isExpired) {
            android.util.Log.d("AuthViewModel", "Token expired, attempting refresh...")
            val result = authRepository.refreshToken()
            
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
                android.util.Log.d("AuthViewModel", "Token refreshed successfully")
            } else {
                android.util.Log.e("AuthViewModel", "Token refresh failed, logging out user")
                logout()
            }
        }
    }
}

