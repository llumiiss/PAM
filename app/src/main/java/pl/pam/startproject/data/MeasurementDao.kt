package pl.pam.startproject.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(entity: MeasurementAttemptEntity): Long

    @Query("SELECT * FROM measurement_attempts ORDER BY measured_at_epoch_ms DESC")
    fun observeAllByDateDesc(): Flow<List<MeasurementAttemptEntity>>

    @Query(
        "SELECT * FROM measurement_attempts WHERE measure_type = :measureType ORDER BY measured_at_epoch_ms DESC"
    )
    fun observeByTypeByDateDesc(measureType: String): Flow<List<MeasurementAttemptEntity>>
}
