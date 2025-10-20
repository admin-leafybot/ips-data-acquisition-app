# Timestamp Specification
## Dual-Timestamp Strategy for Maximum Flexibility

**Version**: 2.1  
**Last Updated**: October 2025

---

## 🎯 Timestamp Strategy Summary

### Button Presses & Sessions
- **Single Timestamp**: Unix milliseconds (wall-clock time)

### IMU Sensor Data  
- **Dual Timestamps**: Unix nanoseconds + Sensor nanoseconds
- **Best of both worlds**: Correlation + Hardware precision

---

## ⏱️ Detailed Breakdown

### 1. Button Presses & Sessions

**Field**: `Timestamp`  
**Format**: Unix milliseconds  
**Type**: `Long` (13 digits)  
**Example**: `1697587200000`

```kotlin
val timestamp = System.currentTimeMillis()
```

**Properties**:
- ✅ Absolute wall-clock time
- ✅ Never resets (Unix epoch since 1970)
- ✅ Can convert to human-readable date
- ✅ Precision: 1 millisecond
- ✅ Perfect for events seconds/minutes apart

---

### 2. IMU Sensor Data (DUAL TIMESTAMPS)

#### Timestamp 1: Wall-Clock (Unix Nanoseconds)

**Field**: `Timestamp`  
**Format**: Unix nanoseconds  
**Type**: `Long` (19 digits)  
**Example**: `1697587200123000000`

```kotlin
val timestamp = System.currentTimeMillis() * 1_000_000
```

**Properties**:
- ✅ Absolute wall-clock time
- ✅ **Never resets on boot** (Unix epoch)
- ✅ **Can convert to actual date/time** in backend
- ✅ Precision: 1 microsecond (from ms source)
- ✅ Direct correlation with button presses
- ✅ Same time base for all data

**Backend Conversion**:
```javascript
// Convert to milliseconds
const timestampMs = timestamp / 1_000_000;

// Convert to Date
const date = new Date(timestampMs);
// 2023-10-17T20:00:00.123Z
```

#### Timestamp 2: Hardware Sensor (Sensor Nanoseconds)

**Field**: `TimestampNanos`  
**Format**: Nanoseconds since device boot  
**Type**: `Long` (19 digits)  
**Example**: `1234567890123456789`

```kotlin
val timestampNanos = event.timestamp  // From SensorEvent
```

**Properties**:
- ✅ Exact hardware timing
- ✅ Perfect sensor synchronization
- ✅ Precision: 1 nanosecond (true hardware precision)
- ✅ Best for sensor fusion algorithms
- ⚠️ **Resets on device boot**
- ⚠️ Cannot convert to absolute date (relative timing only)

**Use For**:
- Time differences between samples
- Sampling rate calculation  
- Sensor fusion (accel + gyro + mag sync)
- Derivative calculations (velocity, jerk)

---

## 🎯 Why Both Timestamps?

### Timestamp (Unix Nanos) - For Correlation
```python
# Button press
button_time_ms = 1697587200000
button_time_ns = button_time_ms * 1_000_000  # 1697587200000000000

# IMU data 123ms later
imu_time_ns = 1697587200123000000

# Direct subtraction works!
time_after_button_ns = imu_time_ns - button_time_ns  # 123000000 ns
time_after_button_ms = time_after_button_ns / 1_000_000  # 123 ms
```

### TimestampNanos (Sensor) - For Precision
```python
# Calculate exact sampling rate using hardware timestamps
sample1_sensor = 1234567890123456789
sample2_sensor = 1234567890128456789

diff_nanos = sample2_sensor - sample1_sensor  # 5000000 ns = 5 ms
sampling_rate = 1_000_000_000 / diff_nanos    # 200 Hz (exact!)
```

---

## 📊 Example Data

### Button Press (Single Timestamp)
```json
{
  "SessionId": "550e8400-e29b-41d4-a716-446655440000",
  "Action": "ENTERED_RESTAURANT_BUILDING",
  "Timestamp": 1697587200000
}
```
**Note**: 13 digits (milliseconds)

