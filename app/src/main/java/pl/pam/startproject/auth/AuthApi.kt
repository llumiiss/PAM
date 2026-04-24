package pl.pam.startproject.auth

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

data class AuthUserDto(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String?,
    val role: String
)

data class AuthResponseDto(
    val token: String,
    val user: AuthUserDto
)

data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String?
)

data class LoginRequestDto(
    val emailOrUsername: String,
    val password: String
)

data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthResponseDto

    @PUT("api/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body body: ChangePasswordRequestDto
    )
}
