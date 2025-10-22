package com.ips.dataacquisition

import android.app.Application
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.remote.RetrofitClientFactory
import io.sentry.android.core.SentryAndroid
import io.sentry.SentryOptions

class IPSApplication : Application() {
    
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Sentry for crash reporting and logging
        SentryAndroid.init(this) { options ->
            // TODO: Replace with your actual Sentry DSN from https://sentry.io
            options.dsn = "https://76d30558f13a4d9a5fe371e564373e28@o4510234097614848.ingest.us.sentry.io/4510234099384320"
            
            // Set the environment
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            
            // Set the release version
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
            
            // Enable breadcrumbs for logging
            options.isEnableUserInteractionBreadcrumbs = true
            options.isEnableActivityLifecycleBreadcrumbs = true
            options.isEnableAppLifecycleBreadcrumbs = true
            
            // Sample rate for performance monitoring (0.0 to 1.0)
            options.tracesSampleRate = 1.0 // 100% in development, reduce to 0.1-0.2 in production
            
            // Enable debug logging in development
            options.isDebug = BuildConfig.DEBUG
            
            // Before send callback - can modify or filter events
            options.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                // You can filter or modify events here
                // Return null to drop the event
                event
            }
        }
        
        // Initialize Retrofit with auth interceptor
        RetrofitClientFactory.initialize(this)
    }
}

