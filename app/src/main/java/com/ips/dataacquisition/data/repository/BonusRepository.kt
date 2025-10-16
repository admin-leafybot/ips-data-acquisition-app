package com.ips.dataacquisition.data.repository

import com.ips.dataacquisition.data.local.dao.BonusDao
import com.ips.dataacquisition.data.model.Bonus
import com.ips.dataacquisition.data.remote.ApiService
import kotlinx.coroutines.flow.Flow

class BonusRepository(
    private val bonusDao: BonusDao,
    private val apiService: ApiService
) {
    
    fun getAllBonuses(): Flow<List<Bonus>> = bonusDao.getAllBonuses()
    
    suspend fun fetchBonusesFromServer(startDate: String? = null, endDate: String? = null) {
        try {
            val response = apiService.getBonuses(startDate, endDate)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { bonuses ->
                    bonusDao.insertBonuses(bonuses)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

