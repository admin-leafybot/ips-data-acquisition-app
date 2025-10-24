package ai.indoorbrain.data.model

import com.google.gson.annotations.SerializedName

// Login Request (Note: Backend expects PascalCase for requests)
data class LoginRequest(
    @SerializedName("Phone")
    val phone: String,
    
    @SerializedName("Password")
    val password: String
)

// Login Response
data class LoginResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("token")
    val token: String?,
    
    @SerializedName("refreshToken")
    val refreshToken: String?,
    
    @SerializedName("userId")
    val userId: String?,
    
    @SerializedName("fullName")
    val fullName: String?,
    
    @SerializedName("expiresAt")
    val expiresAt: String?,  // ISO 8601 datetime
    
    @SerializedName("expiresIn")
    val expiresIn: Int?  // Seconds until expiration
)

// Signup Request (Note: Backend expects PascalCase for requests, camelCase for responses)
data class SignupRequest(
    @SerializedName("Phone")
    val phone: String,
    
    @SerializedName("Password")
    val password: String,
    
    @SerializedName("FullName")
    val fullName: String
)

// Signup Response
data class SignupResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("userId")
    val userId: String?
)

// Refresh Token Request (Note: Backend expects PascalCase for requests)
data class RefreshTokenRequest(
    @SerializedName("RefreshToken")
    val refreshToken: String
)

// Refresh Token Response
data class RefreshTokenResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("token")
    val token: String?,
    
    @SerializedName("refreshToken")
    val refreshToken: String?,
    
    @SerializedName("expiresAt")
    val expiresAt: String?,
    
    @SerializedName("expiresIn")
    val expiresIn: Int?
)

// User data stored locally
data class User(
    val userId: String,
    val phone: String,
    val fullName: String,
    val token: String,
    val refreshToken: String,
    val tokenExpiresAt: Long  // Unix timestamp in milliseconds
)

// App Version Check
data class AppVersionRequest(
    @SerializedName("VersionName")
    val versionName: String
)

data class AppVersionResponse(
    @SerializedName("isActive")
    val isActive: Boolean,
    
    @SerializedName("message")
    val message: String?
)

