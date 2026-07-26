package com.pelonot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PelonotApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_WORKOUT = "workout_channel"
        const val NOTIFICATION_CHANNEL_SYNC = "sync_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val workoutChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_WORKOUT,
                "Workout Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while a workout is active"
                setShowBadge(false)
            }

            val syncChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SYNC,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background sync notifications"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(workoutChannel)
            manager.createNotificationChannel(syncChannel)
        }
    }
}