package com.ips.dataacquisition.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "bonuses")
data class Bonus(
    @PrimaryKey
    @ColumnInfo(name = "date")
    @SerializedName("Date")
    val date: String, // Format: YYYY-MM-DD
    
    @ColumnInfo(name = "amount")
    @SerializedName("Amount")
    val amount: Double,
    
    @ColumnInfo(name = "sessions_completed")
    @SerializedName("SessionsCompleted")
    val sessionsCompleted: Int,
    
    @ColumnInfo(name = "description")
    @SerializedName("Description")
    val description: String? = null
)

data class DailyBonus(
    @SerializedName("Date")
    val date: String,
    
    @SerializedName("BonusAmount")
    val bonusAmount: Double,
    
    @SerializedName("SessionsCount")
    val sessionsCount: Int
)

