package com.ips.dataacquisition.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("data")
    val data: T?
)

data class SessionCreateRequest(
    @SerializedName("SessionId")
    val sessionId: String,
    
    @SerializedName("Timestamp")
    val timestamp: Long
)

data class SessionCreateResponse(
    @SerializedName("SessionId")
    val sessionId: String,
    
    @SerializedName("CreatedAt")
    val createdAt: Long
)

data class ButtonPressRequest(
    @SerializedName("SessionId")
    val sessionId: String,
    
    @SerializedName("Action")
    val action: String,
    
    @SerializedName("Timestamp")
    val timestamp: Long,
    
    @SerializedName("FloorIndex")
    val floorIndex: Int? = null
)

data class IMUDataUploadRequest(
    @SerializedName("SessionId")
    val sessionId: String?,
    
    @SerializedName("DataPoints")
    val dataPoints: List<com.ips.dataacquisition.data.model.IMUData>
)

data class SessionUpdateRequest(
    @SerializedName("SessionId")
    val sessionId: String,
    
    @SerializedName("EndTimestamp")
    val endTimestamp: Long
)

