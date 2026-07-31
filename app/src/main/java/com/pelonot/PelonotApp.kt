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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

        applyTelemetrySource()
    }

    /**
     * Keeps `SensorRepository`'s mode in step with the rider's preference
     * (2.4.6).
     *
     * `SensorRepository.setMode` had exactly one caller — `SettingsViewModel`,
     * on tap — so the persisted choice was applied to the *object* only in the
     * session where it was made. On every launch after that the repository was
     * back to its default of `Auto`, while Settings went on drawing the chip
     * the rider had chosen, because Settings reads DataStore and the pipeline
     * reads its own field. Chosen "Hardware" and restarted the app? The setting
     * whose entire purpose is that **a ride never records fabricated numbers**
     * was silently back to the mode that falls back to simulated telemetry.
     *
     * Collected here rather than re-applied at ride start, so the one place
     * that owns the pipeline is the one place that decides what feeds it, for
     * the life of the process and not the life of a screen.
     */
    private fun applyTelemetrySource() {
        appScope.launch {
            ServiceLocator.settingsRepository.settings
                .map { it.sensorMode }
                .distinctUntilChanged()
                .collect { ServiceLocator.sensorRepository.setMode(it) }
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
