# 🎯 Sentry Integration Guide

## ✅ What's Already Done

1. ✅ Added Sentry dependencies to `app/build.gradle.kts`
2. ✅ Created `CloudLogger` utility class
3. ✅ Initialized Sentry in `IPSApplication.kt`

---

## 🚀 Setup Steps

### Step 1: Create a Sentry Account (5 minutes)

1. Go to [https://sentry.io/signup/](https://sentry.io/signup/)
2. Sign up (free tier: 5,000 events/month)
3. Create a new project:
   - Platform: **Android**
   - Name: **IPS Data Acquisition**
   - Team: Default

### Step 2: Get Your DSN (1 minute)

1. After project creation, you'll see your **DSN** (Data Source Name)
   - It looks like: `https://abc123@o123456.ingest.sentry.io/789012`
2. Copy this DSN

### Step 3: Add DSN to Your App (1 minute)

Replace `YOUR_SENTRY_DSN_HERE` in `IPSApplication.kt`:

```kotlin
options.dsn = "https://abc123@o123456.ingest.sentry.io/789012"  // Your actual DSN
```

---

## 📝 How to Use CloudLogger

### Replace Existing Logs

**Before:**
```kotlin
android.util.Log.d("SessionRepo", "$userIdPrefix 📤 Syncing ${count} button presses")
android.util.Log.e("IMURepository", "$userIdPrefix ❌ Batch failed", exception)
```

**After:**
```kotlin
CloudLogger.d("SessionRepo", "$userIdPrefix 📤 Syncing ${count} button presses")
CloudLogger.e("IMURepository", "$userIdPrefix ❌ Batch failed", exception)
```

### Set User Context (on login)

```kotlin
// In AuthViewModel after successful login
CloudLogger.setUser(userId = user.userId, phone = user.phone)
```

### Clear User Context (on logout)

```kotlin
// In AuthViewModel on logout
CloudLogger.clearUser()
```

### Capture Important Events

```kotlin
// For significant milestones
CloudLogger.captureEvent("Session completed successfully", SentryLevel.INFO)
CloudLogger.captureEvent("Too many sync failures", SentryLevel.WARNING)
```

---

## 🎨 What You'll See in Sentry Dashboard

### 1. **Issues Page** (Main dashboard)
```
🔴 Crashes: 2 in last 24h
⚠️  Non-fatal: 5 
👤 Users affected: 3
📈 Trend: ↓ 50% from yesterday

Recent Issues:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ NullPointerException
   [user_abc123] IMU sync failed
   Affected: 1 user, 3 events
   Last seen: 5 mins ago
   
❌ HTTP 401 Unauthorized  
   [user_def456] Token expired
   Affected: 2 users, 8 events
   Last seen: 2 hours ago
```

### 2. **Issue Detail Page**
```
🔍 NullPointerException in IMURepository.syncIMUData

Stack Trace:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMURepository.syncIMUData (IMURepository.kt:58)
DataSyncService.syncData (DataSyncService.kt:180)
...

Breadcrumbs (Last 100 events before crash):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
14:32:15 [user_abc123] 📊 START 2-min capture
14:32:18 [user_abc123] 🔘 LEFT_RESTAURANT_BUILDING  
14:32:45 [user_abc123] 📤 Syncing 1200 IMU points (2 batches)
14:32:46 [user_abc123] → /api/v1/imu-data/upload (700 points)
14:32:47 [user_abc123] ← HTTP 401  ← Last breadcrumb before crash
14:32:47 ❌ CRASH

User Context:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ID: user_abc123
Phone: +91-9876543210
Device: Samsung Galaxy S21
OS: Android 13
App Version: 1.0.0
```

### 3. **Performance Monitoring**
```
⚡ Average Response Times:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
/api/v1/button-presses/submit  →  125ms  ✅
/api/v1/imu-data/upload        →  2.3s   ⚠️  (slow!)
/api/v1/sessions/create        →  89ms   ✅
```

### 4. **Search & Filter**
```
Filter by:
- User ID: user_abc123
- Event Type: error, warning, info
- Tag: component=IMURepository
- Environment: production, development
- Time Range: Last 24h, 7d, 30d, custom

Search: "HTTP 401"  →  Shows all 401 errors with context
```

---

## 🔍 Advanced Features

### Custom Tags

```kotlin
CloudLogger.setContext("session_id", sessionId)
CloudLogger.setContext("data_source", "gps")
CloudLogger.setContext("network_type", "wifi")
```

### Performance Transactions

```kotlin
// Track slow operations
val transaction = Sentry.startTransaction("imu_data_sync", "task")
try {
    // ... sync IMU data ...
    transaction.status = SpanStatus.OK
} catch (e: Exception) {
    transaction.status = SpanStatus.INTERNAL_ERROR
    transaction.throwable = e
    throw e
} finally {
    transaction.finish()
}
```

### Custom Contexts

```kotlin
Sentry.configureScope { scope ->
    scope.setContexts("pending_data", mapOf(
        "buttons" to 5,
        "imu_points" to 1200
    ))
}
```

---

## 📊 Example Usage in Your Code

### DataSyncService.kt
```kotlin
private suspend fun syncData() {
    try {
        CloudLogger.d("DataSyncService", "$userIdPrefix Starting sync cycle")
        
        val sessionSyncSuccess = sessionRepository.syncUnsyncedData()
        if (!sessionSyncSuccess) {
            CloudLogger.w("DataSyncService", "$userIdPrefix Session sync failed")
        }
        
        val imuSyncSuccess = imuRepository.syncIMUData()
        if (!imuSyncSuccess) {
            CloudLogger.w("DataSyncService", "$userIdPrefix IMU sync failed")
        }
        
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            CloudLogger.captureEvent(
                "$userIdPrefix Too many consecutive failures",
                SentryLevel.ERROR
            )
        }
    } catch (e: Exception) {
        CloudLogger.e("DataSyncService", "$userIdPrefix Sync error", e)
    }
}
```

### IMURepository.kt
```kotlin
if (response.isSuccessful && responseBody?.success == true) {
    CloudLogger.d("IMURepository", "$userIdPrefix ✓ Batch synced")
} else {
    val errorMsg = "$userIdPrefix ❌ HTTP ${response.code()}"
    CloudLogger.e("IMURepository", errorMsg)
    
    // Capture as Sentry event for important errors
    if (response.code() == 401) {
        CloudLogger.captureEvent("Authentication failed during IMU sync", SentryLevel.ERROR)
    }
}
```

---

## 💰 Pricing

### Free Tier (Perfect for testing)
- ✅ 5,000 events/month
- ✅ Unlimited team members
- ✅ 90 days data retention
- ✅ All core features

### Team Plan ($26/month if you exceed free tier)
- ✅ 50,000 events/month
- ✅ 90 days retention
- ✅ Priority support

**Tip:** Each crash = 1 event. Each breadcrumb is free! So log as much as you want in breadcrumbs.

---

## 🎯 Best Practices

1. **Always set user context on login**
   ```kotlin
   CloudLogger.setUser(userId, phone)
   ```

2. **Use breadcrumbs liberally** (they're free!)
   ```kotlin
   CloudLogger.d("tag", "message")  // Creates breadcrumb
   ```

3. **Only capture events for important errors**
   ```kotlin
   CloudLogger.captureEvent("Critical error", SentryLevel.ERROR)  // Costs 1 event
   ```

4. **Filter sensitive data**
   ```kotlin
   options.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
       // Remove sensitive data
       event.contexts?.remove("auth_token")
       event
   }
   ```

5. **Use tags for filtering**
   ```kotlin
   CloudLogger.setContext("environment", "production")
   CloudLogger.setContext("user_type", "delivery_partner")
   ```

---

## 🔗 Useful Links

- Sentry Dashboard: https://sentry.io/
- Android SDK Docs: https://docs.sentry.io/platforms/android/
- Best Practices: https://docs.sentry.io/product/best-practices/

---

## 🚨 Next Steps

1. ✅ Sign up for Sentry account
2. ✅ Get your DSN
3. ✅ Replace `YOUR_SENTRY_DSN_HERE` in `IPSApplication.kt`
4. ✅ Sync Gradle
5. ✅ Build and run the app
6. ✅ Check Sentry dashboard for events

**Test it:** Force a crash to verify it's working:
```kotlin
throw RuntimeException("Test crash for Sentry")
```

