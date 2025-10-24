package ai.indoorbrain.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.indoorbrain.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppVersionViewModel(
    private val appRepository: AppRepository
) : ViewModel() {
    
    private val _isVersionSupported = MutableStateFlow<Boolean?>(null)  // null = checking, true = supported, false = unsupported
    val isVersionSupported: StateFlow<Boolean?> = _isVersionSupported.asStateFlow()
    
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()
    
    fun checkAppVersion(versionName: String) {
        viewModelScope.launch {
            try {
                _isChecking.value = true
                Log.d("AppVersionViewModel", "Checking app version: $versionName")
                
                val result = appRepository.checkAppVersion(versionName)
                
                if (result.isSuccess) {
                    val isSupported = result.getOrNull() ?: true  // Default to true on null
                    _isVersionSupported.value = isSupported
                    Log.d("AppVersionViewModel", "Version check result: isSupported=$isSupported")
                } else {
                    // On error, assume version is supported (fail-open)
                    _isVersionSupported.value = true
                    Log.e("AppVersionViewModel", "Version check failed, allowing app to continue")
                }
            } catch (e: Exception) {
                // On exception, assume version is supported (fail-open)
                _isVersionSupported.value = true
                Log.e("AppVersionViewModel", "Version check exception, allowing app to continue", e)
            } finally {
                _isChecking.value = false
            }
        }
    }
}

