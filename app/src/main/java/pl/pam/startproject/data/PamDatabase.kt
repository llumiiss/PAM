package pl.pam.startproject.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MeasurementAttemptEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PamDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var instance: PamDatabase? = null

        fun get(context: Context): PamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PamDatabase::class.java,
                    "pam_measurements.db",
                ).build().also { instance = it }
            }
        }
    }
}
