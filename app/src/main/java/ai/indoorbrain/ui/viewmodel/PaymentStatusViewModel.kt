package ai.indoorbrain.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.indoorbrain.data.model.Session
import ai.indoorbrain.data.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PaymentStatusViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        loadSessions()
    }
    
    private fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            sessionRepository.getAllSessions()
                .collect { sessionList ->
                    _sessions.value = sessionList.filter { 
                        it.endTimestamp != null 
                    }
                    // Set loading to false after first emission
                    _isLoading.value = false
                }
        }
    }
    
    fun refreshSessions() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                sessionRepository.fetchSessionsFromServer()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

