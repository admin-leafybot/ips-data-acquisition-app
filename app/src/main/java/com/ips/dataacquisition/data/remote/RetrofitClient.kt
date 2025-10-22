package com.ips.dataacquisition.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response as OkHttpResponse
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

object RetrofitClient {
    
    // TODO: Replace with your actual backend URL
    private const val BASE_URL = "http://ida-api.leafybot.com:90/api/v1/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    // GZIP compression interceptor to reduce payload size
    private val gzipInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        if (originalRequest.body == null || originalRequest.header("Content-Encoding") != null) {
            return@Interceptor chain.proceed(originalRequest)
        }
        
        val compressedRequest = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, gzip(originalRequest.body!!))
            .build()
        
        chain.proceed(compressedRequest)
    }
    
    private fun gzip(body: okhttp3.RequestBody): okhttp3.RequestBody {
        return object : okhttp3.RequestBody() {
            override fun contentType() = body.contentType()
            
            override fun contentLength() = -1L // We don't know the compressed length in advance
            
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(gzipInterceptor) // GZIP compression enabled (backend supports it)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS) // Increased for large IMU batches
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

