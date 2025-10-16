package com.ips.dataacquisition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ips.dataacquisition.service.DataSyncService
import com.ips.dataacquisition.service.IMUDataService
import com.ips.dataacquisition.ui.screen.BonusScreen
import com.ips.dataacquisition.ui.screen.HomeScreen
import com.ips.dataacquisition.ui.screen.PaymentStatusScreen
import com.ips.dataacquisition.ui.theme.IPSDataAcquisitionTheme
import com.ips.dataacquisition.ui.viewmodel.BonusViewModel
import com.ips.dataacquisition.ui.viewmodel.HomeViewModel
import com.ips.dataacquisition.ui.viewmodel.PaymentStatusViewModel
import com.ips.dataacquisition.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var paymentViewModel: PaymentStatusViewModel
    private lateinit var bonusViewModel: BonusViewModel
    
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
        
        val factory = ViewModelFactory(applicationContext)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        paymentViewModel = ViewModelProvider(this, factory)[PaymentStatusViewModel::class.java]
        bonusViewModel = ViewModelProvider(this, factory)[BonusViewModel::class.java]
        
        android.util.Log.d("MainActivity", "ViewModels created, requesting permissions...")
        
        setContent {
            IPSDataAcquisitionTheme {
                MainScreen(
                    homeViewModel = homeViewModel,
                    paymentViewModel = paymentViewModel,
                    bonusViewModel = bonusViewModel
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    paymentViewModel: PaymentStatusViewModel,
    bonusViewModel: BonusViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Payment, contentDescription = "Payment Status") },
                    label = { Text("Payment") },
                    selected = currentRoute == "payment",
                    onClick = {
                        navController.navigate("payment") {
                            popUpTo("home")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Bonus") },
                    label = { Text("Bonus") },
                    selected = currentRoute == "bonus",
                    onClick = {
                        navController.navigate("bonus") {
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
                val errorMessage by homeViewModel.errorMessage.collectAsStateWithLifecycle()
                
                HomeScreen(
                    activeSession = activeSession,
                    buttonPresses = buttonPresses,
                    availableActions = availableActions,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onButtonPress = { action -> homeViewModel.onButtonPress(action) },
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
        }
    }
}