### IMU Data Point (Dual Timestamps)
```json
{
  "SessionId": "550e8400-e29b-41d4-a716-446655440000",
  "Timestamp": 1697587200123000000,
  "TimestampNanos": 1234567890123456789,
  "AccelX": 0.123,
  "AccelY": 0.456,
  "AccelZ": 9.789,
  ...
}
```
**Note**: 
- `Timestamp`: 19 digits (Unix nanoseconds)
- `TimestampNanos`: 19 digits (sensor nanoseconds)

---

## 💾 Backend Database Schema

```sql
-- Button Presses
CREATE TABLE button_presses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36),
    action VARCHAR(50),
    timestamp BIGINT NOT NULL,  -- Unix milliseconds (13 digits)
    ...
);

-- IMU Data
CREATE TABLE imu_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36),
    timestamp BIGINT NOT NULL,        -- Unix nanoseconds (19 digits)
    timestamp_nanos BIGINT NOT NULL,  -- Sensor nanoseconds (19 digits)
    accel_x FLOAT,
    ... -- 61 sensor parameters
);
```

---

## 🧮 Backend Usage Examples

### Convert Timestamp to Date
```python
# Receive from app
data_point = {
    "Timestamp": 1697587200123000000,
    "TimestampNanos": 1234567890123456789,
    ...
}

# Convert Timestamp to datetime
timestamp_seconds = data_point['Timestamp'] / 1_000_000_000
dt = datetime.fromtimestamp(timestamp_seconds)
print(dt)
# Output: 2023-10-17 20:00:00.123000
```

### Calculate Time After Button Press
```python
# Get button press time (ms)
button = get_button_press(session_id, action='ENTERED_ELEVATOR')
button_time_ns = button['Timestamp'] * 1_000_000  # Convert ms to ns

# Get IMU data
imu_data = get_imu_data(session_id)

# Calculate how long after button press
for sample in imu_data:
    time_after_button_ms = (sample['Timestamp'] - button_time_ns) / 1_000_000
    if 0 <= time_after_button_ms <= 5000:  # Within 5 seconds after
        print(f"Sample {time_after_button_ms}ms after button press")
```

### Calculate Sampling Rate (Use Hardware Timestamp)
```python
# Use TimestampNanos for most accurate sampling rate
samples = get_imu_data(session_id, limit=100)

diffs = []
for i in range(1, len(samples)):
    diff = samples[i]['TimestampNanos'] - samples[i-1]['TimestampNanos']
    diffs.append(diff)

avg_diff_ns = sum(diffs) / len(diffs)
sampling_rate = 1_000_000_000 / avg_diff_ns
print(f"Exact sampling rate: {sampling_rate:.2f} Hz")
```

### Sensor Fusion (Use Hardware Timestamp)
```python
# When combining accel + gyro + mag, use TimestampNanos
# Ensures all sensors are time-aligned to hardware clock
accel_at_time = get_accel(timestamp_nanos=t)
gyro_at_time = get_gyro(timestamp_nanos=t)  
mag_at_time = get_mag(timestamp_nanos=t)

# Fuse sensors using same hardware timestamp
orientation = sensor_fusion(accel_at_time, gyro_at_time, mag_at_time)
```

---

## 📐 Timestamp Comparison Table

| Aspect | Button Press | IMU - Timestamp | IMU - TimestampNanos |
|--------|-------------|-----------------|----------------------|
| **Format** | Unix ms | Unix ns | Sensor ns |
| **Digits** | 13 | 19 | 19 |
| **Base** | Jan 1, 1970 | Jan 1, 1970 | Device boot |
| **Resets on boot?** | ❌ No | ❌ No | ✅ Yes |
| **Precision** | 1 ms | 1 μs | 1 ns |
| **Convert to date?** | ✅ Yes | ✅ Yes | ❌ No |
| **Correlation** | Direct | Direct | Via session |
| **Hardware sync** | N/A | ⚠️ Software | ✅ Hardware |
| **Use for** | Events | Correlation | Precision |

