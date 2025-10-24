package ai.indoorbrain.data.repository

import ai.indoorbrain.data.local.dao.BonusDao
import ai.indoorbrain.data.model.Bonus
import ai.indoorbrain.data.remote.ApiService
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

