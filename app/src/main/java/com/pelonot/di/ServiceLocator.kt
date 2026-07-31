package com.pelonot.di

import android.content.Context
import com.pelonot.data.audio.VolumeController
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.ClassTemplateSeeder
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.sensor.BleHeartRateManager
import com.pelonot.data.sensor.PelotonSensorServiceSource
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.data.sensor.SensorSource
import com.pelonot.data.sensor.SerialSensorSource
import com.pelonot.data.sensor.SimulatedSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency graph, initialised once from
 * [com.pelonot.PelonotApp.onCreate].
 *
 * Deliberately not Hilt: this is a single-module app with roughly a dozen
 * collaborators, and a hand-written locator keeps the build free of another
 * annotation processor. It does give every component a single owner, which
 * the previous code lacked — services, composables and the overlay each
 * reached for their own `getInstance(context)` singletons, so nothing could be
 * substituted in a test.
 */
object ServiceLocator {

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    private val context: Context
        get() = requireNotNull(applicationContext) {
            "ServiceLocator.init() must be called from PelonotApp.onCreate()"
        }

    /**
     * Work that has to finish even though the thing that asked for it is gone.
     *
     * The case that needs it is the held-back delete in `HistoryViewModel`: the
     * rider deletes a ride, navigates away without undoing, and the delete must
     * still land. `viewModelScope` is already cancelled by the time `onCleared`
     * runs, so a `launch` there never executes its body.
     */
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /**
     * Shared between Settings and the HUD deliberately: both move the same
     * system value, and two controllers would each hold their own stale idea
     * of where it is.
     */
    val volumeController: VolumeController by lazy { VolumeController(context) }

    val syncRepository: SupabaseSyncRepository by lazy {
        SupabaseSyncRepository(enabled = { cloudSyncEnabled() })
    }

    val userRepository: UserRepository by lazy {
        UserRepository(database.userDao(), syncRepository)
    }

    val classRepository: ClassRepository by lazy {
        ClassRepository(database.classTemplateDao())
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(database.workoutDao(), database.workoutMetricDao())
    }

    val classTemplateSeeder: ClassTemplateSeeder by lazy {
        ClassTemplateSeeder(context, database.classTemplateDao(), syncRepository)
    }

    val sensorRepository: SensorRepository by lazy {
        SensorRepository(
            hardwareSource = hardwareSensorSource(),
            simulatedSource = SimulatedSensorSource(),
            bleHeartRateManager = BleHeartRateManager(context)
        )
    }

    /**
     * The bike's sensor board by whichever route this tablet allows.
     *
     * On a stock bike the UART belongs to `system` and Peloton's own service
     * is the only way in. Reading the character device directly needs root, so
     * [SerialSensorSource] is the fallback for a jailbroken tablet rather than
     * the default it used to be.
     */
    private fun hardwareSensorSource(): SensorSource {
        val serviceSource = PelotonSensorServiceSource(context)
        return if (serviceSource.isAvailable()) serviceSource else SerialSensorSource()
    }

    /**
     * Read synchronously because it is consulted from `SupabaseSyncRepository`,
     * which is called from suspending code that already runs off the main
     * thread — including WorkManager, where there is no lifecycle scope to
     * collect a flow in. The read is a single DataStore lookup.
     */
    private fun cloudSyncEnabled(): Boolean = runCatching {
        runBlocking { settingsRepository.settings.first().cloudSyncEnabled }
    }.getOrDefault(true)

    /** Test seam: replaces the graph so instrumented tests can inject fakes. */
    fun resetForTesting() {
        applicationContext = null
    }
}
