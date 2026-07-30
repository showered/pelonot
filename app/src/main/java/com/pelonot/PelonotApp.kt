package com.pelonot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pelonot.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PelonotApp : Application() {

    /**
     * Application-lifetime scope for start-up work that must outlive any
     * single screen. Seeding used to run inside a composable's
     * `LaunchedEffect`, so navigating away mid-seed cancelled it and left a
     * partially-populated class library.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createNotificationChannels()

        appScope.launch {
            runCatching { ServiceLocator.classTemplateSeeder.seedIfEmpty() }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?: return

        val workoutChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_WORKOUT,
            getString(R.string.channel_workout_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_workout_description)
            setShowBadge(false)
            enableVibration(false)
        }

        val syncChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_SYNC,
            getString(R.string.channel_sync_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_sync_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(workoutChannel, syncChannel))
    }

    companion object {
        const val NOTIFICATION_CHANNEL_WORKOUT = "workout_channel"
        const val NOTIFICATION_CHANNEL_SYNC = "sync_channel"
    }
}
