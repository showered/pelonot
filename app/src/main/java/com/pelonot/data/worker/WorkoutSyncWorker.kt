package com.pelonot.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExponentialBackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.remote.SupabaseSyncRepository
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Background worker to sync completed workouts to Supabase.
 */
class WorkoutSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val database = AppDatabase.getDatabase(context)
    private val syncRepository = SupabaseSyncRepository.getInstance(context)
    
    override suspend fun doWork(): Result {
        return try {
            // Get the workout ID from input data
            val workoutId = inputData.getString("workout_id") ?: return Result.failure()
            
            // Get workout and metrics from database
            val workout = database.workoutDao().getWorkoutById(workoutId) ?: return Result.failure()
            val metrics = database.workoutMetricDao().getMetricsForWorkout(workoutId)
            
            // Compress metrics to JSON
            val metricsJson = Json.encodeToString(metrics)
            
            // Sync to Supabase
            syncRepository.syncWorkout(workout, metricsJson)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        fun enqueueSync(workoutId: String, context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(
                    androidx.work.Data.Builder()
                        .putString("workout_id", workoutId)
                        .build()
                )
                .setBackoffPolicy(ExponentialBackoffPolicy())
                .setBackoffDelay(10, TimeUnit.SECONDS)
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}