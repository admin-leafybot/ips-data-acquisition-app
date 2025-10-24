package com.ips.dataacquisition.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Helper class to handle battery optimization exemption requests
 * This is crucial for background services to work properly
 */
class BatteryOptimizationHelper {
    
    companion object {
        private const val TAG = "BatteryOptimizationHelper"
        
        /**
         * Check if battery optimization is disabled for this app
         */
        fun isBatteryOptimizationDisabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                return powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
            return true // For older versions, assume it's disabled
        }
        
        /**
         * Request battery optimization exemption (Android 6.0+)
         * This will open the system settings dialog
         */
        fun requestBatteryOptimizationExemption(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        // Add FLAG_ACTIVITY_NEW_TASK for non-Activity contexts
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to open battery optimization settings", e)
                    // Fallback: open general battery settings
                    openBatterySettings(context)
                }
            }
        }
        
        /**
         * Open general battery settings as fallback
         */
        private fun openBatterySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to open battery settings", e)
                // Last resort: open app settings
                openAppSettings(context)
            }
        }
        
        /**
         * Open app-specific settings where user can manually disable battery optimization
         */
        private fun openAppSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to open app settings", e)
            }
        }
        
        /**
         * Get user-friendly message about battery optimization
         */
        fun getBatteryOptimizationMessage(context: Context): String {
            return if (isBatteryOptimizationDisabled(context)) {
                "✅ Battery optimization is disabled - background sync will work properly"
            } else {
                "⚠️ Battery optimization is enabled - this may prevent background data sync. Please disable it for reliable operation."
            }
        }
        
        /**
         * Check if we need to show battery optimization request
         */
        fun shouldShowBatteryOptimizationRequest(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                   !isBatteryOptimizationDisabled(context)
        }
    }
}

/**
 * Extension for ComponentActivity to easily request battery optimization exemption
 */
fun ComponentActivity.requestBatteryOptimizationExemption() {
    BatteryOptimizationHelper.requestBatteryOptimizationExemption(this)
}

/**
 * Extension for Fragment to easily request battery optimization exemption
 */
fun Fragment.requestBatteryOptimizationExemption() {
    BatteryOptimizationHelper.requestBatteryOptimizationExemption(requireContext())
}

/**
 * ActivityResultLauncher for handling battery optimization result
 * Use this if you want to handle the result of the battery optimization request
 */
fun ComponentActivity.createBatteryOptimizationLauncher(
    onResult: (Boolean) -> Unit
): ActivityResultLauncher<Intent> {
    return registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if battery optimization is now disabled
        val isDisabled = BatteryOptimizationHelper.isBatteryOptimizationDisabled(this)
        onResult(isDisabled)
    }
}
