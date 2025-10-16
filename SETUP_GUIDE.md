# Setup Guide - IPS Data Acquisition App

## Prerequisites

1. **Android Studio** (Latest version - Hedgehog or later recommended)
2. **JDK 17** or higher
3. **Android SDK** with API Level 34
4. A physical Android device or emulator running Android 8.0 (API 26) or higher

## Step-by-Step Setup

### 1. Install Android Studio

Download and install Android Studio from: https://developer.android.com/studio

### 2. Clone or Open Project

```bash
cd /Users/sanjeevkumar/Business/IPS/ips-data-acquisition-app
```

Open this folder in Android Studio using **File → Open**

### 3. Configure Backend URL

Before running the app, you MUST configure your backend API URL:

1. Navigate to: `app/src/main/java/com/ips/dataacquisition/data/remote/RetrofitClient.kt`
2. Replace `"https://your-backend-api.com/api/v1/"` with your actual backend URL
3. Save the file

Example:
```kotlin
private const val BASE_URL = "https://api.myipsbackend.com/v1/"
```

### 4. Sync Gradle

Android Studio should automatically prompt you to sync Gradle. If not:
- Click **File → Sync Project with Gradle Files**
- Wait for sync to complete (may take a few minutes on first run)

### 5. Connect Device or Start Emulator

**Option A: Physical Device**
1. Enable Developer Options on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging"
2. Connect device via USB
3. Accept USB debugging prompt on device

**Option B: Emulator**
1. In Android Studio, click **Tools → Device Manager**
2. Click **Create Device**
3. Select a device (e.g., Pixel 6)
4. Select System Image (API 34 recommended)
5. Click **Finish**
6. Start the emulator

### 6. Build and Run

1. Click the **Run** button (green play icon) in Android Studio toolbar
2. Select your device/emulator
3. Wait for build to complete
4. App will install and launch automatically

### 7. Grant Permissions

On first launch, the app will request permissions:

1. **Location** - Tap "Allow" or "While using the app"
2. **Background Location** - Tap "Allow all the time" (required for data collection)
3. **Notifications** - Tap "Allow" (to see service status)

**Important**: For background data collection to work properly, you must grant "Allow all the time" for location permission.

### 8. Verify Services are Running

After granting permissions, you should see two ongoing notifications:
- "Recording Sensor Data" - IMU data collection service
- "Syncing Data" - Data synchronization service

## Testing the App

### Test Session Flow

1. Open the app on **Home** tab
2. Tap **"Entered Restaurant Building"** - This creates a session
3. You'll see available next actions
4. Follow the button sequence to complete a journey
5. Tap **"Left Delivery Building"** to end the session

### Test Data Collection

1. Start a session
2. Move around with the device
3. Check that IMU data is being collected (notification should be active)
4. Data will be synced to backend automatically

### Test Payment Status

1. Go to **Payment Status** tab
2. You should see completed sessions (after backend updates them)
3. Pull down to refresh

### Test Bonus Screen

1. Go to **Bonus** tab
2. View bonuses (after backend sends bonus data)
3. Pull down to refresh

## Troubleshooting

### Gradle Sync Failed

**Error**: Gradle sync failed with various errors

**Solution**:
1. Click **File → Invalidate Caches → Invalidate and Restart**
2. Ensure you have JDK 17 installed
3. Check your internet connection
4. Try: **File → Sync Project with Gradle Files** again

### App Crashes on Launch

**Possible Causes**:
1. Backend URL not configured → Update RetrofitClient.kt
2. Permissions not granted → Grant all required permissions
3. Missing dependencies → Clean and rebuild project

**Solution**:
```bash
# In Android Studio Terminal
./gradlew clean
./gradlew build
```

### Location Permission Issues

**Issue**: App doesn't show "Allow all the time" option

**Solution** (Android 10+):
1. Grant "While using the app" first
2. Go to device Settings → Apps → IPS Data Acquisition → Permissions → Location
3. Select "Allow all the time"

### Services Not Running

**Issue**: Notifications not showing

**Solution**:
1. Check battery optimization settings
2. Go to Settings → Apps → IPS Data Acquisition → Battery
3. Select "Unrestricted" or "Don't optimize"

### Data Not Syncing

**Check**:
1. Internet connection
2. Backend URL is correct
3. Backend server is running
4. Check Logcat in Android Studio for errors

**View Logs**:
1. Open **Logcat** tab in Android Studio
2. Filter by package: `com.ips.dataacquisition`
3. Look for error messages

### Build Errors

**Missing SDK Packages**:
```
Tools → SDK Manager → SDK Platforms
- Install Android 14.0 (API 34)

Tools → SDK Manager → SDK Tools
- Install Android SDK Build-Tools 34
- Install Android SDK Platform-Tools
```

## Backend Requirements

Your backend must implement these endpoints:

### Required Endpoints

1. **POST /sessions/create**
   - Request: `{session_id: string, timestamp: number}`
   - Response: `{success: boolean, data: {session_id, created_at}}`

2. **POST /sessions/close**
   - Request: `{session_id: string, end_timestamp: number}`
   - Response: `{success: boolean}`

3. **POST /button-presses**
   - Request: `{session_id, action, timestamp}`
   - Response: `{success: boolean}`

4. **POST /button-presses/batch**
   - Request: `[{session_id, action, timestamp}, ...]`
   - Response: `{success: boolean}`

5. **POST /imu-data/upload**
   - Request: `{session_id, data_points: [{timestamp, accel_x, ...}, ...]}`
   - Response: `{success: boolean}`

6. **GET /sessions**
   - Response: `{success: boolean, data: [Session, ...]}`

7. **GET /bonuses**
   - Response: `{success: boolean, data: [Bonus, ...]}`

### Test Backend Locally

You can test with a mock backend:

```bash
# Using json-server (Node.js)
npm install -g json-server
json-server --watch db.json --port 3000
```

Create `db.json`:
```json
{
  "sessions": [],
  "button-presses": [],
  "imu-data": [],
  "bonuses": []
}
```

Update `BASE_URL` to your local IP:
```kotlin
private const val BASE_URL = "http://192.168.1.100:3000/"
```

## Production Build

### Create Release Build

1. **Generate Keystore**:
```bash
keytool -genkey -v -keystore ips-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias ips-key
```

2. **Add to project root `gradle.properties`**:
```properties
IPS_RELEASE_STORE_FILE=../ips-release-key.jks
IPS_RELEASE_STORE_PASSWORD=your-password
IPS_RELEASE_KEY_ALIAS=ips-key
IPS_RELEASE_KEY_PASSWORD=your-password
```

3. **Build Release APK**:
```bash
./gradlew assembleRelease
```

APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Need Help?

- Check Android Studio Logcat for detailed error messages
- Review the main README.md for feature documentation
- Ensure all permissions are granted
- Verify backend is accessible from your device/emulator

## Next Steps

1. Configure your backend URL
2. Build and test the app
3. Verify data collection works
4. Test with your backend API
5. Deploy backend to production
6. Build release APK
7. Distribute to data collectors

