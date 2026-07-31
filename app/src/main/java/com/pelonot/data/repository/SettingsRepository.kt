package com.pelonot.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pelonot.data.sensor.SensorMode
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.UnitSystem
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
    val unitSystem: UnitSystem = UnitSystem.fromLocale()
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
                    ?: UnitSystem.fromLocale()
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
    }
}
