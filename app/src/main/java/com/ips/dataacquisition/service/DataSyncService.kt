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
import com.ips.dataacquisition.data.remote.RetrofitClientFactory
import com.ips.dataacquisition.data.repository.IMURepository
import com.ips.dataacquisition.data.repository.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.ips.dataacquisition.util.CloudLogger
import io.sentry.SentryLevel

class DataSyncService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var monitorJob: Job? = null
    private var sentryMonitorJob: Job? = null
    private var versionCheckJob: Job? = null
    
    private lateinit var sessionRepository: SessionRepository
    private lateinit var imuRepository: IMURepository
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var appRepository: com.ips.dataacquisition.data.repository.AppRepository
    
    private var isNetworkAvailable = false
    private var consecutiveFailures = 0
    private var lastNotificationMessage = ""
    private var userIdPrefix = ""
    
    // For Sentry statistics tracking
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_sync_channel"
        private const val BASE_SYNC_INTERVAL_MS = 3000L // 3 seconds when online (mutex prevents race conditions)
        private const val MAX_SYNC_INTERVAL_MS = 300000L // 5 minutes max when offline
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val VERSION_CHECK_INTERVAL_MS = 1 * 60 * 1000L // Check every 30 minutes
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            android.util.Log.d("DataSyncService", "$userIdPrefix ✓ Network available")
            isNetworkAvailable = true
            consecutiveFailures = 0
            // Don't trigger immediate sync - let regular loop handle it
            // This prevents race conditions with duplicate requests
        }
        
        override fun onLost(network: Network) {
            android.util.Log.d("DataSyncService", "$userIdPrefix ✗ Network lost")
            isNetworkAvailable = false
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        val database = AppDatabase.getDatabase(applicationContext)
        sessionRepository = SessionRepository(
            database.sessionDao(),
            database.buttonPressDao(),
            RetrofitClientFactory.apiService,
            applicationContext
        )
        imuRepository = IMURepository(
            database.imuDataDao(),
            RetrofitClientFactory.apiService,
            applicationContext
        )
        appRepository = com.ips.dataacquisition.data.repository.AppRepository(
            RetrofitClientFactory.apiService
        )
        
        preferencesManager = PreferencesManager(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        registerNetworkCallback()
        isNetworkAvailable = checkNetworkAvailability()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        
        // Initialize userId prefix for logging
        serviceScope.launch {
            userIdPrefix = "[${preferencesManager.getUserIdForLogging()}] "
        }
        
        startSyncLoop()
        startPendingRecordsMonitor()
        startSentryStatisticsMonitor()
        startUserActivityMonitor()
        startSessionTimeoutMonitor()
        startVersionCheckMonitor()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        syncJob?.cancel()
        monitorJob?.cancel()
        sentryMonitorJob?.cancel()
        versionCheckJob?.cancel()
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
            while (isActive) {
                val interval = calculateSyncInterval()
                delay(interval)
                
                if (isNetworkAvailable) {
                    syncData()
                } else {
                    updateNotification("Waiting for network...")
                }
            }
        }
    }
    
    private fun startPendingRecordsMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(30_000) // Check every 30 seconds
                
                try {
                    val pendingIMU = imuRepository.getUnsyncedCount()
                    val pendingButtons = sessionRepository.getUnsyncedButtonPressCount()
                    val total = pendingIMU + pendingButtons
                    
                    if (total > 0) {
                        android.util.Log.d("DataSyncService", "$userIdPrefix 📊 Pending: $pendingButtons buttons + $pendingIMU IMU = $total total")
                    }
                } catch (e: Exception) {
                    // Ignore errors in monitoring
                }
            }
        }
    }
    
    private fun startSentryStatisticsMonitor() {
        sentryMonitorJob?.cancel()
        sentryMonitorJob = serviceScope.launch {
            // Wait a bit for initialization
            delay(10_000)
            
            while (isActive) {
                try {
                    // Only report when user is online
                    val isOnline = preferencesManager.isOnline.first()
                    
                    if (isOnline) {
                        reportStatisticsToSentry()
                    }
                } catch (e: Exception) {
                    // Ignore errors in monitoring
                }
                
                // Report every 1 hour
                delay(3_600_000)
            }
        }
    }
    
    private fun startUserActivityMonitor() {
        serviceScope.launch {
            while (isActive) {
                delay(600_000) // Check every 10 minutes
                
                try {
                    val isOnline = preferencesManager.isOnline.first()
                    if (isOnline) {
                        val lastSessionTime = preferencesManager.getLastSessionTime()
                        val currentTime = System.currentTimeMillis()
                        val hoursSinceLastSession = (currentTime - lastSessionTime) / (1000 * 60 * 60)
                        
                        if (hoursSinceLastSession >= 2) {
                            android.util.Log.d("DataSyncService", "🕐 Auto-offline: No session activity for 2+ hours")
                            preferencesManager.setOnline(false)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors in monitoring
                }
            }
        }
    }
    
    private fun startSessionTimeoutMonitor() {
        serviceScope.launch {
            while (isActive) {
                delay(60_000) // Check every 1 minute
                
                try {
                    // Check if there's an active session
                    val activeSession = sessionRepository.getActiveSession()
                    if (activeSession != null) {
                        // Validate session timeout
                        val validation = sessionRepository.validateSessionTimeout(activeSession.sessionId)
                        
                        // If timeout (not just warning), auto-cancel the session
                        if (validation is SessionRepository.TimeoutValidation.Timeout) {
                            android.util.Log.w("DataSyncService", "$userIdPrefix ⚠️ Auto-cancelling timed out session: ${activeSession.sessionId}")
                            
                            // Cancel the session
                            sessionRepository.cancelSession(activeSession.sessionId)
                            
                            // Stop IMU data capture
                            val intent = Intent(this@DataSyncService, IMUDataService::class.java)
                            intent.action = IMUDataService.ACTION_STOP_CAPTURE
                            startService(intent)
                            
                            android.util.Log.d("DataSyncService", "$userIdPrefix ✓ Session auto-cancelled due to ${validation.reason}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataSyncService", "$userIdPrefix ❌ Error in timeout monitor: ${e.message}")
                }
            }
        }
    }
    
    private fun stopAllServices() {
        try {
            android.util.Log.d("DataSyncService", "Stopping all services due to unsupported version")
            
            // Cancel all ongoing sync jobs gracefully
            syncJob?.cancel()
            monitorJob?.cancel()
            sentryMonitorJob?.cancel()
            versionCheckJob?.cancel()
            
            // Stop IMU Data Service
            val imuIntent = Intent(applicationContext, IMUDataService::class.java)
            applicationContext.stopService(imuIntent)
            
            // Note: DataSyncService will stop itself after this method returns
        } catch (e: Exception) {
            android.util.Log.e("DataSyncService", "Error stopping services: ${e.message}")
        }
    }
    
    private fun startVersionCheckMonitor() {
        versionCheckJob?.cancel()
        versionCheckJob = serviceScope.launch {
            while (isActive) {
                delay(VERSION_CHECK_INTERVAL_MS)
                
                android.util.Log.d("DataSyncService", "⏰ Periodic version check (every 30 min)")
                
                try {
                    val versionName = applicationContext.packageManager
                        .getPackageInfo(applicationContext.packageName, 0).versionName
                    
                    val result = appRepository.checkAppVersion(versionName)
                    
                    if (result.isSuccess) {
                        val isSupported = result.getOrNull() ?: true
                        if (!isSupported) {
                            android.util.Log.w("DataSyncService", "⚠️ App version $versionName is no longer supported!")
                            
                            // Send explicit broadcast to MainActivity
                            val intent = Intent("com.ips.dataacquisition.VERSION_UNSUPPORTED").apply {
                                setPackage(applicationContext.packageName) // Explicit package
                            }
                            applicationContext.sendBroadcast(intent)
                            
                            android.util.Log.d("DataSyncService", "📢 Broadcast sent, waiting before stopping services...")
                            
                            // Small delay to ensure broadcast is delivered before stopping services
                            delay(1000)
                            
                            // Stop all services since version is not supported
                            stopAllServices()
                            
                            // Stop this service
                            stopSelf()
                        }
                    }
                } catch (e: Exception) {
                    // Silently fail - don't interrupt service
                    android.util.Log.e("DataSyncService", "Periodic version check failed: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun reportStatisticsToSentry() {
        try {
            // Get userId for the message
            val userId = preferencesManager.getUserIdForLogging()
            
            // Get pending (unsynced) counts - this is what we really care about
            val pendingIMU = imuRepository.getUnsyncedCount()
            val pendingButtons = sessionRepository.getUnsyncedButtonPressCount()
            
            // Get database counts (includes both synced and unsynced data)
            val database = AppDatabase.getDatabase(applicationContext)
            val totalButtonPresses = database.buttonPressDao().getTotalCount()
            
            // For IMU: Use current session samples + any pending from previous sessions
            val currentSessionSamples = preferencesManager.samplesCollected.first()
            
            // Total IMU = current session + pending from previous sessions
            val imuCapturedTotal = currentSessionSamples + pendingIMU
            val buttonsCapturedTotal = totalButtonPresses
            
            // Create full summary message
            val summary = "[$userId] 1-HOUR STATS: Captured: $imuCapturedTotal IMU + $buttonsCapturedTotal buttons | Pending: $pendingIMU IMU + $pendingButtons buttons"
            
            // Log locally
            android.util.Log.i("DataSyncService", summary)
            
            // Send to Sentry as standalone event
            CloudLogger.captureEvent(summary, SentryLevel.INFO)
            
        } catch (e: Exception) {
            android.util.Log.e("DataSyncService", "$userIdPrefix ❌ Sentry stats error: ${e.message}")
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
            updateNotification("Syncing...")
            
            var syncSuccess = true
            
            // Sync session data (button presses and sessions)
            val sessionSyncSuccess = sessionRepository.syncUnsyncedData()
            if (!sessionSyncSuccess) {
                syncSuccess = false
            }
            
            // Sync IMU data
            val imuSyncSuccess = imuRepository.syncIMUData()
            if (!imuSyncSuccess) {
                syncSuccess = false
            }
            
            if (syncSuccess) {
                consecutiveFailures = 0
                val pendingIMU = imuRepository.getUnsyncedCount()
                val pendingButtonPresses = sessionRepository.getUnsyncedButtonPressCount()
                val totalPending = pendingIMU + pendingButtonPresses
                
                val message = if (totalPending > 0) {
                    "Pending: $totalPending"
                } else {
                    "All synced"
                }
                updateNotification(message)
                
                // Check if user is offline AND no pending records
                val isOnline = preferencesManager.isOnline.first()
                if (!isOnline && totalPending == 0) {
                    updateNotification("Stopping...")
                    delay(2000)
                    stopSelf()
                }
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    android.util.Log.e("DataSyncService", "$userIdPrefix ❌ Too many failures ($consecutiveFailures)")
                    updateNotification("Sync paused")
                } else {
                    android.util.Log.w("DataSyncService", "$userIdPrefix ❌ Failed ($consecutiveFailures)")
                    updateNotification("Retrying...")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DataSyncService", "$userIdPrefix ❌ ${e.message}")
            consecutiveFailures++
            updateNotification("Error, retrying...")
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
        // Only update if message changed to avoid excessive system notifications
        if (message != lastNotificationMessage) {
            lastNotificationMessage = message
            val notification = createNotification(message)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
}
