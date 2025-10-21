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
            RetrofitClientFactory.apiService
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
    
    private fun observeOnlineState() {
        viewModelScope.launch {
            preferencesManager.isOnline.collect { online ->
                _isOnline.value = online
                android.util.Log.d("HomeViewModel", "Online state changed: $online")
                
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
                android.util.Log.d("HomeViewModel", "Collection state changed: $collecting")
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
                    
                    android.util.Log.d("HomeViewModel", "=== PENDING SYNC MONITOR ===")
                    android.util.Log.d("HomeViewModel", "Pending buttons: $pendingButtons")
                    android.util.Log.d("HomeViewModel", "Pending IMU: $pendingIMU")
                    android.util.Log.d("HomeViewModel", "Total pending: $total")
                    android.util.Log.d("HomeViewModel", "Previous UI count: ${_pendingSyncCount.value}")
                    
                    _pendingSyncCount.value = total
                    
                    android.util.Log.d("HomeViewModel", "Updated UI count to: $total")
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Error checking pending sync count", e)
                }
                
                // Check every 2 seconds for more responsive UI
                delay(2000)
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
            android.util.Log.d("HomeViewModel", "Button pressed: ${action.name}, floor: $floorIndex")
            
            // Check if user is online
            if (!_isOnline.value) {
                android.util.Log.w("HomeViewModel", "Button press ignored - user is OFFLINE")
                _errorMessage.value = "Please go ONLINE to record data"
                return@launch
            }
            
            // Check if this action requires floor input
            if (ButtonAction.requiresFloorInput(action) && floorIndex == null) {
                android.util.Log.d("HomeViewModel", "Action requires floor input, showing dialog")
                _pendingAction.value = action
                _showFloorDialog.value = true
                return@launch
            }
            
            _isLoading.value = true
            _errorMessage.value = null
            
            // Update last activity timestamp
            preferencesManager.updateLastActivity()
            
            try {
                // Start session if this is the first button press (LEFT_RESTAURANT_BUILDING is now the first button)
                val sessionId = if (_activeSession.value == null && 
                    action == ButtonAction.LEFT_RESTAURANT_BUILDING) {
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
                android.util.Log.d("HomeViewModel", "Recording button press: ${action.name} for session: $sessionId, floor: $floorIndex")
                val result = sessionRepository.recordButtonPress(sessionId, action, floorIndex)
                
                if (result.isFailure) {
                    android.util.Log.e("HomeViewModel", "Failed to record button press", result.exceptionOrNull())
                    _errorMessage.value = result.exceptionOrNull()?.message
                } else {
                    android.util.Log.d("HomeViewModel", "Button press recorded successfully")
                }
                
                // Control IMU data capture based on button pressed
                controlIMUDataCapture(action)
                
                // Close session if this is the last button
                if (action == ButtonAction.LEFT_DELIVERY_BUILDING) {
                    android.util.Log.d("HomeViewModel", "Closing session: $sessionId")
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
                    _availableActions.value = listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)  // Reset to first button
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
    
    /**
     * Control IMU data capture based on which button was pressed
     */
    private fun controlIMUDataCapture(action: ButtonAction) {
        val intent = Intent(context, com.ips.dataacquisition.service.IMUDataService::class.java)
        
        when (action) {
            ButtonAction.LEFT_RESTAURANT_BUILDING -> {
                // Start 2-minute timed capture
                android.util.Log.d("HomeViewModel", "📊 Starting 2-min timed capture (LEFT_RESTAURANT_BUILDING)")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_TIMED
                intent.putExtra(com.ips.dataacquisition.service.IMUDataService.EXTRA_DURATION_MS, 2 * 60 * 1000L)
                context.startService(intent)
            }
            
            ButtonAction.REACHED_SOCIETY_GATE -> {
                // Start continuous capture
                android.util.Log.d("HomeViewModel", "📊 Starting CONTINUOUS capture (REACHED_SOCIETY_GATE)")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_CONTINUOUS
                context.startService(intent)
            }
            
            ButtonAction.LEFT_DELIVERY_BUILDING -> {
                // Start 3-minute timed capture (will auto-stop after 3 mins)
                android.util.Log.d("HomeViewModel", "📊 Starting 3-min timed capture (LEFT_DELIVERY_BUILDING)")
                intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_START_CAPTURE_TIMED
                intent.putExtra(com.ips.dataacquisition.service.IMUDataService.EXTRA_DURATION_MS, 3 * 60 * 1000L)
                context.startService(intent)
            }
            
            else -> {
                // Other buttons don't affect data capture
                android.util.Log.d("HomeViewModel", "Button ${action.name} - no capture control action")
            }
        }
    }
    
    fun cancelSession() {
        viewModelScope.launch {
            try {
                val currentSession = _activeSession.value
                if (currentSession != null) {
                    _isLoading.value = true
                    android.util.Log.d("HomeViewModel", "Cancelling session: ${currentSession.sessionId}")
                    
                    val result = sessionRepository.cancelSession(currentSession.sessionId)
                    
                    if (result.isSuccess) {
                        android.util.Log.d("HomeViewModel", "Session cancelled successfully")
                        
                        // Stop IMU data capture
                        android.util.Log.d("HomeViewModel", "📊 Stopping data capture (session cancelled)")
                        val intent = Intent(context, com.ips.dataacquisition.service.IMUDataService::class.java)
                        intent.action = com.ips.dataacquisition.service.IMUDataService.ACTION_STOP_CAPTURE
                        context.startService(intent)
                        
                        // Reset state
                        buttonPressFlowJob?.cancel()
                        _activeSession.value = null
                        _buttonPresses.value = emptyList()
                        hasEnteredDeliveryBuilding = false
                        _availableActions.value = listOf(ButtonAction.LEFT_RESTAURANT_BUILDING)  // Reset to first button
                        
                        _errorMessage.value = "Session cancelled successfully"
                    } else {
                        android.util.Log.e("HomeViewModel", "Failed to cancel session", result.exceptionOrNull())
                        _errorMessage.value = "Failed to cancel session: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    _errorMessage.value = "No active session to cancel"
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error cancelling session", e)
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
