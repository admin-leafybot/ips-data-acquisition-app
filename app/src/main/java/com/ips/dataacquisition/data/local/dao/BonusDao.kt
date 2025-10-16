package com.ips.dataacquisition.data.local.dao

import androidx.room.*
import com.ips.dataacquisition.data.model.Bonus
import kotlinx.coroutines.flow.Flow

@Dao
interface BonusDao {
    @Query("SELECT * FROM bonuses ORDER BY date DESC")
    fun getAllBonuses(): Flow<List<Bonus>>
    
    @Query("SELECT * FROM bonuses WHERE date = :date")
    suspend fun getBonusByDate(date: String): Bonus?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBonus(bonus: Bonus)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBonuses(bonuses: List<Bonus>)
    
    @Query("DELETE FROM bonuses WHERE date = :date")
    suspend fun deleteBonus(date: String)
}

