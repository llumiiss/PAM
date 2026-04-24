package pl.pam.startproject.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MeasurementAttemptEntity): Long

    @Query("SELECT * FROM measurement_attempts ORDER BY measured_at_epoch_ms DESC")
    fun observeAllByDateDesc(): Flow<List<MeasurementAttemptEntity>>

    @Query(
        "SELECT * FROM measurement_attempts WHERE owner_user_id = :ownerUserId ORDER BY measured_at_epoch_ms DESC"
    )
    fun observeByOwnerByDateDesc(ownerUserId: Long): Flow<List<MeasurementAttemptEntity>>

    @Query(
        "SELECT * FROM measurement_attempts WHERE measure_type = :measureType ORDER BY measured_at_epoch_ms DESC"
    )
    fun observeByTypeByDateDesc(measureType: String): Flow<List<MeasurementAttemptEntity>>

    @Query(
        "SELECT * FROM measurement_attempts WHERE sync_state IN ('pending', 'failed') ORDER BY measured_at_epoch_ms ASC LIMIT :limit"
    )
    suspend fun getPendingForSync(limit: Int): List<MeasurementAttemptEntity>

    @Query(
        "UPDATE measurement_attempts SET sync_state = 'synced', remote_id = :remoteId, synced_at_epoch_ms = :syncedAt, last_sync_error = NULL WHERE client_record_id = :clientRecordId"
    )
    suspend fun markSynced(clientRecordId: String, remoteId: Long?, syncedAt: Long)

    @Query(
        "UPDATE measurement_attempts SET sync_state = 'failed', sync_retries = sync_retries + 1, last_sync_error = :error WHERE client_record_id = :clientRecordId"
    )
    suspend fun markFailed(clientRecordId: String, error: String?)

    @Query("SELECT COUNT(*) FROM measurement_attempts WHERE sync_state IN ('pending', 'failed')")
    suspend fun countPendingSync(): Int

    @Query(
        """
        DELETE FROM measurement_attempts
        WHERE client_record_id IS NULL OR TRIM(client_record_id) = ''
           OR measured_at_epoch_ms IS NULL OR measured_at_epoch_ms <= 0
           OR measure_type IS NULL OR TRIM(measure_type) = ''
           OR mode_label IS NULL OR TRIM(mode_label) = ''
        """
    )
    suspend fun deleteCorruptedRows(): Int
}
