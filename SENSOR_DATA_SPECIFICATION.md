# Complete Sensor Data Specification
## IPS Data Acquisition App - Comprehensive Sensor List

**Version**: 2.0  
**Updated**: October 2025

---

## Overview

The Android app collects **comprehensive sensor data** from all available device sensors for maximum ML model training quality. This document details every sensor parameter collected.

---

## Data Collection Summary

### Sensor Categories

| Category | Sensors | Total Parameters |
|----------|---------|------------------|
| **Calibrated Motion** | Accelerometer, Gyroscope, Magnetometer, Gravity, Linear Acceleration | 15 params |
| **Uncalibrated Motion** | Accel Uncal, Gyro Uncal, Mag Uncal | 18 params (with bias/drift) |
| **Rotation Vectors** | Rotation Vector, Game Rotation, Geomagnetic Rotation | 12 params (quaternions) |
| **Environmental** | Pressure, Temperature, Light, Humidity, Proximity | 5 params |
| **Activity** | Step Counter, Step Detector | 2 params |
| **Computed** | Roll, Pitch, Yaw, Heading | 4 params |
| **GPS** | Location data | 5 params |
| **TOTAL** | **21 Sensors** | **61 Parameters** |

---

## Complete Data Model

### IMU Data Point Structure

```json
{
  "timestamp": 1697587210100,
  "session_id": "uuid-here",
  
  // CALIBRATED MOTION SENSORS (15 params)
  "accel_x": 0.123,           // m/s² - Calibrated accelerometer X
  "accel_y": 0.456,           // m/s² - Calibrated accelerometer Y
  "accel_z": 9.789,           // m/s² - Calibrated accelerometer Z
  
  "gyro_x": 0.012,            // rad/s - Calibrated gyroscope X
  "gyro_y": 0.034,            // rad/s - Calibrated gyroscope Y
  "gyro_z": 0.056,            // rad/s - Calibrated gyroscope Z
  
  "mag_x": 23.4,              // μT - Calibrated magnetometer X
  "mag_y": 12.5,              // μT - Calibrated magnetometer Y
  "mag_z": 45.6,              // μT - Calibrated magnetometer Z
  
  "gravity_x": 0.05,          // m/s² - Gravity X component
  "gravity_y": 0.10,          // m/s² - Gravity Y component
  "gravity_z": 9.81,          // m/s² - Gravity Z component
  
  "linear_accel_x": 0.073,    // m/s² - Linear acceleration X (no gravity)
  "linear_accel_y": 0.356,    // m/s² - Linear acceleration Y (no gravity)
  "linear_accel_z": -0.021,   // m/s² - Linear acceleration Z (no gravity)
  
  // UNCALIBRATED SENSORS WITH BIAS (18 params)
  "accel_uncal_x": 0.125,     // m/s² - Uncalibrated accelerometer X
  "accel_uncal_y": 0.458,     // m/s² - Uncalibrated accelerometer Y
  "accel_uncal_z": 9.791,     // m/s² - Uncalibrated accelerometer Z
  "accel_bias_x": 0.002,      // m/s² - Accelerometer bias X
  "accel_bias_y": 0.002,      // m/s² - Accelerometer bias Y
  "accel_bias_z": 0.002,      // m/s² - Accelerometer bias Z
  
  "gyro_uncal_x": 0.013,      // rad/s - Uncalibrated gyroscope X
  "gyro_uncal_y": 0.035,      // rad/s - Uncalibrated gyroscope Y
  "gyro_uncal_z": 0.057,      // rad/s - Uncalibrated gyroscope Z
  "gyro_drift_x": 0.001,      // rad/s - Gyroscope drift X
  "gyro_drift_y": 0.001,      // rad/s - Gyroscope drift Y
  "gyro_drift_z": 0.001,      // rad/s - Gyroscope drift Z
  
  "mag_uncal_x": 23.5,        // μT - Uncalibrated magnetometer X
  "mag_uncal_y": 12.6,        // μT - Uncalibrated magnetometer Y
  "mag_uncal_z": 45.7,        // μT - Uncalibrated magnetometer Z
  "mag_bias_x": 0.1,          // μT - Magnetometer bias X
  "mag_bias_y": 0.1,          // μT - Magnetometer bias Y
  "mag_bias_z": 0.1,          // μT - Magnetometer bias Z
  
  // ROTATION VECTORS (12 params - quaternions)
  "rotation_vector_x": 0.123,  // Rotation vector X (quaternion)
  "rotation_vector_y": 0.456,  // Rotation vector Y (quaternion)
  "rotation_vector_z": 0.789,  // Rotation vector Z (quaternion)
  "rotation_vector_w": 0.234,  // Rotation vector W (quaternion scalar)
  
  "game_rotation_x": 0.125,    // Game rotation X (no mag)
  "game_rotation_y": 0.458,    // Game rotation Y (no mag)
  "game_rotation_z": 0.791,    // Game rotation Z (no mag)
  "game_rotation_w": 0.236,    // Game rotation W (no mag)
  
  "geomag_rotation_x": 0.122,  // Geomagnetic rotation X
  "geomag_rotation_y": 0.455,  // Geomagnetic rotation Y
  "geomag_rotation_z": 0.788,  // Geomagnetic rotation Z
  "geomag_rotation_w": 0.233,  // Geomagnetic rotation W
  
  // ENVIRONMENTAL SENSORS (5 params)
  "pressure": 1013.25,         // hPa (millibar) - Barometric pressure
  "temperature": 22.5,         // °C - Ambient temperature
  "light": 450.0,              // lux - Ambient light level
  "humidity": 65.0,            // % - Relative humidity
  "proximity": 5.0,            // cm - Proximity distance (or 0/1 binary)
  
  // ACTIVITY SENSORS (2 params)
  "step_counter": 12543,       // int - Cumulative steps since boot
  "step_detected": true,       // boolean - Step detected this sample
  
  // COMPUTED ORIENTATION (4 params)
  "roll": 15.3,                // degrees - Roll angle
  "pitch": -5.2,               // degrees - Pitch angle
  "yaw": 45.8,                 // degrees - Yaw angle (azimuth)
  "heading": 45.8,             // degrees - Compass heading (0-360)
  
  // GPS DATA (5 params - optional, only when user slows down)
  "latitude": 37.7749,         // degrees - GPS latitude
  "longitude": -122.4194,      // degrees - GPS longitude
  "altitude": 10.5,            // meters - GPS altitude
  "gps_accuracy": 5.2,         // meters - GPS accuracy
  "speed": 0.8                 // m/s - GPS speed
}
```

