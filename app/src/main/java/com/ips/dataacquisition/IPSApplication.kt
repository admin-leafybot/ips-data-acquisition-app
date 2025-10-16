package com.ips.dataacquisition

import android.app.Application
import com.ips.dataacquisition.data.local.AppDatabase

class IPSApplication : Application() {
    
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    
    override fun onCreate() {
        super.onCreate()
    }
}

