package com.ips.dataacquisition.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ips.dataacquisition.data.local.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    
    init {
        loadLanguage()
    }
    
    private fun loadLanguage() {
        viewModelScope.launch {
            preferencesManager.getLanguage().collect { language ->
                _currentLanguage.value = language
            }
        }
    }
    
    fun changeLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(languageCode)
            _currentLanguage.value = languageCode
        }
    }
}