---

## Sensor Details

### 1. Calibrated Motion Sensors (15 parameters)

#### Accelerometer (3-axis)
- **Fields**: `accel_x`, `accel_y`, `accel_z`
- **Unit**: m/s²
- **Range**: Typically ±2g to ±16g
- **Frequency**: ~100-200 Hz
- **Description**: Device-calibrated acceleration including gravity

#### Gyroscope (3-axis)
- **Fields**: `gyro_x`, `gyro_y`, `gyro_z`
- **Unit**: rad/s (radians per second)
- **Range**: Typically ±250°/s to ±2000°/s
- **Frequency**: ~100-200 Hz
- **Description**: Device-calibrated angular velocity

#### Magnetometer (3-axis)
- **Fields**: `mag_x`, `mag_y`, `mag_z`
- **Unit**: μT (microtesla)
- **Range**: ±50 to ±1200 μT
- **Frequency**: ~50-100 Hz
- **Description**: Device-calibrated magnetic field strength

#### Gravity (3-axis)
- **Fields**: `gravity_x`, `gravity_y`, `gravity_z`
- **Unit**: m/s²
- **Description**: Gravity components extracted from accelerometer
- **Note**: Uses low-pass filter on accelerometer data

#### Linear Acceleration (3-axis)
- **Fields**: `linear_accel_x`, `linear_accel_y`, `linear_accel_z`
- **Unit**: m/s²
- **Description**: Acceleration minus gravity = actual device movement
- **Formula**: linear_accel = accel - gravity

---

