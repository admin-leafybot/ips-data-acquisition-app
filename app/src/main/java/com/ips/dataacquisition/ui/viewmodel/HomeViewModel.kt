package com.ips.dataacquisition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ips.dataacquisition.data.model.ButtonAction
import com.ips.dataacquisition.data.model.ButtonPress
import com.ips.dataacquisition.data.model.Session
import com.ips.dataacquisition.data.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()
    
    private val _buttonPresses = MutableStateFlow<List<ButtonPress>>(emptyList())
    val buttonPresses: StateFlow<List<ButtonPress>> = _buttonPresses.asStateFlow()
    
    private val _availableActions = MutableStateFlow<List<ButtonAction>>(
        listOf(ButtonAction.ENTERED_RESTAURANT_BUILDING)
    )
    val availableActions: StateFlow<List<ButtonAction>> = _availableActions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var hasEnteredDeliveryBuilding = false
    private var buttonPressFlowJob: kotlinx.coroutines.Job? = null
    
    init {
        loadActiveSession()
    }
    
    private fun loadActiveSession() {
        viewModelScope.launch {
            try {
                val session = sessionRepository.getActiveSession()
                _activeSession.value = session
                
                session?.let { activeSession ->
                    // Start observing button presses
                    observeButtonPresses(activeSession.sessionId)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    
    private fun observeButtonPresses(sessionId: String) {
        // Cancel previous observation
        buttonPressFlowJob?.cancel()
        
        // Start new observation
        buttonPressFlowJob = viewModelScope.launch {
            sessionRepository.getButtonPressesForSession(sessionId)
                .collect { presses ->
                    _buttonPresses.value = presses
                    updateAvailableActions(presses)
                }
        }
    }
    
    private fun updateAvailableActions(presses: List<ButtonPress>) {
        android.util.Log.d("HomeViewModel", "Updating available actions. Total presses: ${presses.size}")
        val lastPress = presses.lastOrNull()
        android.util.Log.d("HomeViewModel", "Last button press: ${lastPress?.action}")
        
        // Check if user has entered delivery building in this session
        hasEnteredDeliveryBuilding = presses.any { 
            it.action == ButtonAction.ENTERED_DELIVERY_BUILDING.name 
        }
        
        val lastAction = lastPress?.let { 
            try {
                ButtonAction.valueOf(it.action)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Invalid action in database: ${it.action}")
                null
            }
        }
        
        val nextActions = ButtonAction.getNextActions(lastAction, hasEnteredDeliveryBuilding)
        android.util.Log.d("HomeViewModel", "Next available actions: ${nextActions.map { it.name }}")
        _availableActions.value = nextActions
    }
    
    fun onButtonPress(action: ButtonAction) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "Button pressed: ${action.name}")
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Start session if this is the first button press
                val sessionId = if (_activeSession.value == null && 
                    action == ButtonAction.ENTERED_RESTAURANT_BUILDING) {
                    android.util.Log.d("HomeViewModel", "Creating new session...")
                    // Create new session
                    val result = sessionRepository.createSession()
                    if (result.isSuccess) {
                        val newSessionId = result.getOrNull()!!
                        android.util.Log.d("HomeViewModel", "Session created: $newSessionId")
                        
                        // Reload active session from database
                        val session = sessionRepository.getActiveSession()
                        _activeSession.value = session
                        android.util.Log.d("HomeViewModel", "Active session loaded: ${session?.sessionId}")
                        
                        // Start observing button presses for this new session
                        session?.let {
                            android.util.Log.d("HomeViewModel", "Starting to observe button presses for session: ${it.sessionId}")
                            observeButtonPresses(it.sessionId)
                        }
                        
                        newSessionId
                    } else {
                        throw result.exceptionOrNull() ?: Exception("Failed to create session")
                    }
                } else {
                    android.util.Log.d("HomeViewModel", "Using existing session: ${_activeSession.value?.sessionId}")
                    _activeSession.value?.sessionId 
                        ?: throw Exception("No active session")
                }
                
                // Record button press to database (queued for sync)
                android.util.Log.d("HomeViewModel", "Recording button press: ${action.name} for session: $sessionId")
                val result = sessionRepository.recordButtonPress(sessionId, action)
                
                if (result.isFailure) {
                    android.util.Log.e("HomeViewModel", "Failed to record button press", result.exceptionOrNull())
                    _errorMessage.value = result.exceptionOrNull()?.message
                } else {
                    android.util.Log.d("HomeViewModel", "Button press recorded successfully")
                }
                // Note: UI will update automatically via Flow when database changes
                
                // Close session if this is the last button
                if (action == ButtonAction.LEFT_DELIVERY_BUILDING) {
                    android.util.Log.d("HomeViewModel", "Closing session: $sessionId")
                    sessionRepository.closeSession(sessionId)
                    
                    // Reset state
                    buttonPressFlowJob?.cancel()
                    _activeSession.value = null
                    _buttonPresses.value = emptyList()
                    hasEnteredDeliveryBuilding = false
                    _availableActions.value = listOf(ButtonAction.ENTERED_RESTAURANT_BUILDING)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error in onButtonPress", e)
                _errorMessage.value = e.message
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        buttonPressFlowJob?.cancel()
    }
}
