# API Quick Reference Guide
## IPS Data Acquisition - Backend Developer TL;DR

**Version**: 2.1 | **Last Updated**: October 2025

---

## ⚡ Quick Start

### Base URL
```
https://your-domain.com/api/v1/
```

### Standard Response
```json
{
  "success": true | false,
  "message": "...",
  "data": {}
}
```

---

## 📍 6 API Endpoints

### 1. POST `/sessions/create`
```bash
# Create new session
POST /sessions/create
{
  "session_id": "uuid",
  "timestamp": 1697587200000
}

→ 200 OK
{
  "success": true,
  "data": {"session_id": "uuid", "created_at": 1697587200000}
}
```

### 2. POST `/sessions/close`
```bash
# Close session
POST /sessions/close
{
  "session_id": "uuid",
  "end_timestamp": 1697587800000
}

→ 200 OK
{
  "success": true,
  "data": null
}
```

### 3. POST `/button-presses`
```bash
# Record waypoint (sent one at a time from queue)
POST /button-presses
{
  "session_id": "uuid",
  "action": "ENTERED_RESTAURANT_BUILDING",
  "timestamp": 1697587210000
}

→ 200 OK
{
  "success": true,
  "data": null
}
```

**Valid Actions**: `ENTERED_RESTAURANT_BUILDING`, `ENTERED_ELEVATOR`, `CLIMBING_STAIRS_3_FLOORS`, `GOING_UP_8_FLOORS_IN_LIFT`, `REACHED_RESTAURANT_CORRIDOR`, `REACHED_RESTAURANT`, `LEFT_RESTAURANT`, `COMING_DOWN_3_FLOORS`, `LEFT_RESTAURANT_BUILDING`, `ENTERED_DELIVERY_BUILDING`, `REACHED_DELIVERY_CORRIDOR`, `REACHED_DOORSTEP`, `LEFT_DOORSTEP`, `GOING_DOWN_8_FLOORS_IN_LIFT`, `LEFT_DELIVERY_BUILDING`

### 4. POST `/imu-data/upload`
```bash
# Upload sensor batch (~500 data points)
POST /imu-data/upload
{
  "session_id": "uuid",
  "data_points": [
    {
      "timestamp": 1697587210100,
      "accel_x": 0.123, "accel_y": 0.456, "accel_z": 9.789,
      "gyro_x": 0.012, "gyro_y": 0.034, "gyro_z": 0.056,
      "mag_x": 23.4, "mag_y": 12.5, "mag_z": 45.6,
      // ... 52 more sensor parameters (most nullable)
    }
  ]
}

→ 200 OK
{
  "success": true,
  "data": {"points_received": 500}
}
```

**Note**: Each data point has **61 sensor parameters** (see API_DOCUMENTATION.md for full list)

### 5. GET `/sessions`
```bash
# List user sessions
GET /sessions?page=1&limit=20

→ 200 OK
{
  "success": true,
  "data": [
    {
      "session_id": "uuid",
      "start_timestamp": 1697587200000,
      "end_timestamp": 1697587800000,
      "status": "approved",
      "payment_status": "paid",
      "remarks": null,
      "bonus_amount": 10.50
    }
  ]
}
```

**Status**: `in_progress`, `completed`, `approved`, `rejected`

### 6. GET `/bonuses`
```bash
# Get daily bonuses
GET /bonuses?start_date=2024-10-01&end_date=2024-10-31

→ 200 OK
{
  "success": true,
  "data": [
    {
      "date": "2024-10-15",
      "amount": 25.00,
      "sessions_completed": 5,
      "description": "Perfect data quality"
    }
  ]
}
```

---

## 💾 Database Tables

### sessions
```sql
session_id VARCHAR(36) PRIMARY KEY
start_timestamp BIGINT
end_timestamp BIGINT
status VARCHAR(20)  -- in_progress, completed, approved, rejected
payment_status VARCHAR(20)  -- unpaid, paid
remarks TEXT
bonus_amount DECIMAL(10,2)
```

### button_presses
```sql
id BIGINT AUTO_INCREMENT
session_id VARCHAR(36)
action VARCHAR(50)
timestamp BIGINT
is_synced BOOLEAN
```

