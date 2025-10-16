package com.ips.dataacquisition.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    @SerializedName("SessionId")
    val sessionId: String,
    
    @ColumnInfo(name = "start_timestamp")
    @SerializedName("StartTimestamp")
    val startTimestamp: Long,
    
    @ColumnInfo(name = "end_timestamp")
    @SerializedName("EndTimestamp")
    val endTimestamp: Long? = null,
    
    @ColumnInfo(name = "is_synced")
    @SerializedName("IsSynced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "status")
    @SerializedName("Status")
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    
    @ColumnInfo(name = "payment_status")
    @SerializedName("PaymentStatus")
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    
    @ColumnInfo(name = "remarks")
    @SerializedName("Remarks")
    val remarks: String? = null,
    
    @ColumnInfo(name = "bonus_amount")
    @SerializedName("BonusAmount")
    val bonusAmount: Double? = null
)

enum class SessionStatus {
    @SerializedName("in_progress")
    IN_PROGRESS,
    
    @SerializedName("completed")
    COMPLETED,
    
    @SerializedName("approved")
    APPROVED,
    
    @SerializedName("rejected")
    REJECTED
}

enum class PaymentStatus {
    @SerializedName("unpaid")
    UNPAID,
    
    @SerializedName("paid")
    PAID
}

