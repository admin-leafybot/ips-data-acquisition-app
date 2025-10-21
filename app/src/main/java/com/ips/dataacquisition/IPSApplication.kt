package com.ips.dataacquisition

import android.app.Application
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.remote.RetrofitClientFactory

class IPSApplication : Application() {
    
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Retrofit with auth interceptor
        RetrofitClientFactory.initialize(this)
    }
}

