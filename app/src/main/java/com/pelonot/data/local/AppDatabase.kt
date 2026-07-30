package com.pelonot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity

@Database(
    entities = [
        UserEntity::class,
        ClassTemplateEntity::class,
        WorkoutEntity::class,
        WorkoutMetricEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun classTemplateDao(): ClassTemplateDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutMetricDao(): WorkoutMetricDao

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
                // Pre-release: no installs hold data worth preserving, so
                // schema changes recreate the database rather than carrying a
                // migration each time. Replace this with explicit migrations
                // before the first real user installs a build — after that,
                // dropping it silently deletes their entire training history.
                .fallbackToDestructiveMigration()
                .build()
    }
}