### 2. Uncalibrated Sensors with Bias (18 parameters)

#### Accelerometer Uncalibrated
- **Fields**: `accel_uncal_x/y/z`, `accel_bias_x/y/z`
- **Unit**: m/s²
- **Description**: Raw sensor + bias estimation
- **Use**: ML calibration, bias analysis
- **Formula**: calibrated = uncalibrated - bias

#### Gyroscope Uncalibrated
- **Fields**: `gyro_uncal_x/y/z`, `gyro_drift_x/y/z`
- **Unit**: rad/s
- **Description**: Raw sensor + drift estimation
- **Use**: Gyro drift modeling, IMU calibration

#### Magnetometer Uncalibrated
- **Fields**: `mag_uncal_x/y/z`, `mag_bias_x/y/z`
- **Unit**: μT
- **Description**: Raw sensor + hard/soft iron bias
- **Use**: Magnetic interference analysis

---

### 3. Rotation Vectors (12 parameters)

All rotation vectors use **quaternions** (x, y, z, w) for rotation representation.

#### Rotation Vector
- **Fields**: `rotation_vector_x/y/z/w`
- **Description**: Device orientation (accel + mag + gyro fusion)
- **Use**: Most accurate orientation
- **Note**: Affected by magnetic interference

#### Game Rotation Vector
- **Fields**: `game_rotation_x/y/z/w`
- **Description**: Device orientation (accel + gyro only, NO magnetometer)
- **Use**: Gaming, short-term orientation
- **Advantage**: Not affected by magnetic interference
- **Limitation**: Drifts over time without mag correction

#### Geomagnetic Rotation Vector
- **Fields**: `geomag_rotation_x/y/z/w`
- **Description**: Device orientation (accel + mag only, NO gyro)
- **Use**: Low-power orientation
- **Advantage**: No drift
- **Limitation**: Less responsive than rotation vector

**Quaternion Format**:
```
Quaternion: q = w + xi + yj + zk
Where: x, y, z are vector components
       w is scalar component
```

---

### 4. Environmental Sensors (5 parameters)

#### Barometric Pressure
- **Field**: `pressure`
- **Unit**: hPa (hectopascal) or millibar
- **Range**: 300-1100 hPa
- **Use**: Altitude estimation, floor detection
- **Formula**: altitude ≈ 44330 * (1 - (p/p0)^0.1903)

#### Ambient Temperature
- **Field**: `temperature`
- **Unit**: °C (Celsius)
- **Range**: -40 to 85°C
- **Use**: Environmental context
- **Note**: Device heat affects accuracy

#### Light Sensor
- **Field**: `light`
- **Unit**: lux
- **Range**: 0-64000 lux
- **Use**: Indoor/outdoor detection, lighting conditions
- **Reference**: Office ~320-500 lux, Sunlight ~32000-100000 lux

#### Relative Humidity
- **Field**: `humidity`
- **Unit**: % (percentage)
- **Range**: 0-100%
- **Use**: Environmental conditions
- **Note**: Not all devices have this sensor

#### Proximity Sensor
- **Field**: `proximity`
- **Unit**: cm or binary (0/1)
- **Use**: Object detection near device
- **Note**: Binary on many devices (near=0, far=max_range)

---

### 5. Activity Sensors (2 parameters)

#### Step Counter
- **Field**: `step_counter`
- **Type**: Integer (cumulative)
- **Description**: Total steps since device boot
- **Use**: Activity level, gait analysis
- **Note**: Resets on device reboot

#### Step Detector
- **Field**: `step_detected`
- **Type**: Boolean
- **Description**: true if step detected in this time window
- **Use**: Real-time step events
- **Frequency**: Event-based (not continuous)

---

### 6. Computed Orientation (4 parameters)

#### Roll, Pitch, Yaw
- **Fields**: `roll`, `pitch`, `yaw`
- **Unit**: degrees
- **Range**: -180 to 180°
- **Description**: Euler angles from rotation vector
- **Computation**: Using SensorManager.getOrientation()

