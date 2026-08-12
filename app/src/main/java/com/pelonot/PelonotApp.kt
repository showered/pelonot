package com.pelonot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pelonot.data.worker.WorkoutSyncWorker
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.cloud.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
            runCatching { ServiceLocator.classTemplateSeeder.syncWithBundledLibrary() }
        }

        backfillPowerProvenance()

        applyTelemetrySource()
        drainAnyBacklog()
    }

    /**
     * Gives any ride that has none a `power_provenance` (PLAN 23.4.12).
     *
     * **One `UPDATE`, at launch, whole-tablet.** It is here rather than on the
     * screen that needs it because there is no such screen: six queries gate on
     * that column — the household board, the per-class board, *your usual*, the
     * *best you've ridden it* verdict — and they are asked from the dashboard,
     * the class detail, the post-ride summary and the race. A ride the pass has
     * not reached is absent from all of them with nothing visibly wrong, which
     * is the exact defect 23.4.12 exists to close, so the pass runs before any
     * of those screens can be opened rather than lazily behind one of them.
     *
     * That is the opposite choice to 16.3.3a's per-rider bests backfill, and the
     * difference is what each one costs: this is a single statement over
     * `workouts` that matches nothing after the first launch, while that one
     * walks a sample series per ride and only *Your FTP* ever needs the answer.
     *
     * `runCatching` for the seeder's reason above — a start-up task must not be
     * able to take the process down — and the fence
     * `WorkoutDao.completeRidesWithoutProvenance` is what a test asserts against
     * instead of trusting this to have run.
     */
    private fun backfillPowerProvenance() {
        appScope.launch {
            runCatching { ServiceLocator.workoutRepository.backfillPowerProvenance() }
        }
    }

    /**
     * Sends up whatever is still waiting, once, on launch (PLAN 14.2.10).
     *
     * **Nothing did this before, and the gap is worse than it sounds.** The
     * backlog had exactly two triggers — finishing a ride, and signing in — so
     * 14.2.6's promise that "the next ride the rider finishes sweeps it up"
     * was the *only* way a stranded ride ever moved. That is precisely wrong at
     * the one moment it matters most: the first sign-in backfill is both the
     * largest batch the app will ever send and the one most likely to meet a
     * flat network, and if it fails the rider's whole history then sits there
     * until they ride again. A rider who signs in on the sofa and rides on
     * Saturday has four days of believing they are backed up.
     *
     * Found by driving it rather than by reading it: the backfill failed on a
     * bad row, the row was fixed, and **nothing on earth would have retried**.
     *
     * It is cheap and it is not a network call. `enqueueIfAllowed` asks the
     * gate first, so an offline rider schedules nothing at all (rule 1); the
     * job it does schedule is network-constrained and unique per profile, so
     * launching the app four times does not queue four drains.
     */
    private fun drainAnyBacklog() {
        appScope.launch {
            runCatching {
                // **Waits for the session rather than racing it**, and the
                // first version of this did race it: enqueuing straight from
                // `onCreate` asked the gate before the SDK had finished reading
                // the stored session off disk, so it answered "no account on
                // profile 1" — correctly, for that instant — and the launch
                // drain never ran at all. It is the same shape as the defect in
                // 21.1.3 and 8.3d.4: the code is right about what it wants and
                // wrong about when it can have it.
                //
                // Driving it off the session flow fixes the race and buys the
                // other half for free: a rider who signs in *later* in the
                // session gets their backfill from the same collector, so
                // there is one trigger rather than two that drift.
                ServiceLocator.accountRepository.accountState
                    .filterIsInstance<AccountState.SignedIn>()
                    .distinctUntilChangedBy { it.accountId }
                    .collect {
                        val profileId =
                            ServiceLocator.settingsRepository.settings.first().lastProfileId
                        WorkoutSyncWorker.enqueueIfAllowed(
                            context = this@PelonotApp,
                            workoutId = "session",
                            userId = profileId
                        )
                    }
            }
        }
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
