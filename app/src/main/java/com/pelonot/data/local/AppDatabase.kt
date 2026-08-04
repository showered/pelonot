package com.pelonot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.FtpHistoryDao
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.FtpHistoryEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity

@Database(
    entities = [
        UserEntity::class,
        ClassTemplateEntity::class,
        WorkoutEntity::class,
        WorkoutMetricEntity::class,
        FtpHistoryEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun classTemplateDao(): ClassTemplateDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutMetricDao(): WorkoutMetricDao
    abstract fun ftpHistoryDao(): FtpHistoryDao

    companion object {

        /**
         * Kept beside the `@Database(version = …)` above and equal to it. A
         * restore has to refuse a backup from a newer schema (12.4.4), and
         * that comparison needs the number at runtime.
         */
        const val SCHEMA_VERSION = 13

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
