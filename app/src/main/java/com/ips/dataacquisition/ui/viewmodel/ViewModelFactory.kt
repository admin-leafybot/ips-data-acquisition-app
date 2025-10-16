package com.ips.dataacquisition.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.remote.RetrofitClient
import com.ips.dataacquisition.data.repository.BonusRepository
import com.ips.dataacquisition.data.repository.SessionRepository

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val apiService = RetrofitClient.apiService
        
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                val sessionRepository = SessionRepository(
                    database.sessionDao(),
                    database.buttonPressDao(),
                    apiService
                )
                HomeViewModel(sessionRepository) as T
            }
            modelClass.isAssignableFrom(PaymentStatusViewModel::class.java) -> {
                val sessionRepository = SessionRepository(
                    database.sessionDao(),
                    database.buttonPressDao(),
                    apiService
                )
                PaymentStatusViewModel(sessionRepository) as T
            }
            modelClass.isAssignableFrom(BonusViewModel::class.java) -> {
                val bonusRepository = BonusRepository(
                    database.bonusDao(),
                    apiService
                )
                BonusViewModel(bonusRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

