package com.ips.dataacquisition.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.model.IMUData
import com.ips.dataacquisition.data.remote.RetrofitClientFactory
import com.ips.dataacquisition.data.repository.IMURepository
import com.ips.dataacquisition.data.repository.SessionRepository
import kotlinx.coroutines.*
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.asin

class IMUDataService : Service(), SensorEventListener {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sessionRepository: SessionRepository
    private lateinit var imuRepository: IMURepository
    private lateinit var preferencesManager: com.ips.dataacquisition.data.local.PreferencesManager
    
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
    private var isCollectingData = false  // Renamed for clarity
    private var isGPSActive = false
    private var lastGPSUpdateTime = 0L
    private var hasGoodGPSSignal = false
    
    // Fixed 100 Hz sampling using count-based downsampling
    // Hardware runs at ~123 Hz, we want 100 Hz → capture 100/123 = ~81% of events
    // Capture pattern: 5 out of every 6 events (83.3%) ≈ 102.5 Hz
    private var captureCounter = 0
    private val CAPTURE_PATTERN = 6  // Skip every 6th event to downsample from 123 Hz → ~102 Hz
    
    private var currentSessionId: String? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "imu_data_channel"
        private const val BATCH_DURATION_MS = 3000L // 3 seconds (125 Hz × 3s = ~375 records per batch)
        
        // GPS thresholds (for outdoor use - prevents vehicle data collection)
        private const val SPEED_THRESHOLD_START = 2.78f // m/s (10 km/h) - start collecting when BELOW this
        private const val SPEED_THRESHOLD_STOP = 4.17f  // m/s (15 km/h) - stop collecting when ABOVE this
        
        // GPS signal quality detection
        private const val GPS_TIMEOUT_MS = 5000L // 5 seconds - if no GPS update, consider signal lost
        private const val GPS_ACCURACY_THRESHOLD = 50f // meters - only trust GPS speed for vehicle detection if better than this
        // IMPORTANT: GPS location (lat/lon/alt/accuracy/speed) is ALWAYS captured in IMU data regardless of accuracy!
        
