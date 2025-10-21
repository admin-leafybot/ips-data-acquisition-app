package com.ips.dataacquisition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.service.DataSyncService
import com.ips.dataacquisition.service.IMUDataService
import kotlinx.coroutines.launch
import com.ips.dataacquisition.ui.screen.BonusScreen
import com.ips.dataacquisition.ui.screen.HomeScreen
import com.ips.dataacquisition.ui.screen.PaymentStatusScreen
import com.ips.dataacquisition.ui.screen.SettingsScreen
import com.ips.dataacquisition.ui.theme.IPSDataAcquisitionTheme
import com.ips.dataacquisition.ui.viewmodel.BonusViewModel
import com.ips.dataacquisition.ui.viewmodel.HomeViewModel
import com.ips.dataacquisition.ui.viewmodel.PaymentStatusViewModel
import com.ips.dataacquisition.ui.viewmodel.SettingsViewModel
import com.ips.dataacquisition.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var paymentViewModel: PaymentStatusViewModel
    private lateinit var bonusViewModel: BonusViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var preferencesManager: PreferencesManager
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("MainActivity", "Permission result received")
        permissions.forEach { (permission, granted) ->
            android.util.Log.d("MainActivity", "  $permission: $granted")
        }
        
        // Start services after permissions (granted or not)
        // Services will check permissions internally
        android.util.Log.d("MainActivity", "Starting services after permission request...")
        startServices()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d("MainActivity", "==========================================")
        android.util.Log.d("MainActivity", "MainActivity onCreate")
        android.util.Log.d("MainActivity", "==========================================")
        
        preferencesManager = PreferencesManager(applicationContext)
        
        val factory = ViewModelFactory(applicationContext)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        paymentViewModel = ViewModelProvider(this, factory)[PaymentStatusViewModel::class.java]
        bonusViewModel = ViewModelProvider(this, factory)[BonusViewModel::class.java]
        settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]
        
        android.util.Log.d("MainActivity", "ViewModels created, requesting permissions...")
        
        // Observe online state to control services
        observeOnlineStateForServices()
        
        // Observe language changes to recreate activity
        observeLanguageChanges()
        
        setContent {
            IPSDataAcquisitionTheme {
                MainScreen(
                    homeViewModel = homeViewModel,
                    paymentViewModel = paymentViewModel,
                    bonusViewModel = bonusViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
        
        // Request permissions after UI is set up
        requestPermissions()
    }
    
    private fun requestPermissions() {
        // Check if we already have location permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        android.util.Log.d("MainActivity", "Current permission state:")
        android.util.Log.d("MainActivity", "  ACCESS_FINE_LOCATION: $hasFineLocation")
        android.util.Log.d("MainActivity", "  ACCESS_COARSE_LOCATION: $hasCoarseLocation")
        
        // If we already have location permissions, start services immediately
        if (hasFineLocation || hasCoarseLocation) {
            android.util.Log.d("MainActivity", "Location permissions already granted, starting services")
            startServices()
            return
        }
        
        // Request permissions if not granted
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Add ACTIVITY_RECOGNITION permission for step counter/detector (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        android.util.Log.d("MainActivity", "Requesting permissions: $permissions")
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun startServices() {
        android.util.Log.d("MainActivity", "Starting background services...")
        
        // Start IMU Data Collection Service
        Intent(this, IMUDataService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                android.util.Log.d("MainActivity", "Started IMUDataService as foreground service")
            } else {
                startService(intent)
                android.util.Log.d("MainActivity", "Started IMUDataService")
            }
        }
        
        // Start Data Sync Service
        Intent(this, DataSyncService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                android.util.Log.d("MainActivity", "Started DataSyncService as foreground service")
            } else {
                startService(intent)
                android.util.Log.d("MainActivity", "Started DataSyncService")
            }
        }
        
        android.util.Log.d("MainActivity", "All services started successfully")
    }
    
    private fun stopServices() {
        android.util.Log.d("MainActivity", "Stopping background services...")
        
        // Stop IMU Data Collection Service
        Intent(this, IMUDataService::class.java).also { intent ->
            stopService(intent)
            android.util.Log.d("MainActivity", "Stopped IMUDataService")
        }
        
        // Stop Data Sync Service
        Intent(this, DataSyncService::class.java).also { intent ->
            stopService(intent)
            android.util.Log.d("MainActivity", "Stopped DataSyncService")
        }
        
        android.util.Log.d("MainActivity", "All services stopped")
    }
    
    private fun observeOnlineStateForServices() {
        lifecycleScope.launch {
            preferencesManager.isOnline.collect { isOnline ->
                android.util.Log.d("MainActivity", "Online state changed: $isOnline, controlling services...")
                
                if (isOnline) {
                    // User went online - start all services
                    startServices()
                } else {
                    // User went offline - stop IMU data collection but keep sync running until queue is empty
                    android.util.Log.d("MainActivity", "User went offline - stopping IMU collection, keeping sync active for pending records")
                    
                    // Stop IMU Data Collection Service immediately (no new data)
                    Intent(this@MainActivity, IMUDataService::class.java).also { intent ->
                        stopService(intent)
                        android.util.Log.d("MainActivity", "Stopped IMUDataService (user offline)")
                    }
                    
                    // Keep DataSyncService running to flush pending records
                    // It will stop itself when queue is empty (handled in DataSyncService)
                    android.util.Log.d("MainActivity", "DataSyncService will continue until all pending records are synced")
                }
            }
        }
    }
    
    private fun observeLanguageChanges() {
        lifecycleScope.launch {
            var firstRun = true
            settingsViewModel.currentLanguage.collect { languageCode ->
                if (firstRun) {
                    // Skip first emission (initial value)
                    firstRun = false
                    applyLanguage(languageCode)
                } else {
                    // Language changed by user - apply and recreate activity
                    applyLanguage(languageCode)
                    recreate()
                }
            }
        }
    }
    
    private fun applyLanguage(languageCode: String) {
        val locale = when (languageCode) {
            "hi" -> java.util.Locale("hi", "IN")
            else -> java.util.Locale("en", "US")
        }
        java.util.Locale.setDefault(locale)
        
        val config = resources.configuration
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    paymentViewModel: PaymentStatusViewModel,
    bonusViewModel: BonusViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.tab_home)) },
                    label = { Text(stringResource(R.string.tab_home)) },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Payment, contentDescription = stringResource(R.string.tab_payment_status)) },
                    label = { Text(stringResource(R.string.tab_payment_status)) },
                    selected = currentRoute == "payment",
                    onClick = {
                        navController.navigate("payment") {
                            popUpTo("home")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = stringResource(R.string.tab_bonus)) },
                    label = { Text(stringResource(R.string.tab_bonus)) },
                    selected = currentRoute == "bonus",
                    onClick = {
                        navController.navigate("bonus") {
                            popUpTo("home")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings)) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo("home")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                val activeSession by homeViewModel.activeSession.collectAsStateWithLifecycle()
                val buttonPresses by homeViewModel.buttonPresses.collectAsStateWithLifecycle()
                val availableActions by homeViewModel.availableActions.collectAsStateWithLifecycle()
                val isLoading by homeViewModel.isLoading.collectAsStateWithLifecycle()
                val isOnline by homeViewModel.isOnline.collectAsStateWithLifecycle()
                val isCollectingData by homeViewModel.isCollectingData.collectAsStateWithLifecycle()
                val samplesCollected by homeViewModel.samplesCollected.collectAsStateWithLifecycle()
                val pendingSyncCount by homeViewModel.pendingSyncCount.collectAsStateWithLifecycle()
                val errorMessage by homeViewModel.errorMessage.collectAsStateWithLifecycle()
                val showFloorDialog by homeViewModel.showFloorDialog.collectAsStateWithLifecycle()
                val pendingAction by homeViewModel.pendingAction.collectAsStateWithLifecycle()
                val showSuccessMessage by homeViewModel.showSuccessMessage.collectAsStateWithLifecycle()
                
                HomeScreen(
                    activeSession = activeSession,
                    buttonPresses = buttonPresses,
                    availableActions = availableActions,
                    isLoading = isLoading,
                    isOnline = isOnline,
                    isCollectingData = isCollectingData,
                    samplesCollected = samplesCollected,
                    pendingSyncCount = pendingSyncCount,
                    errorMessage = errorMessage,
                    showFloorDialog = showFloorDialog,
                    pendingAction = pendingAction,
                    showSuccessMessage = showSuccessMessage,
                    onButtonPress = { action -> homeViewModel.onButtonPress(action) },
                    onFloorSelected = { floor -> homeViewModel.onFloorSelected(floor) },
                    onDismissFloorDialog = { homeViewModel.dismissFloorDialog() },
                    onToggleOnline = { homeViewModel.toggleOnlineStatus() },
                    onClearError = { homeViewModel.clearError() }
                )
            }
            
            composable("payment") {
                val sessions by paymentViewModel.sessions.collectAsStateWithLifecycle()
                val isLoading by paymentViewModel.isLoading.collectAsStateWithLifecycle()
                val isRefreshing by paymentViewModel.isRefreshing.collectAsStateWithLifecycle()
                
                PaymentStatusScreen(
                    sessions = sessions,
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                    onRefresh = { paymentViewModel.refreshSessions() }
                )
            }
            
            composable("bonus") {
                val bonuses by bonusViewModel.bonuses.collectAsStateWithLifecycle()
                val totalBonus by bonusViewModel.totalBonus.collectAsStateWithLifecycle()
                val isLoading by bonusViewModel.isLoading.collectAsStateWithLifecycle()
                val isRefreshing by bonusViewModel.isRefreshing.collectAsStateWithLifecycle()
                
                BonusScreen(
                    bonuses = bonuses,
                    totalBonus = totalBonus,
                    isLoading = isLoading,
                    //isRefreshing = isRefreshing,
                    //onRefresh = { bonusViewModel.refreshBonuses() }
                )
            }
            
            composable("settings") {
                val currentLanguage by settingsViewModel.currentLanguage.collectAsStateWithLifecycle()
                
                SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageChange = { languageCode ->
                        settingsViewModel.changeLanguage(languageCode)
                    }
                )
            }
        }
    }
}

