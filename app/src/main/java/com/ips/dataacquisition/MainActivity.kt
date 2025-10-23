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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.ips.dataacquisition.ui.screen.LoginScreen
import com.ips.dataacquisition.ui.screen.PaymentStatusScreen
import com.ips.dataacquisition.ui.screen.SettingsScreen
import com.ips.dataacquisition.ui.screen.SignupScreen
import com.ips.dataacquisition.ui.theme.IPSDataAcquisitionTheme
import com.ips.dataacquisition.ui.viewmodel.AppVersionViewModel
import com.ips.dataacquisition.ui.viewmodel.AuthViewModel
import com.ips.dataacquisition.ui.viewmodel.BonusViewModel
import com.ips.dataacquisition.ui.viewmodel.HomeViewModel
import com.ips.dataacquisition.ui.viewmodel.PaymentStatusViewModel
import com.ips.dataacquisition.ui.viewmodel.SettingsViewModel
import com.ips.dataacquisition.ui.viewmodel.ViewModelFactory
import com.ips.dataacquisition.ui.screen.UnsupportedVersionScreen

class MainActivity : ComponentActivity() {
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var paymentViewModel: PaymentStatusViewModel
    private lateinit var bonusViewModel: BonusViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var appVersionViewModel: AppVersionViewModel
    private lateinit var preferencesManager: PreferencesManager
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Start services after permissions (granted or not)
        // Services will check permissions internally
        startServices()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        preferencesManager = PreferencesManager(applicationContext)
        
        val factory = ViewModelFactory(applicationContext)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        paymentViewModel = ViewModelProvider(this, factory)[PaymentStatusViewModel::class.java]
        bonusViewModel = ViewModelProvider(this, factory)[BonusViewModel::class.java]
        settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
        appVersionViewModel = ViewModelProvider(this, factory)[AppVersionViewModel::class.java]
        
        // Check app version before proceeding
        val versionName = getAppVersion()
        appVersionViewModel.checkAppVersion(versionName)
        
        // Observe language changes to recreate activity
        observeLanguageChanges()
        
