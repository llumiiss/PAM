package pl.pam.startproject.admin

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

data class AdminUserDto(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String?,
    val role: String,
    val isActive: Int,
    val attemptsCount: Long,
    val passwordPlain: String? = null,
)

data class AdminUsersResponseDto(
    val users: List<AdminUserDto>
)

interface AdminApi {
    @GET("api/admin/users")
    suspend fun getUsers(@Header("Authorization") authorization: String): AdminUsersResponseDto

    @DELETE("api/admin/users/{id}")
    suspend fun deleteUser(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long
    )

    @DELETE("api/admin/attempts/{id}")
    suspend fun deleteAttempt(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long
    )
}
