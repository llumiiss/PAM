package pl.pam.startproject.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_attempts")
data class MeasurementAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "measured_at_epoch_ms") val measuredAtEpochMs: Long,
    @ColumnInfo(name = "measure_type") val measureType: String,
    @ColumnInfo(name = "mode_label") val modeLabel: String,
    @ColumnInfo(name = "start_strategy") val startStrategy: String?,
    @ColumnInfo(name = "max_speed_kmh") val maxSpeedKmh: Double,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
)
