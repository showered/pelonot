package com.pelonot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pelonot.data.local.dao.ActiveRideRivalDao
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.FtpHistoryDao
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.dao.WorkoutPowerBestDao
import com.pelonot.data.local.entity.ActiveRideRivalEntity
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.FtpHistoryEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.local.entity.WorkoutPowerBestEntity

@Database(
    entities = [
        UserEntity::class,
        ClassTemplateEntity::class,
        WorkoutEntity::class,
        WorkoutMetricEntity::class,
        FtpHistoryEntity::class,
        ActiveRideRivalEntity::class,
        WorkoutPowerBestEntity::class
    ],
    version = 22,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun classTemplateDao(): ClassTemplateDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutMetricDao(): WorkoutMetricDao
    abstract fun ftpHistoryDao(): FtpHistoryDao
    abstract fun activeRideRivalDao(): ActiveRideRivalDao
    abstract fun workoutPowerBestDao(): WorkoutPowerBestDao

    /**
     * The schema version this database is actually open at (12.4.4).
     *
     * Asked of the open file rather than restated as a constant beside the
     * annotation. There *was* a constant here, kept equal to
     * `@Database(version = …)` by a comment saying so, and it drifted the first
     * time somebody bumped the version without reading it: at 16 against a
     * database at 17, every backup this build wrote was refused on restore as
     * *"made by a newer version of Pelonot"*. The number has one source now.
     */
    fun schemaVersion(): Int = openHelper.readableDatabase.version

    companion object {

        private const val DATABASE_NAME = "pelonot_database"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // Explicit migrations, never a destructive fallback: a rider's
                // training history is the one thing in this app that cannot be
                // regenerated. See [AppMigrations] for the rule that goes with
                // this — a schema change without a migration now fails at open
                // rather than quietly emptying the database.
                .addMigrations(*AppMigrations.ALL)
                // A downgrade means someone has installed an older APK over a
                // newer one, which only happens on a development device and
                // which no forward migration can describe.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