        // Indoor mode: Continuous capture when GPS unavailable (no accelerometer threshold)
        // Simpler logic: If GPS is poor/lost → capture everything indoors
    }
    
    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("IMUDataService", "========================================")
        android.util.Log.d("IMUDataService", "IMUDataService STARTED")
        android.util.Log.d("IMUDataService", "========================================")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val database = AppDatabase.getDatabase(applicationContext)
        sessionRepository = SessionRepository(
            database.sessionDao(),
            database.buttonPressDao(),
            RetrofitClientFactory.apiService
        )
        imuRepository = IMURepository(
            database.imuDataDao(),
            RetrofitClientFactory.apiService
        )
        preferencesManager = com.ips.dataacquisition.data.local.PreferencesManager(applicationContext)
        
        setupSensors()
        createNotificationChannel()
        
        android.util.Log.d("IMUDataService", "Starting foreground with notification...")
        startForeground(NOTIFICATION_ID, createNotification())
        
        android.util.Log.d("IMUDataService", "Starting sensor data collection...")
        startDataCollection()
        startBatchProcessing()
        
        android.util.Log.d("IMUDataService", "IMU service fully initialized and running")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("IMUDataService", "onStartCommand called with flags=$flags, startId=$startId")
        intent?.getStringExtra("session_id")?.let {
            currentSessionId = it
            android.util.Log.d("IMUDataService", "Session ID updated: $it")
        }
        return START_STICKY
    }
    
    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        android.util.Log.w("IMUDataService", "Task removed - app minimized/closed. Service continues...")
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
        android.util.Log.d("IMUDataService", "=== ENVIRONMENTAL SENSORS AVAILABLE ===")
        android.util.Log.d("IMUDataService", "Pressure: ${if (pressure != null) "✅ ${pressure!!.name}" else "❌ Not available"}")
        android.util.Log.d("IMUDataService", "Temperature: ${if (temperature != null) "✅ ${temperature!!.name}" else "❌ Not available"}")
        android.util.Log.d("IMUDataService", "Light: ${if (light != null) "✅ ${light!!.name}" else "❌ Not available"}")
        android.util.Log.d("IMUDataService", "Humidity: ${if (humidity != null) "✅ ${humidity!!.name}" else "❌ Not available"}")
        android.util.Log.d("IMUDataService", "Proximity: ${if (proximity != null) "✅ ${proximity!!.name}" else "❌ Not available"}")
        
        // Activity sensors
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }
    
    private fun startDataCollection() {
        // HYBRID APPROACH: Use GPS outdoors, accelerometer indoors
        android.util.Log.d("IMUDataService", "========================================")
        android.util.Log.d("IMUDataService", "Starting HYBRID movement detection:")
        android.util.Log.d("IMUDataService", "  - GPS (outdoor): Prevents vehicle data (>15 km/h)")
        android.util.Log.d("IMUDataService", "  - Accelerometer (indoor): Detects walking when GPS unavailable")
        android.util.Log.d("IMUDataService", "========================================")
        
        // Start GPS monitoring
        startGPSUpdates()
        
        // Start accelerometer for movement detection (always needed for hybrid approach)
        android.util.Log.d("IMUDataService", "Starting accelerometer for indoor movement detection")
        val delay = SensorManager.SENSOR_DELAY_FASTEST
        accelerometer?.let { sensorManager.registerListener(this, it, delay) }
        
        // Start periodic GPS signal quality check
        startGPSSignalMonitoring()
    }
    
    private fun startGPSSignalMonitoring() {
        serviceScope.launch {
            // Initial check: If GPS never started (lastGPSUpdateTime = 0), start collecting immediately
            android.util.Log.d("IMUDataService", "⏱️  GPS initialization check: waiting 1 second...")
            delay(1000) // Give GPS 1 second to initialize
            
            android.util.Log.d("IMUDataService", "⏱️  GPS timeout check: lastGPSUpdateTime = $lastGPSUpdateTime")
            if (lastGPSUpdateTime == 0L) {
                // GPS never provided any updates - assume we're indoors
                android.util.Log.d("IMUDataService", "📡 GPS not available at startup - CONTINUOUS indoor capture")
                android.util.Log.d("IMUDataService", "Current isCollectingData: $isCollectingData")
                if (!isCollectingData) {
                    android.util.Log.d("IMUDataService", "🏢 Indoor mode: Starting CONTINUOUS data collection")
                    isCollectingData = true
                    serviceScope.launch { preferencesManager.setCollectingData(true) }
                    startAllSensors()
                } else {
                    android.util.Log.d("IMUDataService", "Already collecting data, no action needed")
                }
            } else {
                android.util.Log.d("IMUDataService", "📡 GPS is working (last update: ${System.currentTimeMillis() - lastGPSUpdateTime}ms ago)")
            }
            
            // Then continue monitoring for signal changes
            while (isActive) {
                delay(2000) // Check every 2 seconds
                
                val timeSinceLastGPS = System.currentTimeMillis() - lastGPSUpdateTime
                val hadGoodSignal = hasGoodGPSSignal
                hasGoodGPSSignal = timeSinceLastGPS < GPS_TIMEOUT_MS
                
                // Log signal transitions and start continuous collection when GPS lost
                if (hadGoodSignal != hasGoodGPSSignal) {
                    if (hasGoodGPSSignal) {
                        android.util.Log.d("IMUDataService", "📡 GPS signal ACQUIRED - using GPS speed check")
                    } else {
                        android.util.Log.d("IMUDataService", "📡 GPS signal LOST (${timeSinceLastGPS}ms) - CONTINUOUS indoor capture")
                        // GPS lost - start collecting continuously (indoor mode)
                        if (!isCollectingData) {
                            android.util.Log.d("IMUDataService", "🏢 Indoor mode: Starting CONTINUOUS data collection")
                            isCollectingData = true
                            serviceScope.launch { preferencesManager.setCollectingData(true) }
                            startAllSensors()
                        }
                    }
                }
            }
        }
    }
    
    private fun startAllSensors() {
        android.util.Log.d("IMUDataService", "Movement detected - starting ALL 21 sensors")
        val delay = SensorManager.SENSOR_DELAY_FASTEST  // Max rate (~125 Hz on this device)
        
        // Motion sensors (accelerometer already running for movement detection)
        // accelerometer?.let { sensorManager.registerListener(this, it, delay) }  // Already registered
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
    
    private fun stopAllSensors() {
        android.util.Log.d("IMUDataService", "User moving fast (vehicle) - stopping ALL sensors EXCEPT accelerometer")
        
        // Stop ALL sensors EXCEPT accelerometer (need it for hybrid detection)
        // accelerometer: Keep running for indoor movement detection
        // accelerometer?.let { sensorManager.unregisterListener(this, it) }  // DON'T stop
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
        magnetometer?.let { sensorManager.unregisterListener(this, it) }
        gravity?.let { sensorManager.unregisterListener(this, it) }
        linearAcceleration?.let { sensorManager.unregisterListener(this, it) }
        
        accelerometerUncalibrated?.let { sensorManager.unregisterListener(this, it) }
        gyroscopeUncalibrated?.let { sensorManager.unregisterListener(this, it) }
        magnetometerUncalibrated?.let { sensorManager.unregisterListener(this, it) }
        
        rotationVector?.let { sensorManager.unregisterListener(this, it) }
        gameRotationVector?.let { sensorManager.unregisterListener(this, it) }
        geomagneticRotationVector?.let { sensorManager.unregisterListener(this, it) }
        
        pressure?.let { sensorManager.unregisterListener(this, it) }
        temperature?.let { sensorManager.unregisterListener(this, it) }
        light?.let { sensorManager.unregisterListener(this, it) }
        humidity?.let { sensorManager.unregisterListener(this, it) }
        proximity?.let { sensorManager.unregisterListener(this, it) }
        
        stepCounter?.let { sensorManager.unregisterListener(this, it) }
        stepDetector?.let { sensorManager.unregisterListener(this, it) }
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
                
                // Log actual rate every 10 seconds for diagnostics
                val now = System.currentTimeMillis()
                if (now - lastRateLogTime >= 10000) {
                    val duration = (now - lastRateLogTime) / 1000.0
                    val accelRate = totalAccelEvents / duration
                    val captureRate = capturedSnapshots / duration
                    android.util.Log.d("IMUDataService", "=== RATE DIAGNOSTICS ===")
                    android.util.Log.d("IMUDataService", "Accelerometer events: $accelRate Hz (hardware)")
                    android.util.Log.d("IMUDataService", "Captured snapshots: $captureRate Hz (target: 100 Hz)")
                    android.util.Log.d("IMUDataService", "isCollectingData: $isCollectingData | GPS signal: ${if (hasGoodGPSSignal) "GOOD" else "LOST"}")
                    android.util.Log.d("IMUDataService", "Detection mode: ${if (hasGoodGPSSignal) "GPS speed check" else "Continuous indoor"}")
                    android.util.Log.d("IMUDataService", "Downsampling: Skip every ${CAPTURE_PATTERN}th event (capture ${CAPTURE_PATTERN-1}/$CAPTURE_PATTERN = ${((CAPTURE_PATTERN-1)*100.0/CAPTURE_PATTERN).toInt()}%)")
                    
                    // Reset counters
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
        if (isGPSActive) return
        
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
        
        isGPSActive = true
    }
    
    private fun stopGPSUpdates() {
        if (!isGPSActive) return
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentLocation = null
        isGPSActive = false
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // ALWAYS capture GPS location regardless of accuracy
                currentLocation = location
                
                // Record GPS update time for signal quality monitoring
                lastGPSUpdateTime = System.currentTimeMillis()
                hasGoodGPSSignal = true  // Received update, signal is good
                
                val speed = if (location.hasSpeed()) location.speed else 0f
                val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
                val speedKmh = speed * 3.6
                
                android.util.Log.d("IMUDataService", String.format("📡 GPS: lat=%.6f, lon=%.6f, acc=%.0fm, speed=%.1f km/h", 
                    location.latitude, location.longitude, accuracy, speedKmh))
                
                // ONLY use GPS for movement detection when signal is good
                // NOTE: GPS location is ALWAYS captured in IMU data, regardless of accuracy
                if (accuracy < GPS_ACCURACY_THRESHOLD) {
                    // Good GPS signal - use it to prevent vehicle data collection
                    if (!isCollectingData) {
                        // Currently NOT collecting - check if we should START
                        if (speed < SPEED_THRESHOLD_START) {
                            val thresholdKmh = SPEED_THRESHOLD_START * 3.6
                            android.util.Log.d("IMUDataService", String.format("🚗 GPS (outdoor): Speed < %.1f km/h - START data collection", thresholdKmh))
                            isCollectingData = true
                            serviceScope.launch { preferencesManager.setCollectingData(true) }
                            startAllSensors()
                        }
                        else{

                        }
                    } else {
                        // Currently collecting - check if we should STOP
                        if (speed > SPEED_THRESHOLD_STOP) {
                            val thresholdKmh = SPEED_THRESHOLD_STOP * 3.6
                            android.util.Log.d("IMUDataService", String.format("🚗 GPS (outdoor): Speed > %.1f km/h - STOP data collection (VEHICLE)", thresholdKmh))
                            isCollectingData = false
                            captureCounter = 0
                            serviceScope.launch { preferencesManager.setCollectingData(false) }
                            stopAllSensors()
                            synchronized(sensorDataBuffer) {
                                val discarded = sensorDataBuffer.size
                                sensorDataBuffer.clear()
                                if (discarded > 0) {
                                    android.util.Log.d("IMUDataService", String.format("Discarded %d samples (vehicle speed: %.1f km/h)", discarded, speedKmh))
                                }
                            }
                        }
                        else{

                        }
                    }
                } else {
                    // Poor GPS accuracy - will use continuous indoor capture (handled by GPS signal monitoring)
                    android.util.Log.d("IMUDataService", String.format("📡 GPS: Poor accuracy %.0fm - continuous indoor mode", accuracy))
                }
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
            android.util.Log.d("IMUDataService", "No sensor data in this batch")
            return
        }
        
        android.util.Log.d("IMUDataService", "=== BATCH PROCESSING ===")
        android.util.Log.d("IMUDataService", "Processing batch with ${batch.size} sensor readings")
        android.util.Log.d("IMUDataService", "Expected: ~300 per batch (100 Hz target × 3s)")
        android.util.Log.d("IMUDataService", "Actual rate: ${batch.size / 3.0} Hz (may be lower if hardware throttled)")
        
        val sessionId = currentSessionId ?: serviceScope.async {
            sessionRepository.getActiveSession()?.sessionId
        }.await()
        
        android.util.Log.d("IMUDataService", "Session ID: ${sessionId ?: "null (no active session)"}")
        
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
        
        android.util.Log.d("IMUDataService", "Saving ${imuDataList.size} IMU data points to database")
        imuRepository.saveIMUDataBatch(imuDataList)
        android.util.Log.d("IMUDataService", "IMU batch saved to queue successfully")
        
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
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Sensor Data")
            .setContentText("Collecting GPS and IMU data...")
            .setSmallIcon(R.drawable.ic_sensors)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.w("IMUDataService", "Service being destroyed!")
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
