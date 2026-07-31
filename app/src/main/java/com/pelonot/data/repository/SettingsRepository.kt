package com.pelonot.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pelonot.data.sensor.SensorMode
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
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

    /** Whether the floating HUD is raised over other apps during a ride. */
    val hudEnabled: Boolean = true,

    /** Which screen edge the HUD is pinned to. */
    val hudDock: HudDock = HudDock.DEFAULT
)

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
                hudEnabled = prefs[Keys.HUD_ENABLED] ?: true,
                hudDock = HudDock.fromName(prefs[Keys.HUD_DOCK])
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

    suspend fun setHudEnabled(enabled: Boolean) = edit { it[Keys.HUD_ENABLED] = enabled }

    /** Persisted so the HUD returns to the edge the rider last dragged it to. */
    suspend fun setHudDock(dock: HudDock) = edit { it[Keys.HUD_DOCK] = dock.name }

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
        val HUD_ENABLED = booleanPreferencesKey("hud_enabled")
        val HUD_DOCK = stringPreferencesKey("hud_dock")
    }
}
