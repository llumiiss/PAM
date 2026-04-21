package pl.pam.startproject.sync

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MeasurementSyncApi {

    @POST("api/attempts")
    suspend fun pushAttempt(
        @Header("Authorization") authorization: String?,
        @Body body: MeasurementPushDto
    ): PushResponseBody
}

data class PushResponseBody(val id: Long? = null)
