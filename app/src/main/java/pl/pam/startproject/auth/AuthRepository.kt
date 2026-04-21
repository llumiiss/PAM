package pl.pam.startproject.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.pam.startproject.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthRepository(context: Context) {
    private val sessionManager = SessionManager(context)
    private val api: AuthApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): SessionUser = withContext(Dispatchers.IO) {
        val response = api.register(
            RegisterRequestDto(
                username = username.trim(),
                email = email.trim(),
                password = password,
                displayName = null
            )
        )
        val user = response.user.toSessionUser()
        sessionManager.saveSession(response.token, user)
        user
    }

    suspend fun login(emailOrUsername: String, password: String): SessionUser = withContext(Dispatchers.IO) {
        val response = api.login(LoginRequestDto(emailOrUsername = emailOrUsername.trim(), password = password))
        val user = response.user.toSessionUser()
        sessionManager.saveSession(response.token, user)
        user
    }

    private fun AuthUserDto.toSessionUser() = SessionUser(
        id = id,
        username = username,
        email = email,
        displayName = displayName,
        role = role
    )
}
