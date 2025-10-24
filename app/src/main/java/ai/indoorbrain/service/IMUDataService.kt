package ai.indoorbrain.service

import ai.indoorbrain.R
import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import ai.indoorbrain.data.local.AppDatabase
import ai.indoorbrain.data.local.PreferencesManager
import ai.indoorbrain.data.model.IMUData
import ai.indoorbrain.data.remote.RetrofitClientFactory
import ai.indoorbrain.data.repository.IMURepository
import ai.indoorbrain.data.repository.SessionRepository
import ai.indoorbrain.util.CloudLogger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.sentry.SentryLevel
import kotlinx.coroutines.*

class IMUDataService : Service(), SensorEventListener {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sessionRepository: SessionRepository
    private lateinit var imuRepository: IMURepository
    private lateinit var preferencesManager: PreferencesManager
    
    // All sensor references
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravity: Sensor? = null
    private var linearAcceleration: Sensor? = null
    private var accelerometerUncalibrated: Sensor? = null
    private var gyroscopeUncalibrated: Sensor? = null
    private var magnetometerUncalibrated: Sensor? = null
    private var rotationVector: Sensor? = null
    private var gameRotationVector: Sensor? = null
    private var geomagneticRotationVector: Sensor? = null
    private var pressure: Sensor? = null
    private var temperature: Sensor? = null
    private var light: Sensor? = null
    private var humidity: Sensor? = null
    private var proximity: Sensor? = null
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null
    
    // Current sensor data
    private val sensorDataBuffer = mutableListOf<SensorDataPoint>()
    private var currentAccel = FloatArray(3) { 0f }
    private var currentGyro = FloatArray(3) { 0f }
    private var currentMag = FloatArray(3) { 0f }
    private var currentGravity = FloatArray(3) { 0f }
    private var currentLinearAccel = FloatArray(3) { 0f }
    private var currentAccelUncal = FloatArray(6) { 0f } // 3 values + 3 bias
    private var currentGyroUncal = FloatArray(6) { 0f } // 3 values + 3 drift
    private var currentMagUncal = FloatArray(6) { 0f } // 3 values + 3 bias
    private var currentRotationVector = FloatArray(4) { 0f }
    private var currentGameRotation = FloatArray(4) { 0f }
    private var currentGeomagRotation = FloatArray(4) { 0f }
    private var currentPressure: Float? = null
    private var currentTemperature: Float? = null
    private var currentLight: Float? = null
    private var currentHumidity: Float? = null
    private var currentProximity: Float? = null
    private var currentStepCount: Int? = null
    private var stepDetectedThisSecond = false
    
    private var currentLocation: Location? = null
    
    // Speed tracking for hybrid GPS optimization
    private var isCollectingData = false  // Controls whether data is being captured
    private var captureTimerJob: Job? = null  // Timer for auto-stop after duration
    private var captureMode: CaptureMode = CaptureMode.STOPPED
    
    // Fixed 100 Hz sampling using count-based downsampling
    // Hardware runs at ~123 Hz, we want 100 Hz → capture 100/123 = ~81% of events
    // Capture pattern: 5 out of every 6 events (83.3%) ≈ 102.5 Hz
    private var captureCounter = 0
    private val CAPTURE_PATTERN = 6  // Skip every 6th event to downsample from 123 Hz → ~102 Hz
    
    private var currentSessionId: String? = null
    
    private enum class CaptureMode {
        STOPPED,           // Not capturing
        TIMED,            // Capturing for a fixed duration
        CONTINUOUS        // Capturing until explicitly stopped
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "imu_data_channel"
        private const val BATCH_DURATION_MS = 3000L // 3 seconds (100 Hz × 3s = 300 records per batch)
        
        // Time-based capture durations
        private const val CAPTURE_DURATION_AFTER_LEFT_RESTAURANT = 2 * 60 * 1000L // 2 minutes
        private const val CAPTURE_DURATION_AFTER_LEFT_DELIVERY = 3 * 60 * 1000L // 3 minutes
        
        // Intent actions for controlling data capture
        const val ACTION_START_CAPTURE_TIMED = "com.ips.dataacquisition.START_CAPTURE_TIMED"
        const val ACTION_START_CAPTURE_CONTINUOUS = "com.ips.dataacquisition.START_CAPTURE_CONTINUOUS"
        const val ACTION_STOP_CAPTURE = "com.ips.dataacquisition.STOP_CAPTURE"
        const val EXTRA_DURATION_MS = "duration_ms"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
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
        preferencesManager = PreferencesManager(applicationContext)
        
