package pl.pam.startproject.gps

import android.location.Location

/** Powyżej tej dokładności (m) mniej ufamy prędkości z [Location.getSpeed]. */
private const val MAX_TRUST_ACCURACY_M = 35f

/**
 * Łączy prędkość z GPS z estymacją z dystansu / Δt między fixami (mniej szumu przy słabym sygnale).
 */
object GpsSpeedEstimator {

    fun estimateKmh(previous: Location?, latest: Location): Float {
        val gpsMs = if (latest.hasSpeed() && latest.speed >= 0f) latest.speed else null
        val derivedMs = derivedSpeedMs(previous, latest)
        val acc = if (latest.hasAccuracy()) latest.accuracy else null
        val trustGpsSpeed = acc == null || acc <= MAX_TRUST_ACCURACY_M

        val gpsKmh = gpsMs?.let { it * 3.6f }
        val derKmh = derivedMs?.let { it * 3.6f }

        return when {
            gpsKmh != null && derKmh != null && trustGpsSpeed -> 0.55f * gpsKmh + 0.45f * derKmh
            gpsKmh != null && trustGpsSpeed -> gpsKmh
            derKmh != null -> derKmh
            gpsKmh != null -> gpsKmh
            else -> 0f
        }
    }

    private fun derivedSpeedMs(previous: Location?, latest: Location): Float? {
        if (previous == null) return null
        val dtMs = latest.time - previous.time
        if (dtMs < 80L || dtMs > 12_000L) return null
        val dtSec = dtMs / 1000.0
        if (dtSec <= 0.0) return null
        return (previous.distanceTo(latest) / dtSec).toFloat().coerceIn(0f, 100f)
    }
}