---

## ✅ Benefits of Dual Timestamps

### 1. **Correlation** (Use `Timestamp`)
```python
# Match IMU data to button presses directly
imu_during_elevator = query("""
    SELECT * FROM imu_data
    WHERE timestamp BETWEEN ? AND ?
""", button_time_ns - 5_000_000_000, button_time_ns + 5_000_000_000)
```

### 2. **Precision** (Use `TimestampNanos`)
```python
# Calculate exact velocity from acceleration
v = 0
for i in range(1, len(samples)):
    # Use hardware timestamp for maximum precision
    dt = (samples[i]['TimestampNanos'] - samples[i-1]['TimestampNanos']) / 1e9
    accel = samples[i]['AccelX']
    v += accel * dt  # Precise integration
```

### 3. **Absolute Time** (Use `Timestamp`)
```sql
-- Query by actual date/time
SELECT * FROM imu_data
WHERE timestamp >= 1697587200000000000  -- Oct 17, 2023 20:00:00
  AND timestamp < 1697673600000000000   -- Oct 18, 2023 20:00:00
```

### 4. **Sensor Fusion** (Use `TimestampNanos`)
```python
# Align multiple sensors to hardware clock
def fuse_sensors(accel_data, gyro_data, mag_data):
    # Match by TimestampNanos (hardware synchronized)
    for a, g, m in zip_by_hardware_time(accel, gyro, mag):
        orientation = kalman_filter(a, g, m)
```

---

## 📋 Summary

### What Each Timestamp Provides

**`Timestamp` (Unix nanoseconds)**:
- ✅ Absolute wall-clock time
- ✅ Never resets
- ✅ Correlate with button presses
- ✅ Convert to dates
- ✅ Same time base as sessions

**`TimestampNanos` (Sensor nanoseconds)**:
- ✅ True hardware precision
- ✅ Exact sensor timing
- ✅ Perfect for sensor fusion
- ✅ Maximum accuracy for derivatives
- ✅ Synchronized with sensor clock

### When to Use Which

**Use `Timestamp` for**:
- Correlating with button presses
- Converting to human dates
- Timeline visualization
- Event synchronization

**Use `TimestampNanos` for**:
- Calculating sampling rate
- Sensor fusion algorithms
- Velocity/jerk calculations
- High-precision timing

---

## 🔧 Backend Implementation

### Database Schema
```sql
ALTER TABLE imu_data
ADD COLUMN timestamp_nanos BIGINT NOT NULL DEFAULT 0;

-- Indexes
CREATE INDEX idx_timestamp ON imu_data(timestamp);
CREATE INDEX idx_timestamp_nanos ON imu_data(timestamp_nanos);
```

### Example Queries

**Get IMU data by date**:
```sql
-- Use Timestamp (Unix nanos)
SELECT * FROM imu_data
WHERE timestamp >= 1697587200000000000
ORDER BY timestamp ASC;
```

**Calculate precise sampling rate**:
```sql
-- Use TimestampNanos (hardware timing)
SELECT 
    AVG(timestamp_nanos - LAG(timestamp_nanos) OVER (ORDER BY timestamp_nanos)) as avg_diff_nanos,
    1000000000.0 / AVG(timestamp_nanos - LAG(timestamp_nanos) OVER (ORDER BY timestamp_nanos)) as sampling_rate_hz
FROM imu_data
WHERE session_id = 'uuid';
```

---

## ✅ **Result: Best of Both Worlds!**

### For ML Training You Now Have:

1. **Absolute Time** → Correlate with ground truth labels (button presses)
2. **Hardware Precision** → Accurate feature extraction
3. **Flexibility** → Choose which timestamp based on analysis needs
4. **Completeness** → No information lost

**This is the optimal solution for indoor positioning ML!** 🎯

---

**Status**: ✅ **IMPLEMENTED**  
**Database Version**: 3  
**IMU Fields**: 63 total (61 sensors + 2 timestamps)  
**Last Updated**: October 2025
