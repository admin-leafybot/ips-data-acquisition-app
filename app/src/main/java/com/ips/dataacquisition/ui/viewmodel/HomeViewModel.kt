package com.ips.dataacquisition.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.data.model.ButtonAction
import com.ips.dataacquisition.data.model.ButtonPress
import com.ips.dataacquisition.data.model.Session
import com.ips.dataacquisition.data.remote.RetrofitClientFactory
import com.ips.dataacquisition.data.repository.IMURepository
import com.ips.dataacquisition.data.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()
    
    private val _buttonPresses = MutableStateFlow<List<ButtonPress>>(emptyList())
    val buttonPresses: StateFlow<List<ButtonPress>> = _buttonPresses.asStateFlow()
    
    private val _availableActions = MutableStateFlow<List<ButtonAction>>(
        listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)  // Session starts here now
    )
    val availableActions: StateFlow<List<ButtonAction>> = _availableActions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _isCollectingData = MutableStateFlow(false)
    val isCollectingData: StateFlow<Boolean> = _isCollectingData.asStateFlow()
    
    private val _samplesCollected = MutableStateFlow(0L)
    val samplesCollected: StateFlow<Long> = _samplesCollected.asStateFlow()
    
    // Floor selection state
    private val _showFloorDialog = MutableStateFlow(false)
    val showFloorDialog: StateFlow<Boolean> = _showFloorDialog.asStateFlow()
    
    private val _pendingAction = MutableStateFlow<ButtonAction?>(null)
    val pendingAction: StateFlow<ButtonAction?> = _pendingAction.asStateFlow()
    
    // Session completion success message
    private val _showSuccessMessage = MutableStateFlow(false)
    val showSuccessMessage: StateFlow<Boolean> = _showSuccessMessage.asStateFlow()
    
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()
    
    // Timeout warning state
    data class TimeoutWarningState(
        val minutesElapsed: Int,
        val message: String,
        val isTimeout: Boolean // true = force cancel, false = warning
    )
    
    private val _showTimeoutWarning = MutableStateFlow<TimeoutWarningState?>(null)
    val showTimeoutWarning: StateFlow<TimeoutWarningState?> = _showTimeoutWarning.asStateFlow()
    
    private var hasEnteredDeliveryBuilding = false
    private var buttonPressFlowJob: kotlinx.coroutines.Job? = null
    private var autoOfflineJob: kotlinx.coroutines.Job? = null
    private var pendingSyncMonitorJob: kotlinx.coroutines.Job? = null
    
    private lateinit var imuRepository: IMURepository
    
    init {
        // Initialize IMU repository
        val database = AppDatabase.getDatabase(context)
        imuRepository = IMURepository(
            database.imuDataDao(),
            RetrofitClientFactory.apiService,
            context
        )
        
        loadActiveSession()
        observeOnlineState()
        observeCollectionState()
        startAutoOfflineMonitor()
        startPendingSyncMonitor()
    }
    
    private fun loadActiveSession() {
        viewModelScope.launch {
            try {
                // Initially load the active session
                val session = sessionRepository.getActiveSession()
                _activeSession.value = session
                
                session?.let { activeSession ->
                    // Start observing button presses
                    observeButtonPresses(activeSession.sessionId)
                }
                
                // Start observing active session changes (e.g., when DataSyncService auto-cancels)
                sessionRepository.observeActiveSession().collect { updatedSession ->
                    if (_activeSession.value?.sessionId != updatedSession?.sessionId) {
                        // Session changed (created, closed, or cancelled)
                        _activeSession.value = updatedSession
                        
                        if (updatedSession != null) {
                            observeButtonPresses(updatedSession.sessionId)
                        } else {
                            // Session was closed/cancelled
                            buttonPressFlowJob?.cancel()
                            _buttonPresses.value = emptyList()
                            hasEnteredDeliveryBuilding = false
                            _availableActions.value = listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)
                        }
                    }
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
        val lastPress = presses.lastOrNull()
        
        // Check if user has entered delivery building in this session
        hasEnteredDeliveryBuilding = presses.any { 
            it.action == ButtonAction.ENTERED_DELIVERY_BUILDING.name 
        }
        
        val lastAction = lastPress?.let { 
            try {
                ButtonAction.valueOf(it.action)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ Invalid action: ${it.action}")
                null
            }
        }
        
        val nextActions = ButtonAction.getNextActions(lastAction, hasEnteredDeliveryBuilding, presses)
        _availableActions.value = nextActions
    }
    
    private fun observeOnlineState() {
        viewModelScope.launch {
            preferencesManager.isOnline.collect { online ->
                _isOnline.value = online
                
                if (!online) {
                    // Reset samples counter when going offline
                    preferencesManager.resetSamplesCollected()
                }
            }
        }
    }
    
    private fun observeCollectionState() {
        viewModelScope.launch {
            preferencesManager.isCollectingData.collect { collecting ->
                _isCollectingData.value = collecting
            }
        }
        
        viewModelScope.launch {
            preferencesManager.samplesCollected.collect { samples ->
                _samplesCollected.value = samples
            }
        }
    }
    
    private fun startPendingSyncMonitor() {
        pendingSyncMonitorJob = viewModelScope.launch {
            while (true) {
                try {
                    // Query pending count from repositories
                    val pendingIMU = imuRepository.getUnsyncedCount()
                    val pendingButtons = sessionRepository.getUnsyncedButtonPressCount()
                    val total = pendingIMU + pendingButtons
                    
                    _pendingSyncCount.value = total
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Error checking pending sync count", e)
                }
                
                // Check every 30 seconds
                delay(30_000)
            }
        }
    }
    
    private fun startAutoOfflineMonitor() {
        autoOfflineJob = viewModelScope.launch {
            while (true) {
                delay(60_000) // Check every minute
                
                if (_isOnline.value) {
                    val timedOut = preferencesManager.checkAndHandleTimeout()
                    if (timedOut) {
                        android.util.Log.w("HomeViewModel", "User auto-offlined due to inactivity")
                        _errorMessage.value = "Auto-offline: No activity for 2 hours"
                    }
                }
            }
        }
    }
    
    fun toggleOnlineStatus() {
        viewModelScope.launch {
            val newState = !_isOnline.value
            preferencesManager.setOnline(newState)
            
            if (newState) {
                // Going online
                android.util.Log.d("HomeViewModel", "User went ONLINE")
                preferencesManager.updateLastActivity()
            } else {
                // Going offline
                android.util.Log.d("HomeViewModel", "User went OFFLINE")
            }
        }
    }
    
    fun onButtonPress(action: ButtonAction, floorIndex: Int? = null) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "🔘 ${action.name}${if (floorIndex != null) " (Floor $floorIndex)" else ""}")
            
            // Check if user is online
            if (!_isOnline.value) {
                _errorMessage.value = "Please go ONLINE to record data"
                return@launch
            }
            
            // Check for session timeout (only if session exists and not starting a new session)
            val currentSession = _activeSession.value
            if (currentSession != null && action != ButtonAction.LEFT_RESTAURANT_BUILDING) {
                when (val validation = sessionRepository.validateSessionTimeout(currentSession.sessionId)) {
                    is SessionRepository.TimeoutValidation.Timeout -> {
                        // Force cancel session due to timeout
                        _showTimeoutWarning.value = TimeoutWarningState(
                            minutesElapsed = validation.minutesElapsed,
                            message = validation.message,
                            isTimeout = true
                        )
                        return@launch
                    }
                    SessionRepository.TimeoutValidation.Valid -> {
                        // Continue normally
                    }
                }
            }
            
            // Check if this action requires floor input (context-aware)
            if (ButtonAction.requiresFloorInput(action, _buttonPresses.value) && floorIndex == null) {
                _pendingAction.value = action
                _showFloorDialog.value = true
                return@launch
            }
            
            _isLoading.value = true
            _errorMessage.value = null
            
            // Update last activity timestamp
            preferencesManager.updateLastActivity()
            
            try {
                // Start session if this is the first button press
                val sessionId = if (_activeSession.value == null && 
                    action == ButtonAction.LEFT_RESTAURANT_BUILDING) {
                    // Create new session
                    val result = sessionRepository.createSession()
                    if (result.isSuccess) {
                        val newSessionId = result.getOrNull()!!
                        
                        // Reload active session from database
                        val session = sessionRepository.getActiveSession()
                        _activeSession.value = session
                        
                        // Start observing button presses for this new session
                        session?.let {
                            observeButtonPresses(it.sessionId)
                        }
                        
                        newSessionId
                    } else {
                        throw result.exceptionOrNull() ?: Exception("Failed to create session")
                    }
                } else {
                    _activeSession.value?.sessionId 
                        ?: throw Exception("No active session")
                }
                
                // Record button press to database (queued for sync)
                val result = sessionRepository.recordButtonPress(sessionId, action, floorIndex)
                
                if (result.isFailure) {
                    android.util.Log.e("HomeViewModel", "❌ Button save failed: ${result.exceptionOrNull()?.message}")
                    _errorMessage.value = result.exceptionOrNull()?.message
                }
                
                // Control IMU data capture based on button pressed (pass sessionId for final 3-min capture)
                controlIMUDataCapture(action, sessionId)
                
                // Close session if this is the last button (after starting the final 3-min capture)
                if (action == ButtonAction.LEAVING_SOCIETY) {
                    sessionRepository.closeSession(sessionId)
                    
                    // Show success message
                    _showSuccessMessage.value = true
                    
                    // Hide success message after 3 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _showSuccessMessage.value = false
                    }
                    
                    // Reset state
                    buttonPressFlowJob?.cancel()
                    _activeSession.value = null
                    _buttonPresses.value = emptyList()
                    hasEnteredDeliveryBuilding = false
                    _availableActions.value = listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ Button error: ${e.message}")
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun onFloorSelected(floorNumber: Int) {
        viewModelScope.launch {
            _showFloorDialog.value = false
            val action = _pendingAction.value
            if (action != null) {
                _pendingAction.value = null
                onButtonPress(action, floorNumber)
            }
        }
    }
    
    fun dismissFloorDialog() {
        _showFloorDialog.value = false
        _pendingAction.value = null
    }
    
    fun onTimeoutDismiss() {
        // User acknowledged the timeout
        _showTimeoutWarning.value = null
        cancelSession()
    }
    
    /**
     * Control IMU data capture based on which button was pressed
     */
    private fun controlIMUDataCapture(action: ButtonAction, sessionId: String) {
        val intent = Intent(context, com.ips.dataacquisition.service.IMUDataService::class.java)
        
        when (action) {
            ButtonAction.LEFT_RESTAURANT_BUILDING -> {
                android.util.Log.d("HomeViewModel", "📊 START 2-min capture")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_TIMED
                intent.putExtra(com.ips.dataacquisition.service.IMUDataService.EXTRA_DURATION_MS, 2 * 60 * 1000L)
                intent.putExtra("session_id", sessionId)
                context.startService(intent)
            }
            
            ButtonAction.REACHED_SOCIETY_GATE -> {
                android.util.Log.d("HomeViewModel", "📊 START continuous capture")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_CONTINUOUS
                intent.putExtra("session_id", sessionId)
                context.startService(intent)
            }
            
            ButtonAction.LEAVING_SOCIETY -> {
                android.util.Log.d("HomeViewModel", "📊 START 3-min capture (keeping sessionId for final data)")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_TIMED
                intent.putExtra(com.ips.dataacquisition.service.IMUDataService.EXTRA_DURATION_MS, 3 * 60 * 1000L)
                // Pass the sessionId BEFORE closing the session so the final 3 minutes of data are associated with it
                intent.putExtra("session_id", sessionId)
                context.startService(intent)
            }
            
            else -> {
                // Other buttons don't affect data capture
            }
        }
    }
    
    fun cancelSession() {
        viewModelScope.launch {
            try {
                val currentSession = _activeSession.value
                if (currentSession != null) {
                    _isLoading.value = true
                    
                    val result = sessionRepository.cancelSession(currentSession.sessionId)
                    
                    if (result.isSuccess) {
                        // Stop IMU data capture
                        val intent = Intent(context, com.ips.dataacquisition.service.IMUDataService::class.java)
                        intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_STOP_CAPTURE
                        context.startService(intent)
                        
                        // Reset state
                        buttonPressFlowJob?.cancel()
                        _activeSession.value = null
                        _buttonPresses.value = emptyList()
                        hasEnteredDeliveryBuilding = false
                        _availableActions.value = listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)
                        
                        _errorMessage.value = "Session cancelled successfully"
                    } else {
                        _errorMessage.value = "Failed to cancel session: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    _errorMessage.value = "No active session to cancel"
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ Cancel error: ${e.message}")
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        buttonPressFlowJob?.cancel()
        autoOfflineJob?.cancel()
        pendingSyncMonitorJob?.cancel()
    }
}
