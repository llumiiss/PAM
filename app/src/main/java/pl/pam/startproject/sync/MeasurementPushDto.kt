package pl.pam.startproject.sync

import pl.pam.startproject.data.MeasurementAttemptEntity

/** JSON wysyłany do POST /api/attempts (camelCase, Gson). */
data class MeasurementPushDto(
    val measuredAtEpochMs: Long,
    val measureType: String,
    val modeLabel: String,
    val startStrategy: String?,
    val maxSpeedKmh: Double,
    val durationMs: Long,
    val distanceM: Double,
) {
    companion object {
        fun fromEntity(e: MeasurementAttemptEntity) = MeasurementPushDto(
            measuredAtEpochMs = e.measuredAtEpochMs,
            measureType = e.measureType,
            modeLabel = e.modeLabel,
            startStrategy = e.startStrategy,
            maxSpeedKmh = e.maxSpeedKmh,
            durationMs = e.durationMs,
            distanceM = e.distanceM,
        )
    }
}
