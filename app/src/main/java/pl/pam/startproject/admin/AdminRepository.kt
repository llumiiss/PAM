package pl.pam.startproject.admin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.pam.startproject.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AdminRepository(context: Context) {
    private val api: AdminApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApi::class.java)
    }

    suspend fun loadUsers(token: String): List<AdminUserDto> = withContext(Dispatchers.IO) {
        api.getUsers("Bearer $token").users
    }

    suspend fun deleteUser(token: String, userId: Long) = withContext(Dispatchers.IO) {
        api.deleteUser("Bearer $token", userId)
    }

    suspend fun deleteAttempt(token: String, attemptId: Long) = withContext(Dispatchers.IO) {
        api.deleteAttempt("Bearer $token", attemptId)
    }
}
