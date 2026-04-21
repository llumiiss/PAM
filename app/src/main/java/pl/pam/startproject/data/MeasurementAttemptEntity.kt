package pl.pam.startproject.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurement_attempts",
    indices = [
        Index(value = ["client_record_id"], unique = true),
        Index(value = ["sync_state"]),
        Index(value = ["measured_at_epoch_ms"])
    ]
)
data class MeasurementAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_record_id") val clientRecordId: String,
    @ColumnInfo(name = "measured_at_epoch_ms") val measuredAtEpochMs: Long,
    @ColumnInfo(name = "measure_type") val measureType: String,
    @ColumnInfo(name = "mode_label") val modeLabel: String,
    @ColumnInfo(name = "start_strategy") val startStrategy: String?,
    @ColumnInfo(name = "max_speed_kmh") val maxSpeedKmh: Double,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    /** JSON [[tMs,vKmh],…] — profil prędkości z pomiaru (estymacja GPS + dystans/Δt). */
    @ColumnInfo(name = "speed_profile_json") val speedProfileJson: String? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SyncState.PENDING,
    @ColumnInfo(name = "sync_retries") val syncRetries: Int = 0,
    @ColumnInfo(name = "last_sync_error") val lastSyncError: String? = null,
    @ColumnInfo(name = "remote_id") val remoteId: Long? = null,
    @ColumnInfo(name = "synced_at_epoch_ms") val syncedAtEpochMs: Long? = null,
)
