package com.ips.dataacquisition.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.data.remote.RetrofitClient
import com.ips.dataacquisition.data.repository.IMURepository
import com.ips.dataacquisition.data.repository.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DataSyncService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    private lateinit var sessionRepository: SessionRepository
    private lateinit var imuRepository: IMURepository
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var preferencesManager: PreferencesManager
    
    private var isNetworkAvailable = false
    private var consecutiveFailures = 0
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_sync_channel"
        private const val BASE_SYNC_INTERVAL_MS = 3000L // 3 seconds when online (100 Hz × 3s = 300 records, send up to 1000)
        private const val MAX_SYNC_INTERVAL_MS = 300000L // 5 minutes max when offline
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            android.util.Log.d("DataSyncService", "✓ Network became available!")
            isNetworkAvailable = true
            consecutiveFailures = 0
            // Trigger immediate sync when network becomes available
            serviceScope.launch {
                android.util.Log.d("DataSyncService", "Triggering immediate sync after network return")
                syncData()
            }
        }
        
        override fun onLost(network: Network) {
            android.util.Log.d("DataSyncService", "✗ Network lost")
            isNetworkAvailable = false
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("DataSyncService", "========================================")
        android.util.Log.d("DataSyncService", "DataSyncService STARTED")
        android.util.Log.d("DataSyncService", "========================================")
        
        val database = AppDatabase.getDatabase(applicationContext)
        sessionRepository = SessionRepository(
            database.sessionDao(),
            database.buttonPressDao(),
            RetrofitClient.apiService
        )
        imuRepository = IMURepository(
            database.imuDataDao(),
            RetrofitClient.apiService
        )
        
        preferencesManager = PreferencesManager(applicationContext)
        
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Register network callback
        registerNetworkCallback()
        
        // Check initial network state
        isNetworkAvailable = checkNetworkAvailability()
        android.util.Log.d("DataSyncService", "Initial network state: $isNetworkAvailable")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        
        startSyncLoop()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("DataSyncService", "onStartCommand called with flags=$flags, startId=$startId")
        return START_STICKY
    }
    
    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        android.util.Log.w("DataSyncService", "Task removed - app minimized/closed")
        // Service should continue running
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.w("DataSyncService", "Service being destroyed!")
        connectivityManager.unregisterNetworkCallback(networkCallback)
        syncJob?.cancel()
        serviceScope.cancel()
    }
    
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    private fun checkNetworkAvailability(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            android.util.Log.d("DataSyncService", "Sync loop started")
            while (isActive) {
                // Calculate dynamic interval based on network and failure count
                val interval = calculateSyncInterval()
                android.util.Log.d("DataSyncService", "Next sync in ${interval/1000} seconds. Network: $isNetworkAvailable, Failures: $consecutiveFailures")
                
                delay(interval)
                
                // Only attempt sync if network is available
                if (isNetworkAvailable) {
                    android.util.Log.d("DataSyncService", "Network available, attempting sync...")
                    syncData()
                } else {
                    android.util.Log.d("DataSyncService", "Network unavailable, skipping sync")
                    updateNotification("Waiting for network...")
                }
            }
        }
    }
    
    private fun calculateSyncInterval(): Long {
        return if (isNetworkAvailable && consecutiveFailures == 0) {
            // Normal interval when everything is working
            BASE_SYNC_INTERVAL_MS
        } else {
            // Exponential backoff when failing or offline
            val backoffMultiplier = kotlin.math.min(consecutiveFailures, 10)
            val interval = BASE_SYNC_INTERVAL_MS * (1 shl backoffMultiplier) // 2^n exponential backoff
            kotlin.math.min(interval, MAX_SYNC_INTERVAL_MS)
        }
    }
    
    private suspend fun syncData() {
        try {
            android.util.Log.d("DataSyncService", "Starting sync cycle. Network available: $isNetworkAvailable")
            updateNotification("Syncing...")
            
            var syncSuccess = true
            
            // Sync session data (button presses and sessions)
            android.util.Log.d("DataSyncService", "Syncing session data...")
            val sessionSyncSuccess = sessionRepository.syncUnsyncedData()
            if (!sessionSyncSuccess) {
                android.util.Log.w("DataSyncService", "Session sync failed")
                syncSuccess = false
            } else {
                android.util.Log.d("DataSyncService", "Session sync successful")
            }
            
            // Sync IMU data (if session sync succeeded or independently)
            android.util.Log.d("DataSyncService", "Syncing IMU data...")
            val imuSyncSuccess = imuRepository.syncIMUData()
            if (!imuSyncSuccess) {
                android.util.Log.w("DataSyncService", "IMU sync failed")
                syncSuccess = false
            } else {
                android.util.Log.d("DataSyncService", "IMU sync successful")
            }
            
            if (syncSuccess) {
                consecutiveFailures = 0
                val pendingIMU = imuRepository.getUnsyncedCount()
                val pendingButtonPresses = sessionRepository.getUnsyncedButtonPressCount()
                val totalPending = pendingIMU + pendingButtonPresses
                
                val message = if (totalPending > 0) {
                    "Synced. Pending: $pendingButtonPresses button(s), $pendingIMU IMU"
                } else {
                    "All data synced ✓"
                }
                android.util.Log.d("DataSyncService", message)
                updateNotification(message)
                
                // Check if user is offline AND no pending records - if so, stop service
                val isOnline = preferencesManager.isOnline.first()
                if (!isOnline && totalPending == 0) {
                    android.util.Log.d("DataSyncService", "========================================")
                    android.util.Log.d("DataSyncService", "User is OFFLINE and ALL records synced")
                    android.util.Log.d("DataSyncService", "Stopping DataSyncService gracefully")
                    android.util.Log.d("DataSyncService", "========================================")
                    updateNotification("All data synced. Service stopping...")
                    delay(2000) // Show notification briefly
                    stopSelf()
                }
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    // Too many failures, might be server issue
                    android.util.Log.e("DataSyncService", "Too many consecutive failures ($consecutiveFailures)")
                    updateNotification("Sync paused (server issue)")
                } else {
                    val pendingCount = imuRepository.getUnsyncedCount()
                    android.util.Log.w("DataSyncService", "Sync failed (attempt $consecutiveFailures). Pending: $pendingCount")
                    updateNotification("Sync failed. Pending: $pendingCount. Retrying...")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DataSyncService", "Sync error", e)
            e.printStackTrace()
            consecutiveFailures++
            updateNotification("Sync error. Retrying...")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Syncing data with server"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Data Sync")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
}
