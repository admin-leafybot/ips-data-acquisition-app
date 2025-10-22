package com.ips.dataacquisition.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ips.dataacquisition.data.local.AppDatabase
import com.ips.dataacquisition.data.local.PreferencesManager
import com.ips.dataacquisition.data.remote.RetrofitClientFactory
import com.ips.dataacquisition.data.repository.AppRepository
import com.ips.dataacquisition.data.repository.AuthRepository
import com.ips.dataacquisition.data.repository.BonusRepository
import com.ips.dataacquisition.data.repository.SessionRepository

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val apiService = RetrofitClientFactory.apiService
        val preferencesManager = PreferencesManager(context)
        
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                val sessionRepository = SessionRepository(
                    database.sessionDao(),
                    database.buttonPressDao(),
                    apiService,
                    context
                )
                HomeViewModel(context, sessionRepository, preferencesManager) as T
            }
            modelClass.isAssignableFrom(PaymentStatusViewModel::class.java) -> {
                val sessionRepository = SessionRepository(
                    database.sessionDao(),
                    database.buttonPressDao(),
                    apiService,
                    context
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
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(context, preferencesManager) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                val authRepository = AuthRepository(apiService, preferencesManager)
                AuthViewModel(authRepository, preferencesManager) as T
            }
            modelClass.isAssignableFrom(AppVersionViewModel::class.java) -> {
                val appRepository = AppRepository(apiService)
                AppVersionViewModel(appRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