**Definitions**:
- **Roll**: Rotation around device's longitudinal axis (X)
- **Pitch**: Rotation around device's lateral axis (Y)
- **Yaw**: Rotation around device's vertical axis (Z) = Azimuth

#### Heading
- **Field**: `heading`
- **Unit**: degrees
- **Range**: 0-360°
- **Description**: Compass direction (0=North, 90=East, 180=South, 270=West)
- **Computation**: From magnetometer + accelerometer
- **Use**: Navigation, orientation tracking

---

### 7. GPS Data (5 parameters)

**Collection Strategy**: GPS is **only activated when user slows down** (speed < 0.5 m/s) to save battery.

#### Latitude & Longitude
- **Fields**: `latitude`, `longitude`
- **Unit**: degrees
- **Range**: Lat: -90 to 90, Lon: -180 to 180
- **Precision**: ~7-8 decimal places

#### Altitude
- **Field**: `altitude`
- **Unit**: meters above sea level
- **Accuracy**: ±10-50m typical

#### GPS Accuracy
- **Field**: `gps_accuracy`
- **Unit**: meters (error radius)
- **Description**: Estimated horizontal accuracy

#### Speed
- **Field**: `speed`
- **Unit**: m/s
- **Description**: GPS-derived ground speed
- **Use**: Movement detection for battery optimization

---

## Nullable Fields

Most fields are **nullable** (can be `null`) because:

1. **Sensor Availability**: Not all devices have all sensors
2. **GPS Optimization**: GPS data only when moving slowly
3. **Sensor Initialization**: Sensors may not have readings yet
4. **Power Saving**: Environmental sensors sample less frequently

### Always Present (NOT NULL):
- `timestamp`
- `session_id`
- `accel_x/y/z` (basic accelerometer)
- `gyro_x/y/z` (basic gyroscope)
- `mag_x/y/z` (basic magnetometer)

### Usually Present:
- Rotation vectors (most modern devices)
- Gravity and linear acceleration
- Step counter (most devices)

### Often Missing:
- Uncalibrated sensors (Android 4.3+)
- Environmental sensors (device-dependent)
- GPS data (only when user slows down)

---

## Data Volume Estimates

### Per Data Point
- ~61 float/int fields × 4 bytes = **~244 bytes per sample**
- With JSON overhead: **~400-500 bytes per sample**

### Per Batch (5 seconds)
- Sampling rate: ~100 Hz for motion sensors
- Data points per batch: ~500 samples
- **Batch size: ~200-250 KB**

### Per Hour
- Batches per hour: 720
- **Data volume: ~144-180 MB/hour**

### Storage Recommendations
- Use **compression** for uploads
- Consider **binary format** instead of JSON for efficiency
- Implement **downsampling** for non-critical sensors

---

## API Endpoint Update

### POST /imu-data/upload

**Request** (simplified example with 2 data points):
```json
{
  "session_id": "uuid-here",
  "data_points": [
    {
      "timestamp": 1697587210100,
      "accel_x": 0.123, "accel_y": 0.456, "accel_z": 9.789,
      "gyro_x": 0.012, "gyro_y": 0.034, "gyro_z": 0.056,
      "mag_x": 23.4, "mag_y": 12.5, "mag_z": 45.6,
      "gravity_x": 0.05, "gravity_y": 0.10, "gravity_z": 9.81,
      "linear_accel_x": 0.073, "linear_accel_y": 0.356, "linear_accel_z": -0.021,
      "accel_uncal_x": 0.125, ... [all 18 uncal params],
      "rotation_vector_x": 0.123, ... [all 12 rotation params],
      "pressure": 1013.25, "temperature": 22.5, "light": 450.0, "humidity": 65.0, "proximity": 5.0,
      "step_counter": 12543, "step_detected": true,
      "roll": 15.3, "pitch": -5.2, "yaw": 45.8, "heading": 45.8,
      "latitude": 37.7749, "longitude": -122.4194, "altitude": 10.5, "gps_accuracy": 5.2, "speed": 0.8
    },
    { /* ... next data point ... */ }
  ]
}
```

---

## Database Schema Update

### Complete IMU Data Table

