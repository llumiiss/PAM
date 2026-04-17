package pl.pam.startproject.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.pam.startproject.BuildConfig
import pl.pam.startproject.data.MeasurementAttemptEntity
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Po zapisie lokalnym (Room) wysyła tę samą próbę do backendu → MySQL (Docker).
 * Błąd sieci nie cofa zapisu lokalnego — tylko log.
 */
class MeasurementSyncRepository(context: Context) {

    private val api: MeasurementSyncApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeasurementSyncApi::class.java)
    }

    suspend fun pushAfterLocalSave(row: MeasurementAttemptEntity) = withContext(Dispatchers.IO) {
        try {
            val body = MeasurementPushDto.fromEntity(row)
            val res = api.pushAttempt(body)
            Log.i(TAG, "Synced attempt to server, remote id=${res.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Sync to MySQL backend failed (local Room zapis bez zmian)", e)
        }
    }

    companion object {
        private const val TAG = "MeasurementSync"

        @Volatile
        private var instance: MeasurementSyncRepository? = null

        fun get(context: Context): MeasurementSyncRepository {
            return instance ?: synchronized(this) {
                instance ?: MeasurementSyncRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
