package com.pelonot.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pelonot.data.sensor.SensorMode
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.UnitSystem
import com.pelonot.domain.retention.RetentionAge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** How the app chooses between light and dark. */
enum class ThemeMode { System, Light, Dark }

/**
 * User preferences that are not part of a rider profile.
 *
 * These previously lived in `remember { mutableStateOf(...) }` inside the
 * navigation graph, so the chosen theme and FTP were discarded on rotation,
 * on process death, and whenever the user navigated back past the screen that
 * happened to own the state.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val useDynamicColor: Boolean = false,
    val sensorMode: SensorMode = SensorMode.Auto,
    val lastProfileId: Int? = null,
    val heartRateDeviceAddress: String? = null,
    val cloudSyncEnabled: Boolean = true,

    /** How loudly the ride is allowed to interrupt. */
    val coachStyle: CoachStyle = CoachStyle.DEFAULT,

    /**
     * How loud the spoken coach is, 0..1, independent of the media volume.
     *
     * Persisted because a rider who turned the coach down did not mean "until
     * the next ride" (11.5.6). The media volume is *not* stored here — that is
     * a system value, and the system already remembers it.
     */
    val coachVolume: Float = DEFAULT_COACH_VOLUME,

    /** Whether the floating HUD is raised over other apps during a ride. */
    val hudEnabled: Boolean = true,

    /** Which screen edge the HUD is pinned to. */
    val hudDock: HudDock = HudDock.DEFAULT,

    /**
     * How solid the HUD's panel is, 0..1 (11.1b.1).
     *
     * Set once in Settings rather than fiddled with mid-ride. Stored as the
     * rider left it and floored at the point of use, where the strip's own
     * colours are known — see `HudMinimumOpacity`, which derives the floor
     * from a contrast calculation rather than a guess.
     */
    val hudOpacity: Float = HudOpacity.DEFAULT,

    /**
     * Miles or kilometres. Display only — see [UnitSystem].
     *
     * The default is derived from the device locale rather than fixed at
     * metric, so a UK or US rider is not shown kilometres on a bike whose own
     * display is in miles.
     */
    val unitSystem: UnitSystem = UnitSystem.fromLocale(),

    /**
     * When the rider last backed up, or last said "not now" — whichever is
     * later (23.3.1).
     *
     * **One mark for both**, because the reminder only ever asks one question:
     * how much riding has happened that a backup would not have. A dismissal
     * answers it honestly by moving the line to now — the rides already
     * recorded stop asking, and the next ten earn the next reminder. Null means
     * neither has ever happened, so the count runs from the first ride.
     */
    val backupMarkAtMs: Long? = null,

    /** Distinguishes "nothing since your backup" from "no backup at all". */
    val hasEverBackedUp: Boolean = false,

    /**
     * When the cloud last accepted a ride, or null if it never has (PLAN
     * 14.2.3).
     *
     * Distinct from [backupMarkAtMs], which is about the *local file* backup of
     * 12.4.4 and is a different promise to the rider: one is "you exported a
     * copy", the other is "your account has this". A rider can have either
     * without the other.
     */
    val lastCloudSyncAtMs: Long? = null,

    /**
     * What went wrong the last time an upload was attempted, or null if the
     * last thing that happened was a success.
     *
     * **This is the item's whole point.** `SyncOutcome.Failed` has always died
     * in a `Log.w` — on a tablet whose `log.tag` is `W` device-wide, so even
     * the log was mostly theoretical — and that is precisely how three separate
     * cloud defects survived the entire project: a missing `GRANT`, a timestamp
     * Postgres could not parse, and a decode that threw on every class fetch.
     * All three were silent, and all three would have been one glance at this
     * line.
     */
    val lastCloudSyncError: String? = null,

    /** When [lastCloudSyncError] happened. Null exactly when the error is. */
    val lastCloudSyncErrorAtMs: Long? = null,

    /**
     * How old a ride has to be before its seconds are condensed to an outline
     * (23.4.4).
     *
     * **A device setting rather than a rider one**, like the telemetry source
     * and unlike the FTP: it is about how much of this tablet the recording
     * takes up, and a household bike has one database and several profiles. The
     * consequence is that one rider's choice trims another rider's rides, which
     * is 23.4.11's question in miniature and is why the screen says *every
     * rider on this bike* out loud.
     */
    val retentionAge: RetentionAge = RetentionAge.DEFAULT
) {
    companion object {
        /**
         * Full. The coach already sits under audio focus ducking (11.1.6), so
         * a rider who has never touched this should hear it clearly over their
         * film rather than have to go and find the slider first.
         */
        const val DEFAULT_COACH_VOLUME = 1f
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pelonot_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            // A corrupt preferences file must not take the app down on launch.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.Dark,
                useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                sensorMode = prefs[Keys.SENSOR_MODE]
                    ?.let { name -> SensorMode.entries.firstOrNull { it.name == name } }
                    ?: SensorMode.Auto,
                lastProfileId = prefs[Keys.LAST_PROFILE_ID]?.takeIf { it >= 0 },
                heartRateDeviceAddress = prefs[Keys.HR_DEVICE_ADDRESS],
                cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true,
                coachStyle = CoachStyle.fromName(prefs[Keys.COACH_STYLE]),
                coachVolume = (prefs[Keys.COACH_VOLUME] ?: AppSettings.DEFAULT_COACH_VOLUME)
                    .coerceIn(0f, 1f),
                hudEnabled = prefs[Keys.HUD_ENABLED] ?: true,
                hudDock = HudDock.fromName(prefs[Keys.HUD_DOCK]),
                hudOpacity = (prefs[Keys.HUD_OPACITY] ?: HudOpacity.DEFAULT).coerceIn(0f, 1f),
                // Absent means the rider has never chosen, so fall back to the
                // locale each time rather than pinning metric on first read.
                unitSystem = UnitSystem.fromName(prefs[Keys.UNIT_SYSTEM])
                    ?: UnitSystem.fromLocale(),
                backupMarkAtMs = prefs[Keys.BACKUP_MARK_AT],
                hasEverBackedUp = prefs[Keys.HAS_EVER_BACKED_UP] ?: false,
                lastCloudSyncAtMs = prefs[Keys.LAST_CLOUD_SYNC_AT],
                lastCloudSyncError = prefs[Keys.LAST_CLOUD_SYNC_ERROR],
                lastCloudSyncErrorAtMs = prefs[Keys.LAST_CLOUD_SYNC_ERROR_AT],
                retentionAge = RetentionAge.fromName(prefs[Keys.RETENTION_AGE])
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setSensorMode(mode: SensorMode) = edit { it[Keys.SENSOR_MODE] = mode.name }

    suspend fun setLastProfileId(id: Int?) = edit { prefs ->
        if (id == null) prefs.remove(Keys.LAST_PROFILE_ID) else prefs[Keys.LAST_PROFILE_ID] = id
    }

    suspend fun setHeartRateDeviceAddress(address: String?) = edit { prefs ->
        if (address == null) prefs.remove(Keys.HR_DEVICE_ADDRESS)
        else prefs[Keys.HR_DEVICE_ADDRESS] = address
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) = edit { it[Keys.CLOUD_SYNC_ENABLED] = enabled }

    suspend fun setCoachStyle(style: CoachStyle) = edit { it[Keys.COACH_STYLE] = style.name }

    suspend fun setCoachVolume(volume: Float) = edit {
        it[Keys.COACH_VOLUME] = volume.coerceIn(0f, 1f)
    }

    suspend fun setHudEnabled(enabled: Boolean) = edit { it[Keys.HUD_ENABLED] = enabled }

    /** Persisted so the HUD returns to the edge the rider last dragged it to. */
    suspend fun setHudDock(dock: HudDock) = edit { it[Keys.HUD_DOCK] = dock.name }

    suspend fun setHudOpacity(opacity: Float) = edit {
        it[Keys.HUD_OPACITY] = opacity.coerceIn(0f, 1f)
    }

    suspend fun setUnitSystem(units: UnitSystem) = edit { it[Keys.UNIT_SYSTEM] = units.name }

    /**
     * A backup was written (23.3.1). Only ever called on success — a backup
     * that failed has protected nothing, and recording it would tell the rider
     * they are safe on the one day they are not.
     */
    suspend fun markBackedUp(atMs: Long = System.currentTimeMillis()) = edit {
        it[Keys.BACKUP_MARK_AT] = atMs
        it[Keys.HAS_EVER_BACKED_UP] = true
    }

    /**
     * "Not now". Moves the line without claiming a backup happened, so the
     * sentence next time is still *no backup yet* if that is the truth.
     */
    suspend fun snoozeBackupReminder(atMs: Long = System.currentTimeMillis()) = edit {
        it[Keys.BACKUP_MARK_AT] = atMs
    }

    /**
     * The cloud took a ride (14.2.3).
     *
     * Deliberately does **not** clear the last error. A drain can upload
     * fifteen rides and fail on the sixteenth, and "it worked" and "it then
     * stopped working" are both true and both worth saying. The error is
     * cleared by [clearCloudSyncError] when a drain actually finishes with
     * nothing left, which is the only moment the app can honestly claim the
     * rider is up to date.
     */
    suspend fun recordCloudSync(atMs: Long = System.currentTimeMillis()) = edit {
        it[Keys.LAST_CLOUD_SYNC_AT] = atMs
    }

    /**
     * An upload failed, and this is what it said.
     *
     * Truncated, because a PostgREST error body can be long and the rider needs
     * the first line of it rather than a wall. It is shown on the rider's own
     * device about their own account, so the text is not treated as sensitive —
     * but it is not sent anywhere either, and nothing here should ever start
     * doing that without an item of its own.
     */
    suspend fun recordCloudSyncFailure(
        message: String,
        atMs: Long = System.currentTimeMillis()
    ) = edit {
        it[Keys.LAST_CLOUD_SYNC_ERROR] = message.take(MAX_ERROR_CHARS)
        it[Keys.LAST_CLOUD_SYNC_ERROR_AT] = atMs
    }

    /**
     * The rider chose how long the full record is kept (23.4.4).
     *
     * Storing the choice is all this does. Nothing is deleted on the strength
     * of it until the rider taps the button that says what it will do, and the
     * launch pass only ever acts on a choice already made — see
     * `RetentionRepository`.
     */
    suspend fun setRetentionAge(age: RetentionAge) = edit { it[Keys.RETENTION_AGE] = age.name }

    /** The backlog is empty, so whatever went wrong before has stopped. */
    suspend fun clearCloudSyncError() = edit {
        it.remove(Keys.LAST_CLOUD_SYNC_ERROR)
        it.remove(Keys.LAST_CLOUD_SYNC_ERROR_AT)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SENSOR_MODE = stringPreferencesKey("sensor_mode")
        val LAST_PROFILE_ID = intPreferencesKey("last_profile_id")
        val HR_DEVICE_ADDRESS = stringPreferencesKey("hr_device_address")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val COACH_STYLE = stringPreferencesKey("coach_style")
        val COACH_VOLUME = floatPreferencesKey("coach_volume")
        val HUD_ENABLED = booleanPreferencesKey("hud_enabled")
        val HUD_DOCK = stringPreferencesKey("hud_dock")
        val HUD_OPACITY = floatPreferencesKey("hud_opacity")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val BACKUP_MARK_AT = longPreferencesKey("backup_mark_at")
        val HAS_EVER_BACKED_UP = booleanPreferencesKey("has_ever_backed_up")
        val LAST_CLOUD_SYNC_AT = longPreferencesKey("last_cloud_sync_at")
        val LAST_CLOUD_SYNC_ERROR = stringPreferencesKey("last_cloud_sync_error")
        val LAST_CLOUD_SYNC_ERROR_AT = longPreferencesKey("last_cloud_sync_error_at")
        val RETENTION_AGE = stringPreferencesKey("retention_age")
    }

    private companion object {
        /** Enough for a PostgREST message and its code, not a whole body. */
        const val MAX_ERROR_CHARS = 300
    }
}
