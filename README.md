# IPS Data Acquisition App

An Android application for collecting ground truth data for Indoor Positioning System (IPS) machine learning models.

## Features

- **Session-based Data Collection**: Track user journey through predefined waypoints
- **Sequential Button Flow**: Smart UI that only shows valid next actions based on current location
- **IMU Sensor Data Collection**: Continuous collection of:
  - Accelerometer data
  - Gyroscope data
  - Magnetometer data
  - GPS data (when user slows down for battery optimization)
- **Background Services**: Foreground services for continuous data collection even when phone is locked
- **Offline Support**: Local storage with automatic sync when network is available
- **Payment Status Tracking**: View session approval status and payment information
- **Bonus System**: Track daily bonuses for completed sessions

## Architecture

- **MVVM Architecture** with Jetpack Compose
- **Room Database** for local data storage
- **Retrofit** for API communication
- **Kotlin Coroutines** for asynchronous operations
- **Material Design 3** for modern UI

## Requirements

- Android SDK 26 (Android 8.0) or higher
- Target SDK 34 (Android 14)
- Kotlin 1.9.20
- Gradle 8.2.0

## Setup Instructions

### 1. Clone the Repository

```bash
git clone <your-repository-url>
cd ips-data-acquisition-app
```

### 2. Configure Backend URL

Open `app/src/main/java/com/ips/dataacquisition/data/remote/RetrofitClient.kt` and update the `BASE_URL`:

```kotlin
private const val BASE_URL = "https://your-backend-api.com/api/v1/"
```

### 3. Build the Project

Open the project in Android Studio and sync Gradle files.

### 4. Run the App

Connect an Android device or start an emulator, then click "Run" in Android Studio.

## Permissions

The app requires the following permissions:

- **Location** (Fine and Coarse): For GPS data collection
- **Background Location**: To collect data even when app is in background
- **Sensors**: For IMU data collection
- **Internet**: For API communication
- **Foreground Service**: For continuous data collection
- **Notifications**: To show service status

## User Flow

### Home Screen

1. User taps "Entered Restaurant Building" to start a session
2. App creates a session and starts recording data
3. Only valid next buttons are shown based on the current state
4. User follows the path through the building
5. Session ends when user taps "Left Delivery Building"

### Available Paths

**Restaurant Path:**
1. Entered Restaurant Building
2. Entered Elevator / Climbing Stairs 3 floors
3. Going up 8 floors in Lift (if elevator) / Reached Restaurant Corridor (if stairs)
4. Reached Restaurant Corridor (if elevator)
5. Reached Restaurant
6. Left Restaurant
7. Going down 8 floors in Lift / Coming Down 3 floors
8. Left Restaurant Building

**Delivery Path:**
1. Entered Delivery Building
2. Entered Elevator / Climbing Stairs 3 floors
3. Going up 8 floors in Lift (if elevator) / Reached Delivery Corridor (if stairs)
4. Reached Delivery Corridor
5. Reached Doorstep
6. Left Doorstep
7. Going down 8 floors in Lift / Coming Down 3 floors
8. Left Delivery Building

### Payment Status Screen

- View all completed sessions
- See approval status (Approved/Rejected/Pending)
- Check payment status (Paid/Unpaid)
- Read rejection remarks if applicable

### Bonus Screen

- View total bonus earned
- See daily bonus breakdown
- Track number of sessions completed per day

## Background Services

### IMU Data Service

- Continuously collects sensor data at maximum frequency
- Creates 5-second batches for efficient storage
- **Battery Optimization**: Only activates GPS when user speed drops below threshold
- Stores data locally with session association

### Data Sync Service

- Runs in background every 30 seconds
- Syncs pending button presses, sessions, and IMU data
- Automatically retries failed uploads
- Cleans up old synced data (older than 7 days)

## API Endpoints

The app expects the following REST API endpoints:

### Sessions
- `POST /sessions/create` - Create new session
- `POST /sessions/close` - Close session
- `GET /sessions` - Get user sessions

### Button Presses
- `POST /button-presses` - Submit single button press
- `POST /button-presses/batch` - Submit multiple button presses

### IMU Data
- `POST /imu-data/upload` - Upload IMU data batch

### Bonuses
- `GET /bonuses` - Get user bonuses

## Data Models

### Session
```json
{
  "session_id": "uuid",
  "start_timestamp": 1234567890,
  "end_timestamp": 1234567890,
  "status": "approved|rejected|completed|in_progress",
  "payment_status": "paid|unpaid",
  "remarks": "string",
  "bonus_amount": 10.50
}
```

### Button Press
```json
{
  "session_id": "uuid",
  "action": "ENTERED_RESTAURANT_BUILDING",
  "timestamp": 1234567890
}
```

### IMU Data
```json
{
  "session_id": "uuid",
  "timestamp": 1234567890,
  "accel_x": 0.5,
  "accel_y": 0.5,
  "accel_z": 9.8,
  "gyro_x": 0.0,
  "gyro_y": 0.0,
  "gyro_z": 0.0,
  "mag_x": 0.0,
  "mag_y": 0.0,
  "mag_z": 0.0,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "altitude": 10.0,
  "gps_accuracy": 5.0,
  "speed": 0.5
}
```

## Building for Production

### Release Build

1. Create a keystore:
```bash
keytool -genkey -v -keystore ips-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ips-key
```

2. Add to `gradle.properties`:
```properties
IPS_RELEASE_STORE_FILE=ips-release-key.jks
IPS_RELEASE_STORE_PASSWORD=your-password
IPS_RELEASE_KEY_ALIAS=ips-key
IPS_RELEASE_KEY_PASSWORD=your-password
```

3. Build release APK:
```bash
./gradlew assembleRelease
```

## Testing

The app includes unit tests and instrumentation tests. Run them with:

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Troubleshooting

### GPS Not Working
- Ensure location permissions are granted
- Check that location services are enabled on device
- Verify GPS has clear view of sky

### Data Not Syncing
- Check internet connection
- Verify backend URL is correct
- Check logs for API errors

### Service Stops Running
- Disable battery optimization for the app in system settings
- Ensure foreground service permissions are granted

## Privacy & Data

- All data is stored locally first
- Data is encrypted in transit (HTTPS)
- Users can view all data collected
- Sessions can be deleted from local storage

## License

[Your License Here]

## Support

For issues and questions, please contact [your-email@example.com]

