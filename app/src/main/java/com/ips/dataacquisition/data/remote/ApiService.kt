package com.ips.dataacquisition.data.remote

import com.ips.dataacquisition.data.model.Bonus
import com.ips.dataacquisition.data.model.Session
import com.ips.dataacquisition.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("sessions/create")
    suspend fun createSession(
        @Body request: SessionCreateRequest
    ): Response<ApiResponse<SessionCreateResponse>>
    
    @POST("sessions/close")
    suspend fun closeSession(
        @Body request: SessionUpdateRequest
    ): Response<ApiResponse<Unit>>
    
    @POST("button-presses")
    suspend fun submitButtonPress(
        @Body request: ButtonPressRequest
    ): Response<ApiResponse<Unit>>
    
    @POST("imu-data/upload")
    suspend fun uploadIMUData(
        @Body request: IMUDataUploadRequest
    ): Response<ApiResponse<Unit>>
    
    @GET("sessions")
    suspend fun getSessions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Session>>>
    
    @GET("bonuses")
    suspend fun getBonuses(
        @Query("start_date") startDate: String?,
        @Query("end_date") endDate: String?
    ): Response<ApiResponse<List<Bonus>>>
}