        setupSensors()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startBatchProcessing()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Update session ID if provided
        intent?.getStringExtra("session_id")?.let {
            currentSessionId = it
        }
        
        // Handle control actions
        when (intent?.action) {
            ACTION_START_CAPTURE_TIMED -> {
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, CAPTURE_DURATION_AFTER_LEFT_RESTAURANT)
                startTimedCapture(durationMs)
            }
            ACTION_START_CAPTURE_CONTINUOUS -> {
                startContinuousCapture()
            }
            ACTION_STOP_CAPTURE -> {
                stopDataCapture()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Start time-based data capture that automatically stops after a duration
     */
    private fun startTimedCapture(durationMs: Long) {
        Log.d("IMUDataService", "📊 TIMED capture: ${durationMs / 60000} min")
        
        captureTimerJob?.cancel()
        startAllSensors()
        isCollectingData = true
        captureMode = CaptureMode.TIMED
        
        // Critical diagnostic for Sentry
        if (accelerometer == null) {
            serviceScope.launch {
                val userId = preferencesManager.getUserIdForLogging()
                CloudLogger.captureEvent("[$userId] IMU_DATA_CAPTURE_FAILED: Accelerometer sensor not available", SentryLevel.ERROR)
            }
        }
        
        serviceScope.launch { 
            preferencesManager.setCollectingData(true)
        }
        
        captureTimerJob = serviceScope.launch {
            delay(durationMs)
            Log.d("IMUDataService", "⏱️ Timer expired - stopping")
            stopDataCapture()
        }
        
        updateNotification("Capturing data (${durationMs / 60000} min timer)")
    }
    
    /**
     * Start continuous data capture that runs until explicitly stopped
     */
    private fun startContinuousCapture() {
        Log.d("IMUDataService", "📊 CONTINUOUS capture")
        
        captureTimerJob?.cancel()
        startAllSensors()
        isCollectingData = true
        captureMode = CaptureMode.CONTINUOUS
        
        // Critical diagnostic for Sentry
        if (accelerometer == null) {
            serviceScope.launch {
                val userId = preferencesManager.getUserIdForLogging()
                CloudLogger.captureEvent("[$userId] IMU_DATA_CAPTURE_FAILED: Accelerometer sensor not available", SentryLevel.ERROR)
            }
        }
        
        serviceScope.launch { 
            preferencesManager.setCollectingData(true)
        }
        
        updateNotification("Capturing data (continuous)")
    }
    
    /**
     * Stop data capture and all sensors
     */
    private fun stopDataCapture() {
        Log.d("IMUDataService", "⏹️ STOPPED")
        
        captureTimerJob?.cancel()
        captureTimerJob = null
        isCollectingData = false
        captureMode = CaptureMode.STOPPED
        
        serviceScope.launch { 
            preferencesManager.setCollectingData(false)
        }
        
        stopAllSensors()
        updateNotification("Idle")
    }
    
    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        Log.w("IMUDataService", "Task removed - app minimized/closed. Service continues...")
    }
    
    private fun setupSensors() {
        // Calibrated sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        
        // Uncalibrated sensors
        accelerometerUncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        gyroscopeUncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        magnetometerUncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        
        // Rotation vectors
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        geomagneticRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        
        // Environmental sensors
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        // Log which environmental sensors are available
        Log.d("IMUDataService", "=== ENVIRONMENTAL SENSORS AVAILABLE ===")
        Log.d("IMUDataService", "Pressure: ${if (pressure != null) "✅ ${pressure!!.name}" else "❌ Not available"}")
        Log.d("IMUDataService", "Temperature: ${if (temperature != null) "✅ ${temperature!!.name}" else "❌ Not available"}")
        Log.d("IMUDataService", "Light: ${if (light != null) "✅ ${light!!.name}" else "❌ Not available"}")
        Log.d("IMUDataService", "Humidity: ${if (humidity != null) "✅ ${humidity!!.name}" else "❌ Not available"}")
        Log.d("IMUDataService", "Proximity: ${if (proximity != null) "✅ ${proximity!!.name}" else "❌ Not available"}")
        
        // Activity sensors
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }
    
