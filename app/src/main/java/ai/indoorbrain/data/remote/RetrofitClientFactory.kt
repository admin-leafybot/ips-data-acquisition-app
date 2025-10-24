package ai.indoorbrain.data.remote

import android.content.Context
import ai.indoorbrain.data.local.PreferencesManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClientFactory {
    
    private const val BASE_URL = "http://ida-api.leafybot.com:90/api/v1/"
    
    private lateinit var authInterceptor: AuthInterceptor
    private lateinit var apiServiceInstance: ApiService
    private var isInitialized = false
    
    fun initialize(context: Context) {
        if (isInitialized) return
        
        val preferencesManager = PreferencesManager(context)
        authInterceptor = AuthInterceptor(preferencesManager)
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // GZIP compression interceptor
        val gzipInterceptor = Interceptor { chain ->
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
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)  // Add auth token to requests
            .addInterceptor(gzipInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiServiceInstance = retrofit.create(ApiService::class.java)
        isInitialized = true
    }
    
    val apiService: ApiService
        get() {
            check(isInitialized) { "RetrofitClientFactory must be initialized first. Call initialize(context) in Application.onCreate()" }
            return apiServiceInstance
        }
    
    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = -1L
            
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }
}

