package ai.indoorbrain.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "imu_data")
data class IMUData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    @SerializedName("SessionId")
    val sessionId: String?,
    
    @ColumnInfo(name = "timestamp")
    @SerializedName("Timestamp")
    val timestamp: Long,  // Unix nanoseconds (wall-clock time with nano precision)
    
    @ColumnInfo(name = "timestamp_nanos")
    @SerializedName("TimestampNanos")
    val timestampNanos: Long,  // Sensor event nanoseconds (hardware timing, since boot)
    
    // Calibrated Accelerometer (m/s²)
    @ColumnInfo(name = "accel_x")
    @SerializedName("AccelX")
    val accelX: Float,
    
    @ColumnInfo(name = "accel_y")
    @SerializedName("AccelY")
    val accelY: Float,
    
    @ColumnInfo(name = "accel_z")
    @SerializedName("AccelZ")
    val accelZ: Float,
    
    // Calibrated Gyroscope (rad/s)
    @ColumnInfo(name = "gyro_x")
    @SerializedName("GyroX")
    val gyroX: Float,
    
    @ColumnInfo(name = "gyro_y")
    @SerializedName("GyroY")
    val gyroY: Float,
    
    @ColumnInfo(name = "gyro_z")
    @SerializedName("GyroZ")
    val gyroZ: Float,
    
    // Calibrated Magnetometer (μT)
    @ColumnInfo(name = "mag_x")
    @SerializedName("MagX")
    val magX: Float,
    
    @ColumnInfo(name = "mag_y")
    @SerializedName("MagY")
    val magY: Float,
    
    @ColumnInfo(name = "mag_z")
    @SerializedName("MagZ")
    val magZ: Float,
    
    // Gravity (m/s²)
    @ColumnInfo(name = "gravity_x")
    @SerializedName("GravityX")
    val gravityX: Float? = null,
    
    @ColumnInfo(name = "gravity_y")
    @SerializedName("GravityY")
    val gravityY: Float? = null,
    
    @ColumnInfo(name = "gravity_z")
    @SerializedName("GravityZ")
    val gravityZ: Float? = null,
    
    // Linear Acceleration (m/s²)
    @ColumnInfo(name = "linear_accel_x")
    @SerializedName("LinearAccelX")
    val linearAccelX: Float? = null,
    
    @ColumnInfo(name = "linear_accel_y")
    @SerializedName("LinearAccelY")
    val linearAccelY: Float? = null,
    
    @ColumnInfo(name = "linear_accel_z")
    @SerializedName("LinearAccelZ")
    val linearAccelZ: Float? = null,
    
    // Uncalibrated Accelerometer (m/s²)
    @ColumnInfo(name = "accel_uncal_x")
    @SerializedName("AccelUncalX")
    val accelUncalX: Float? = null,
    
    @ColumnInfo(name = "accel_uncal_y")
    @SerializedName("AccelUncalY")
    val accelUncalY: Float? = null,
    
    @ColumnInfo(name = "accel_uncal_z")
    @SerializedName("AccelUncalZ")
    val accelUncalZ: Float? = null,
    
    @ColumnInfo(name = "accel_bias_x")
    @SerializedName("AccelBiasX")
    val accelBiasX: Float? = null,
    
    @ColumnInfo(name = "accel_bias_y")
    @SerializedName("AccelBiasY")
    val accelBiasY: Float? = null,
    
    @ColumnInfo(name = "accel_bias_z")
    @SerializedName("AccelBiasZ")
    val accelBiasZ: Float? = null,
    
    // Uncalibrated Gyroscope (rad/s)
    @ColumnInfo(name = "gyro_uncal_x")
    @SerializedName("GyroUncalX")
    val gyroUncalX: Float? = null,
    
    @ColumnInfo(name = "gyro_uncal_y")
    @SerializedName("GyroUncalY")
    val gyroUncalY: Float? = null,
    
    @ColumnInfo(name = "gyro_uncal_z")
    @SerializedName("GyroUncalZ")
    val gyroUncalZ: Float? = null,
    
    @ColumnInfo(name = "gyro_drift_x")
    @SerializedName("GyroDriftX")
    val gyroDriftX: Float? = null,
    
    @ColumnInfo(name = "gyro_drift_y")
    @SerializedName("GyroDriftY")
    val gyroDriftY: Float? = null,
    
    @ColumnInfo(name = "gyro_drift_z")
    @SerializedName("GyroDriftZ")
    val gyroDriftZ: Float? = null,
    
    // Uncalibrated Magnetometer (μT)
    @ColumnInfo(name = "mag_uncal_x")
    @SerializedName("MagUncalX")
    val magUncalX: Float? = null,
    
    @ColumnInfo(name = "mag_uncal_y")
    @SerializedName("MagUncalY")
    val magUncalY: Float? = null,
    
    @ColumnInfo(name = "mag_uncal_z")
    @SerializedName("MagUncalZ")
    val magUncalZ: Float? = null,
    
    @ColumnInfo(name = "mag_bias_x")
    @SerializedName("MagBiasX")
    val magBiasX: Float? = null,
    
    @ColumnInfo(name = "mag_bias_y")
    @SerializedName("MagBiasY")
    val magBiasY: Float? = null,
    
    @ColumnInfo(name = "mag_bias_z")
    @SerializedName("MagBiasZ")
    val magBiasZ: Float? = null,
    
    // Rotation Vector (quaternion)
    @ColumnInfo(name = "rotation_vector_x")
    @SerializedName("RotationVectorX")
    val rotationVectorX: Float? = null,
    
    @ColumnInfo(name = "rotation_vector_y")
    @SerializedName("RotationVectorY")
    val rotationVectorY: Float? = null,
    
    @ColumnInfo(name = "rotation_vector_z")
    @SerializedName("RotationVectorZ")
    val rotationVectorZ: Float? = null,
    
    @ColumnInfo(name = "rotation_vector_w")
    @SerializedName("RotationVectorW")
    val rotationVectorW: Float? = null,
    
    // Game Rotation Vector (quaternion, no magnetometer)
    @ColumnInfo(name = "game_rotation_x")
    @SerializedName("GameRotationX")
    val gameRotationX: Float? = null,
    
    @ColumnInfo(name = "game_rotation_y")
    @SerializedName("GameRotationY")
    val gameRotationY: Float? = null,
    
    @ColumnInfo(name = "game_rotation_z")
    @SerializedName("GameRotationZ")
    val gameRotationZ: Float? = null,
    
    @ColumnInfo(name = "game_rotation_w")
    @SerializedName("GameRotationW")
    val gameRotationW: Float? = null,
    
    // Geomagnetic Rotation Vector (quaternion)
    @ColumnInfo(name = "geomag_rotation_x")
    @SerializedName("GeomagRotationX")
    val geomagRotationX: Float? = null,
    
    @ColumnInfo(name = "geomag_rotation_y")
    @SerializedName("GeomagRotationY")
    val geomagRotationY: Float? = null,
    
    @ColumnInfo(name = "geomag_rotation_z")
    @SerializedName("GeomagRotationZ")
    val geomagRotationZ: Float? = null,
    
    @ColumnInfo(name = "geomag_rotation_w")
    @SerializedName("GeomagRotationW")
    val geomagRotationW: Float? = null,
    
    // Environmental Sensors
    @ColumnInfo(name = "pressure")
    @SerializedName("Pressure")
    val pressure: Float? = null, // hPa (millibar)
    
    @ColumnInfo(name = "temperature")
    @SerializedName("Temperature")
    val temperature: Float? = null, // °C
    
    @ColumnInfo(name = "light")
    @SerializedName("Light")
    val light: Float? = null, // lux
    
    @ColumnInfo(name = "humidity")
    @SerializedName("Humidity")
    val humidity: Float? = null, // %
    
    @ColumnInfo(name = "proximity")
    @SerializedName("Proximity")
    val proximity: Float? = null, // cm (or binary 0/1)
    
    // Activity Sensors
    @ColumnInfo(name = "step_counter")
    @SerializedName("StepCounter")
    val stepCounter: Int? = null, // cumulative steps
    
    @ColumnInfo(name = "step_detected")
    @SerializedName("StepDetected")
    val stepDetected: Boolean? = null, // true if step detected this sample
    
    // Computed Orientation (degrees)
    @ColumnInfo(name = "roll")
    @SerializedName("Roll")
    val roll: Float? = null,
    
    @ColumnInfo(name = "pitch")
    @SerializedName("Pitch")
    val pitch: Float? = null,
    
    @ColumnInfo(name = "yaw")
    @SerializedName("Yaw")
    val yaw: Float? = null,
    
    @ColumnInfo(name = "heading")
    @SerializedName("Heading")
    val heading: Float? = null, // 0-360 degrees
    
    // GPS (optional - only when user slows down)
    @ColumnInfo(name = "latitude")
    @SerializedName("Latitude")
    val latitude: Double? = null,
    
    @ColumnInfo(name = "longitude")
    @SerializedName("Longitude")
    val longitude: Double? = null,
    
    @ColumnInfo(name = "altitude")
    @SerializedName("Altitude")
    val altitude: Double? = null,
    
    @ColumnInfo(name = "gps_accuracy")
    @SerializedName("GpsAccuracy")
    val gpsAccuracy: Float? = null,
    
    @ColumnInfo(name = "speed")
    @SerializedName("Speed")
    val speed: Float? = null,
    
    @ColumnInfo(name = "is_synced")
    @SerializedName("IsSynced")
    val isSynced: Boolean = false
)

// Batch of IMU data for efficient storage and transmission
data class IMUDataBatch(
    @SerializedName("session_id")
    val sessionId: String?,
    
    @SerializedName("start_timestamp")
    val startTimestamp: Long,
    
    @SerializedName("end_timestamp")
    val endTimestamp: Long,
    
    @SerializedName("data_points")
    val dataPoints: List<IMUData>
)