### imu_data (66 columns!)
```sql
id BIGINT AUTO_INCREMENT
session_id VARCHAR(36)
timestamp BIGINT

-- Calibrated (15 fields)
accel_x/y/z FLOAT
gyro_x/y/z FLOAT
mag_x/y/z FLOAT
gravity_x/y/z FLOAT
linear_accel_x/y/z FLOAT

-- Uncalibrated (18 fields)
accel_uncal_x/y/z, accel_bias_x/y/z FLOAT
gyro_uncal_x/y/z, gyro_drift_x/y/z FLOAT
mag_uncal_x/y/z, mag_bias_x/y/z FLOAT

-- Rotation vectors (12 fields - quaternions)
rotation_vector_x/y/z/w FLOAT
game_rotation_x/y/z/w FLOAT
geomag_rotation_x/y/z/w FLOAT

-- Environmental (5 fields)
pressure, temperature, light, humidity, proximity FLOAT

-- Activity (2 fields)
step_counter INT, step_detected BOOLEAN

-- Computed (4 fields)
roll, pitch, yaw, heading FLOAT

-- GPS (5 fields)
latitude, longitude, altitude DOUBLE
gps_accuracy, speed FLOAT

is_synced BOOLEAN
```

### bonuses
```sql
date DATE
amount DECIMAL(10,2)
sessions_completed INT
description TEXT
```

---

## 📊 Traffic Patterns

| Endpoint | Frequency | Payload Size |
|----------|-----------|--------------|
| `/sessions/create` | Once per session | ~100 bytes |
| `/sessions/close` | Once per session | ~100 bytes |
| `/button-presses` | 1-2 per minute | ~150 bytes |
| `/imu-data/upload` | ~6 per minute | ~250 KB |
| `/sessions` (GET) | On user request | - |
| `/bonuses` (GET) | On user request | - |

**Key Notes**:
- Button presses sent **one at a time** (not batches)
- After offline: Burst of 5-20 button presses
- IMU data is HIGH VOLUME (recommend async processing)

---

## ⚠️ Important Notes

### Nullable Fields

Most IMU sensor fields are **nullable** because:
- Not all devices have all sensors
- GPS only active when user slows down
- Environmental sensors sample less frequently

**Your API MUST handle null values gracefully**

### Queue-Based App

Mobile app uses queue-first architecture:
```
User Action → Local Queue (instant ✓)
              ↓
      Background Service
              ↓
      When Online → Send to API
```

**Implications**:
- No immediate API calls on user action
- Sequential processing from queue
- Retry with exponential backoff
- Only syncs when internet available

### Performance

**IMU Data Endpoint**:
- ⚠️ HIGH VOLUME: ~250 KB every 10 seconds per user
- Recommend: Accept 200 OK, queue for async processing
- Use bulk insert operations
- Consider compression (gzip)

---

## 🧪 Test cURL Commands

```bash
# 1. Create session
curl -X POST http://localhost:3000/api/v1/sessions/create \
  -H "Content-Type: application/json" \
  -d '{"session_id":"123e4567-e89b-12d3-a456-426614174000","timestamp":1697587200000}'

# 2. Button press
curl -X POST http://localhost:3000/api/v1/button-presses \
  -H "Content-Type: application/json" \
  -d '{"session_id":"123e4567-e89b-12d3-a456-426614174000","action":"ENTERED_RESTAURANT_BUILDING","timestamp":1697587210000}'

# 3. Close session
curl -X POST http://localhost:3000/api/v1/sessions/close \
  -H "Content-Type: application/json" \
  -d '{"session_id":"123e4567-e89b-12d3-a456-426614174000","end_timestamp":1697587800000}'

# 4. Get sessions
curl http://localhost:3000/api/v1/sessions

# 5. Get bonuses
curl 'http://localhost:3000/api/v1/bonuses?start_date=2024-10-01&end_date=2024-10-31'
```

---

## 🚨 Common Issues

**Issue**: Getting null fields in IMU data  
**Solution**: Normal - many fields are nullable

**Issue**: Many button press requests  
**Solution**: Expected - queue processes one at a time

**Issue**: Large IMU payloads  
**Solution**: Enable gzip compression, use async processing

**Issue**: Burst traffic after offline  
**Solution**: Expected - queue backlog being processed

---

## 📚 Full Documentation

For complete details, see:
- **`API_DOCUMENTATION.md`** - Full API specification
- **`SENSOR_DATA_SPECIFICATION.md`** - All 61 sensor parameters
- **`QUEUE_ARCHITECTURE_SUMMARY.md`** - Mobile app behavior

---

## ✅ Implementation Checklist

- [ ] Set up 6 API endpoints
- [ ] Create 4 database tables
- [ ] Handle nullable IMU fields
- [ ] Implement error responses
- [ ] Add rate limiting
- [ ] Enable compression for IMU endpoint
- [ ] Test with cURL
- [ ] Deploy to staging
- [ ] Share staging URL with mobile team

---

**Status**: ✅ **CURRENT**  
**Version**: 2.1  
**Last Updated**: October 2025