```sql
CREATE TABLE imu_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36),
    timestamp BIGINT NOT NULL,
    
    -- Calibrated motion (15 fields)
    accel_x FLOAT NOT NULL,
    accel_y FLOAT NOT NULL,
    accel_z FLOAT NOT NULL,
    gyro_x FLOAT NOT NULL,
    gyro_y FLOAT NOT NULL,
    gyro_z FLOAT NOT NULL,
    mag_x FLOAT NOT NULL,
    mag_y FLOAT NOT NULL,
    mag_z FLOAT NOT NULL,
    gravity_x FLOAT,
    gravity_y FLOAT,
    gravity_z FLOAT,
    linear_accel_x FLOAT,
    linear_accel_y FLOAT,
    linear_accel_z FLOAT,
    
    -- Uncalibrated sensors (18 fields)
    accel_uncal_x FLOAT,
    accel_uncal_y FLOAT,
    accel_uncal_z FLOAT,
    accel_bias_x FLOAT,
    accel_bias_y FLOAT,
    accel_bias_z FLOAT,
    gyro_uncal_x FLOAT,
    gyro_uncal_y FLOAT,
    gyro_uncal_z FLOAT,
    gyro_drift_x FLOAT,
    gyro_drift_y FLOAT,
    gyro_drift_z FLOAT,
    mag_uncal_x FLOAT,
    mag_uncal_y FLOAT,
    mag_uncal_z FLOAT,
    mag_bias_x FLOAT,
    mag_bias_y FLOAT,
    mag_bias_z FLOAT,
    
    -- Rotation vectors (12 fields)
    rotation_vector_x FLOAT,
    rotation_vector_y FLOAT,
    rotation_vector_z FLOAT,
    rotation_vector_w FLOAT,
    game_rotation_x FLOAT,
    game_rotation_y FLOAT,
    game_rotation_z FLOAT,
    game_rotation_w FLOAT,
    geomag_rotation_x FLOAT,
    geomag_rotation_y FLOAT,
    geomag_rotation_z FLOAT,
    geomag_rotation_w FLOAT,
    
    -- Environmental (5 fields)
    pressure FLOAT,
    temperature FLOAT,
    light FLOAT,
    humidity FLOAT,
    proximity FLOAT,
    
    -- Activity (2 fields)
    step_counter INT,
    step_detected BOOLEAN,
    
    -- Computed orientation (4 fields)
    roll FLOAT,
    pitch FLOAT,
    yaw FLOAT,
    heading FLOAT,
    
    -- GPS (5 fields)
    latitude DOUBLE,
    longitude DOUBLE,
    altitude DOUBLE,
    gps_accuracy FLOAT,
    speed FLOAT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    INDEX idx_session_timestamp (session_id, timestamp)
) PARTITION BY RANGE (timestamp);
```

**Total Columns**: 66 fields + metadata

---

## Benefits for ML Training

### Why Collect All These Sensors?

1. **Redundancy**: Multiple sensors measuring similar data → cross-validation
2. **Calibration**: Uncalibrated + bias data → learn calibration patterns
3. **Fusion**: Multiple rotation estimates → robust orientation
4. **Context**: Environmental data → understand conditions
5. **Features**: Rich feature set for ML models
6. **Flexibility**: Not all sensors needed for all models

### Recommended Analysis Approaches

1. **Feature Selection**: Use ML to determine most useful sensors
2. **Sensor Fusion**: Combine complementary sensors
3. **Anomaly Detection**: Use redundancy to detect bad sensors
4. **Calibration Learning**: Learn device-specific calibrations
5. **Context Classification**: Environmental sensors for scene understanding

---

## Summary

**Total Parameters Collected**: **61 sensor parameters** per sample

**Collection Rate**: ~100-200 samples/second

**Data Quality**: Research-grade sensor suite

**Battery Impact**: Optimized with selective GPS usage

**Device Compatibility**: Gracefully handles missing sensors

This comprehensive sensor collection enables state-of-the-art indoor positioning ML models with maximum data richness and redundancy.

---

**Document Status**: ✅ **CURRENT**  
**Last Updated**: October 2025  
**Version**: 2.0

