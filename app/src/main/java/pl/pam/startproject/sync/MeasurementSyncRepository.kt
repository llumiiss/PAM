package pl.pam.startproject.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.pam.startproject.auth.SessionManager
import pl.pam.startproject.BuildConfig
import pl.pam.startproject.data.MeasurementAttemptEntity
import pl.pam.startproject.data.PamDatabase
import pl.pam.startproject.data.SyncState
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Po zapisie lokalnym (Room) wysyła tę samą próbę do backendu → MySQL (Docker).
 * Błąd sieci nie cofa zapisu lokalnego — tylko log.
 */
class MeasurementSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = PamDatabase.get(appContext).measurementDao()
    private val sessionManager = SessionManager(appContext)

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

    data class SyncBatchResult(val pushed: Int, val failed: Int, val stillPending: Int)

    suspend fun syncPending(limit: Int = 50): SyncBatchResult = withContext(Dispatchers.IO) {
        val batch = dao.getPendingForSync(limit)
        var pushed = 0
        var failed = 0
        for (row in batch) {
            try {
                val body = MeasurementPushDto.fromEntity(row)
                val authHeader = sessionManager.getToken()?.let { "Bearer $it" }
                val res = api.pushAttempt(authHeader, body)
                dao.markSynced(
                    clientRecordId = row.clientRecordId,
                    remoteId = res.id,
                    syncedAt = System.currentTimeMillis()
                )
                pushed++
            } catch (e: Exception) {
                dao.markFailed(row.clientRecordId, e.message)
                failed++
                Log.e(TAG, "Sync failed for ${row.clientRecordId}", e)
            }
        }
        val stillPending = dao.countPendingSync()
        SyncBatchResult(pushed = pushed, failed = failed, stillPending = stillPending)
    }

    suspend fun insertPendingAndSync(row: MeasurementAttemptEntity) {
        withContext(Dispatchers.IO) {
            dao.insert(row.copy(syncState = SyncState.PENDING))
        }
        MeasurementSyncWorker.enqueue(appContext)
    }

    fun enqueueSyncNow() {
        MeasurementSyncWorker.enqueue(appContext)
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