        setContent {
            IPSDataAcquisitionTheme {
                val isVersionSupported by appVersionViewModel.isVersionSupported.collectAsStateWithLifecycle()
                val isChecking by appVersionViewModel.isChecking.collectAsStateWithLifecycle()
                
                // Initialize services only after version is verified as supported
                LaunchedEffect(isVersionSupported) {
                    if (isVersionSupported == true) {
                        observeOnlineStateForServices()
                        requestPermissions()
                    }
                }
                
                when {
                    // Still checking version
                    isVersionSupported == null || isChecking -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.checking_version))
                                }
                            }
                        }
                    }
                    // Version is not supported
                    isVersionSupported == false -> {
                        UnsupportedVersionScreen()
                    }
                    // Version is supported - show main app
                    else -> {
                        MainScreen(
                            homeViewModel = homeViewModel,
                            paymentViewModel = paymentViewModel,
                            bonusViewModel = bonusViewModel,
                            settingsViewModel = settingsViewModel,
                            authViewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
    
    private fun requestPermissions() {
        // Build list of required permissions
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Add ACTIVITY_RECOGNITION permission for step counter/detector (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        // Check which permissions are missing
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        // If all permissions granted, start services
        if (missingPermissions.isEmpty()) {
            startServices()
            return
        }
        
        // Request missing permissions
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }
    
    private fun startServices() {
        // Start IMU Data Collection Service
        Intent(this, IMUDataService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        // Start Data Sync Service (includes Sentry monitoring)
        // Note: startService() is safe to call multiple times - Android handles it
        Intent(this, DataSyncService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
    
    private fun stopServices() {
        // Stop IMU Data Collection Service
        Intent(this, IMUDataService::class.java).also { intent ->
            stopService(intent)
        }
        
        // Stop Data Sync Service
        Intent(this, DataSyncService::class.java).also { intent ->
            stopService(intent)
        }
    }
    
    private fun observeOnlineStateForServices() {
        lifecycleScope.launch {
            preferencesManager.isOnline.collect { isOnline ->
                if (isOnline) {
                    // User went online - start all services
                    startServices()
                } else {
                    // User went offline - stop IMU data collection
                    Intent(this@MainActivity, IMUDataService::class.java).also { intent ->
                        stopService(intent)
                    }
                    // DataSyncService continues to flush pending records
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
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting app version", e)
            "1.0.0"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    paymentViewModel: PaymentStatusViewModel,
    bonusViewModel: BonusViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Check authentication
    val isAuthenticated by authViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val authLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val authError by authViewModel.errorMessage.collectAsStateWithLifecycle()
    
    // If not authenticated, show login screen
    if (!isAuthenticated) {
        val currentLanguage by settingsViewModel.currentLanguage.collectAsStateWithLifecycle()
        val signupSuccess by authViewModel.signupSuccess.collectAsStateWithLifecycle()
        val signupSuccessMessage by authViewModel.signupSuccessMessage.collectAsStateWithLifecycle()
        
        // Navigate to login after successful signup
        LaunchedEffect(signupSuccess) {
            if (signupSuccess) {
                navController.navigate("login") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
        
        NavHost(
            navController = navController,
            startDestination = "login"
        ) {
            composable("login") {
                // Show signup success message on login screen if just signed up
                val displayMessage = signupSuccessMessage ?: authError
                
                // Clear signup success when showing on login screen
                LaunchedEffect(signupSuccessMessage) {
                    if (signupSuccessMessage != null) {
                        kotlinx.coroutines.delay(100)  // Small delay to ensure message is displayed
                    }
                }
                
                LoginScreen(
                    isLoading = authLoading,
                    errorMessage = displayMessage,
                    currentLanguage = currentLanguage,
                    onLogin = { phone, password -> 
                        authViewModel.clearSignupSuccess()
                        authViewModel.login(phone, password) 
                    },
                    onNavigateToSignup = { navController.navigate("signup") },
                    onLanguageChange = { languageCode -> settingsViewModel.changeLanguage(languageCode) },
                    onClearError = { 
                        authViewModel.clearError()
                        authViewModel.clearSignupSuccess()
                    }
                )
            }
            
            composable("signup") {
                SignupScreen(
                    isLoading = authLoading,
                    errorMessage = authError,
                    currentLanguage = currentLanguage,
                    onSignup = { phone, password, fullName -> authViewModel.signup(phone, password, fullName) },
                    onNavigateBack = { navController.popBackStack() },
                    onLanguageChange = { languageCode -> settingsViewModel.changeLanguage(languageCode) },
                    onClearError = { authViewModel.clearError() }
                )
            }
        }
        return
    }
    
    // Authenticated - show main app
    
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
                // Bonus tab - hidden for now
                // NavigationBarItem(
                //     icon = { Icon(Icons.Default.AttachMoney, contentDescription = stringResource(R.string.tab_bonus)) },
                //     label = { Text(stringResource(R.string.tab_bonus)) },
                //     selected = currentRoute == "bonus",
                //     onClick = {
                //         navController.navigate("bonus") {
                //             popUpTo("home")
                //         }
                //     }
                // )
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
                val timeoutWarning by homeViewModel.showTimeoutWarning.collectAsStateWithLifecycle()
                
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
                    timeoutWarning = timeoutWarning,
                    onButtonPress = { action -> homeViewModel.onButtonPress(action) },
                    onFloorSelected = { floor -> homeViewModel.onFloorSelected(floor) },
                    onDismissFloorDialog = { homeViewModel.dismissFloorDialog() },
                    onToggleOnline = { homeViewModel.toggleOnlineStatus() },
                    onClearError = { homeViewModel.clearError() },
                    onCancelSession = { homeViewModel.cancelSession() },
                    onTimeoutDismiss = { homeViewModel.onTimeoutDismiss() }
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
                val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
                
                SettingsScreen(
                    currentLanguage = currentLanguage,
                    userName = currentUser?.fullName,
                    onLanguageChange = { languageCode ->
                        settingsViewModel.changeLanguage(languageCode)
                    },
                    onLogout = { authViewModel.logout() }
                )
            }
        }
    }
}

