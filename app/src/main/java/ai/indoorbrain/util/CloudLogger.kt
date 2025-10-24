package ai.indoorbrain.util

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.User

object CloudLogger {
    
    /**
     * Log a debug message to both logcat and Sentry breadcrumbs
     */
    fun d(tag: String, message: String) {
        // Local logcat
        Log.d(tag, message)
        
        // Sentry breadcrumb (shows in crash context)
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.message = message
            this.category = tag
            this.level = SentryLevel.DEBUG
        })
    }
    
    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.message = message
            this.category = tag
            this.level = SentryLevel.WARNING
        })
    }
    
    /**
     * Log an error message with optional exception
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Local logcat
        Log.e(tag, message, throwable)
        
        // Sentry breadcrumb
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.message = message
            this.category = tag
            this.level = SentryLevel.ERROR
        })
        
        // Capture exception if provided
        throwable?.let {
            Sentry.captureException(it) { scope ->
                scope.setTag("component", tag)
                scope.setContexts("error_message", message)
            }
        }
    }
    
    /**
     * Set the current user for tracking
     */
    fun setUser(userId: String, phone: String? = null) {
        Sentry.setUser(User().apply {
            id = userId
            username = phone
        })
    }
    
    /**
     * Clear user data (on logout)
     */
    fun clearUser() {
        Sentry.setUser(null)
    }
    
    /**
     * Add custom context for debugging
     */
    fun setContext(key: String, value: String) {
        Sentry.setTag(key, value)
    }
    
    /**
     * Capture a custom event (for important milestones)
     * This creates a standalone Issue in Sentry that you can see directly
     */
    fun captureEvent(message: String, level: SentryLevel = SentryLevel.INFO) {
        Sentry.captureMessage(message, level)
    }
    
    /**
     * Log as both breadcrumb AND standalone message
     * Use this for logs you want to see in Issues tab without waiting for a crash
     */
    fun logAndCapture(tag: String, message: String, level: SentryLevel = SentryLevel.INFO) {
        val fullMessage = "$tag: $message"
        
        // Local logcat
        when (level) {
            SentryLevel.ERROR -> Log.e(tag, message)
            SentryLevel.WARNING -> Log.w(tag, message)
            else -> Log.d(tag, message)
        }
        
        // Add as breadcrumb (for context in future crashes)
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.message = message
            this.category = tag
            this.level = level
        })
        
        // ALSO capture as standalone event (costs 1 event, but visible immediately)
        Sentry.captureMessage(fullMessage, level)
    }
}