    private fun startAllSensors() {
        val delay = SensorManager.SENSOR_DELAY_FASTEST
        
        // Start GPS for location data
        startGPSUpdates()
        
        // Motion sensors
        accelerometer?.let { sensorManager.registerListener(this, it, delay) }
        gyroscope?.let { sensorManager.registerListener(this, it, delay) }
        magnetometer?.let { sensorManager.registerListener(this, it, delay) }
        gravity?.let { sensorManager.registerListener(this, it, delay) }
        linearAcceleration?.let { sensorManager.registerListener(this, it, delay) }
        
        accelerometerUncalibrated?.let { sensorManager.registerListener(this, it, delay) }
        gyroscopeUncalibrated?.let { sensorManager.registerListener(this, it, delay) }
        magnetometerUncalibrated?.let { sensorManager.registerListener(this, it, delay) }
        
        rotationVector?.let { sensorManager.registerListener(this, it, delay) }
        gameRotationVector?.let { sensorManager.registerListener(this, it, delay) }
        geomagneticRotationVector?.let { sensorManager.registerListener(this, it, delay) }
        
        pressure?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        temperature?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        light?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        humidity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        
        stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    
    private fun startBatchProcessing() {
        serviceScope.launch {
            while (isActive) {
                delay(BATCH_DURATION_MS)
                processBatch()
                stepDetectedThisSecond = false // Reset step detection flag
            }
        }
    }
    
    // Diagnostic counters for rate monitoring
    private var totalAccelEvents = 0
    private var capturedSnapshots = 0
    private var lastRateLogTime = System.currentTimeMillis()
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        // Dual timestamps for best of both worlds
        val wallClockNanos = System.currentTimeMillis() * 1_000_000  // Unix nanoseconds
        val sensorNanos = event.timestamp  // Hardware sensor nanoseconds (since boot)
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccel = event.values.clone()
                
                // Track total accelerometer events for diagnostics
                totalAccelEvents++
                
                // Simple logic: Capture if collecting data (controlled by GPS)
                if (isCollectingData) {
                    // Fixed ~100 Hz: Skip every 6th event (capture 5 out of 6 = 83.3%)
                    captureCounter++
                    if (captureCounter % CAPTURE_PATTERN != 0) {
                        captureSensorSnapshot(wallClockNanos, sensorNanos)
                        capturedSnapshots++
                    }
                }
                
                // Log summary every 30 seconds
                val now = System.currentTimeMillis()
                if (now - lastRateLogTime >= 30000) {
                    val duration = (now - lastRateLogTime) / 1000.0
                    val captureRate = capturedSnapshots / duration
                    Log.d("IMUDataService", "📊 Collecting: ${captureRate.toInt()} Hz | Mode: $captureMode")
                    
                    // Critical diagnostic for Sentry: No data captured
                    if (isCollectingData && capturedSnapshots == 0) {
                        serviceScope.launch {
                            val userId = preferencesManager.getUserIdForLogging()
                            CloudLogger.captureEvent("[$userId] IMU_DATA_CAPTURE_FAILED: No data captured in 30s despite isCollectingData=true", SentryLevel.ERROR)
                        }
                    }
                    
                    totalAccelEvents = 0
                    capturedSnapshots = 0
                    lastRateLogTime = now
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentGyro = event.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMag = event.values.clone()
            }
            Sensor.TYPE_GRAVITY -> {
                currentGravity = event.values.clone()
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                currentLinearAccel = event.values.clone()
            }
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> {
                currentAccelUncal = event.values.clone()
            }
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                currentGyroUncal = event.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                currentMagUncal = event.values.clone()
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                currentRotationVector = event.values.clone()
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                currentGameRotation = event.values.clone()
            }
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                currentGeomagRotation = event.values.clone()
            }
            Sensor.TYPE_PRESSURE -> {
                currentPressure = event.values[0]
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTemperature = event.values[0]
            }
            Sensor.TYPE_LIGHT -> {
                currentLight = event.values[0]
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                currentHumidity = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                currentProximity = event.values[0]
            }
            Sensor.TYPE_STEP_COUNTER -> {
                currentStepCount = event.values[0].toInt()
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                stepDetectedThisSecond = true
            }
        }
    }
    
    private fun captureSensorSnapshot(wallClockNanos: Long, sensorNanos: Long) {
        // Compute orientation (roll, pitch, yaw) from rotation vector
        val (roll, pitch, yaw) = computeOrientation(currentRotationVector)
        
        // Compute heading from magnetometer and accelerometer
        val heading = computeHeading(currentMag, currentAccel)
        
        synchronized(sensorDataBuffer) {
            sensorDataBuffer.add(
                SensorDataPoint(
                    timestamp = wallClockNanos,
                    timestampNanos = sensorNanos,
                    accel = currentAccel.clone(),
                    gyro = currentGyro.clone(),
                    mag = currentMag.clone(),
                    gravity = currentGravity.clone(),
                    linearAccel = currentLinearAccel.clone(),
                    accelUncal = currentAccelUncal.clone(),
                    gyroUncal = currentGyroUncal.clone(),
                    magUncal = currentMagUncal.clone(),
                    rotationVector = currentRotationVector.clone(),
                    gameRotation = currentGameRotation.clone(),
                    geomagRotation = currentGeomagRotation.clone(),
                    pressure = currentPressure,
                    temperature = currentTemperature,
                    light = currentLight,
                    humidity = currentHumidity,
                    proximity = currentProximity,
                    stepCounter = currentStepCount,
                    stepDetected = stepDetectedThisSecond,
                    roll = roll,
                    pitch = pitch,
                    yaw = yaw,
                    heading = heading,
                    location = currentLocation
                )
            )
        }
    }
    
    private fun computeOrientation(rotVec: FloatArray): Triple<Float?, Float?, Float?> {
        if (rotVec.size < 4 || rotVec.all { it == 0f }) return Triple(null, null, null)
        
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotVec)
        
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        
        // Convert to degrees
        val yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()    // Azimuth
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()  // Pitch
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()   // Roll
        
        return Triple(roll, pitch, yaw)
    }
    
    private fun computeHeading(mag: FloatArray, accel: FloatArray): Float? {
        if (mag.all { it == 0f } || accel.all { it == 0f }) return null
        
        val R = FloatArray(9)
        val I = FloatArray(9)
        
        if (!SensorManager.getRotationMatrix(R, I, accel, mag)) {
            return null
        }
        
        val orientation = FloatArray(3)
        SensorManager.getOrientation(R, orientation)
        
        // Convert azimuth to 0-360 degrees
        var heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (heading < 0) {
            heading += 360f
        }
        
        return heading
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun startGPSUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }
    
    private fun stopGPSUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentLocation = null
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Capture GPS location for IMU data
                currentLocation = location
            }
        }
    }
    
    private suspend fun processBatch() {
        val batch = synchronized(sensorDataBuffer) {
            val copy = sensorDataBuffer.toList()
            sensorDataBuffer.clear()
            copy
        }
        
        if (batch.isEmpty()) {
            return
        }
        
        val sessionId = currentSessionId ?: serviceScope.async {
            sessionRepository.getActiveSession()?.sessionId
        }.await()
        
        val imuDataList = batch.map { dataPoint ->
            IMUData(
                sessionId = sessionId,
                timestamp = dataPoint.timestamp,  // Unix nanoseconds (wall-clock)
                timestampNanos = dataPoint.timestampNanos,  // Sensor nanoseconds (hardware)
                
                // Calibrated sensors
                accelX = dataPoint.accel[0],
                accelY = dataPoint.accel[1],
                accelZ = dataPoint.accel[2],
                gyroX = dataPoint.gyro[0],
                gyroY = dataPoint.gyro[1],
                gyroZ = dataPoint.gyro[2],
                magX = dataPoint.mag[0],
                magY = dataPoint.mag[1],
                magZ = dataPoint.mag[2],
                
                // Gravity and linear acceleration
                gravityX = dataPoint.gravity.getOrNull(0),
                gravityY = dataPoint.gravity.getOrNull(1),
                gravityZ = dataPoint.gravity.getOrNull(2),
                linearAccelX = dataPoint.linearAccel.getOrNull(0),
                linearAccelY = dataPoint.linearAccel.getOrNull(1),
                linearAccelZ = dataPoint.linearAccel.getOrNull(2),
                
                // Uncalibrated sensors
                accelUncalX = dataPoint.accelUncal.getOrNull(0),
                accelUncalY = dataPoint.accelUncal.getOrNull(1),
                accelUncalZ = dataPoint.accelUncal.getOrNull(2),
                accelBiasX = dataPoint.accelUncal.getOrNull(3),
                accelBiasY = dataPoint.accelUncal.getOrNull(4),
                accelBiasZ = dataPoint.accelUncal.getOrNull(5),
                
                gyroUncalX = dataPoint.gyroUncal.getOrNull(0),
                gyroUncalY = dataPoint.gyroUncal.getOrNull(1),
                gyroUncalZ = dataPoint.gyroUncal.getOrNull(2),
                gyroDriftX = dataPoint.gyroUncal.getOrNull(3),
                gyroDriftY = dataPoint.gyroUncal.getOrNull(4),
                gyroDriftZ = dataPoint.gyroUncal.getOrNull(5),
                
                magUncalX = dataPoint.magUncal.getOrNull(0),
                magUncalY = dataPoint.magUncal.getOrNull(1),
                magUncalZ = dataPoint.magUncal.getOrNull(2),
                magBiasX = dataPoint.magUncal.getOrNull(3),
                magBiasY = dataPoint.magUncal.getOrNull(4),
                magBiasZ = dataPoint.magUncal.getOrNull(5),
                
                // Rotation vectors
                rotationVectorX = dataPoint.rotationVector.getOrNull(0),
                rotationVectorY = dataPoint.rotationVector.getOrNull(1),
                rotationVectorZ = dataPoint.rotationVector.getOrNull(2),
                rotationVectorW = dataPoint.rotationVector.getOrNull(3),
                
                gameRotationX = dataPoint.gameRotation.getOrNull(0),
                gameRotationY = dataPoint.gameRotation.getOrNull(1),
                gameRotationZ = dataPoint.gameRotation.getOrNull(2),
                gameRotationW = dataPoint.gameRotation.getOrNull(3),
                
                geomagRotationX = dataPoint.geomagRotation.getOrNull(0),
                geomagRotationY = dataPoint.geomagRotation.getOrNull(1),
                geomagRotationZ = dataPoint.geomagRotation.getOrNull(2),
                geomagRotationW = dataPoint.geomagRotation.getOrNull(3),
                
                // Environmental
                pressure = dataPoint.pressure,
                temperature = dataPoint.temperature,
                light = dataPoint.light,
                humidity = dataPoint.humidity,
                proximity = dataPoint.proximity,
                
                // Activity
                stepCounter = dataPoint.stepCounter,
                stepDetected = dataPoint.stepDetected,
                
                // Computed orientation
                roll = dataPoint.roll,
                pitch = dataPoint.pitch,
                yaw = dataPoint.yaw,
                heading = dataPoint.heading,
                
                // GPS
                latitude = dataPoint.location?.latitude,
                longitude = dataPoint.location?.longitude,
                altitude = dataPoint.location?.altitude,
                gpsAccuracy = dataPoint.location?.accuracy,
                speed = dataPoint.location?.speed
            )
        }
        
        imuRepository.saveIMUDataBatch(imuDataList)
        
        // Update samples collected counter
        serviceScope.launch {
            preferencesManager.incrementSamplesCollected(imuDataList.size)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IMU Data Collection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Collecting sensor data in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun stopAllSensors() {
        sensorManager.unregisterListener(this)
        stopGPSUpdates()
    }
    
    private fun updateNotification(status: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPS Data Collection")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_sensors)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPS Data Collection")
            .setContentText("Service initialized")
            .setSmallIcon(R.drawable.ic_sensors)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.w("IMUDataService", "Service being destroyed!")
        captureTimerJob?.cancel()
        sensorManager.unregisterListener(this)
        stopGPSUpdates()
        serviceScope.cancel()
    }
    
    private data class SensorDataPoint(
        val timestamp: Long,  // Unix nanoseconds (wall-clock)
        val timestampNanos: Long,  // Sensor nanoseconds (hardware timing)
        val accel: FloatArray,
        val gyro: FloatArray,
        val mag: FloatArray,
        val gravity: FloatArray,
        val linearAccel: FloatArray,
        val accelUncal: FloatArray,
        val gyroUncal: FloatArray,
        val magUncal: FloatArray,
        val rotationVector: FloatArray,
        val gameRotation: FloatArray,
        val geomagRotation: FloatArray,
        val pressure: Float?,
        val temperature: Float?,
        val light: Float?,
        val humidity: Float?,
        val proximity: Float?,
        val stepCounter: Int?,
        val stepDetected: Boolean?,
        val roll: Float?,
        val pitch: Float?,
        val yaw: Float?,
        val heading: Float?,
        val location: Location?
    )
}
