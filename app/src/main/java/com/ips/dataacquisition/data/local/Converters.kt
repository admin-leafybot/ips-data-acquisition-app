package com.ips.dataacquisition.data.local

import androidx.room.TypeConverter
import com.ips.dataacquisition.data.model.PaymentStatus
import com.ips.dataacquisition.data.model.SessionStatus

class Converters {
    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus {
        return try {
            SessionStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SessionStatus.IN_PROGRESS
        }
    }
    
    @TypeConverter
    fun fromPaymentStatus(value: PaymentStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toPaymentStatus(value: String): PaymentStatus {
        return try {
            PaymentStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PaymentStatus.UNPAID
        }
    }
}

