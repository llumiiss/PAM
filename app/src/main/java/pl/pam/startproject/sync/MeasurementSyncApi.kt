package pl.pam.startproject.sync

import retrofit2.http.Body
import retrofit2.http.POST

interface MeasurementSyncApi {

    @POST("api/attempts")
    suspend fun pushAttempt(@Body body: MeasurementPushDto): PushResponseBody
}

data class PushResponseBody(val id: Long? = null)
