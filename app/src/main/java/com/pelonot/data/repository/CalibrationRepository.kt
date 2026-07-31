package com.pelonot.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pelonot.domain.calibration.CalibratedPowerCurve
import com.pelonot.domain.calibration.CalibrationCell
import com.pelonot.domain.calibration.CalibrationGrid
import com.pelonot.domain.calibration.CalibrationSample
import com.pelonot.domain.calibration.FitOutcome
import com.pelonot.domain.calibration.PowerCurve
import com.pelonot.domain.calibration.PowerCurveFitter
import com.pelonot.domain.calibration.ShippedPowerCurve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** What the rider is told about this bike's calibration (2.2a.6). */
data class CalibrationState(
    val curve: PowerCurve = ShippedPowerCurve,
    val isCalibrated: Boolean = false,
    /** 0..1 of the resistance range this bike has been ridden across. */
    val coverage: Float = 0f,
    val resistanceLevelsCovered: Int = 0,
    val resistanceLevelsNeeded: Int = CalibrationGrid.MIN_RESISTANCE_LEVELS,
    val sampleCount: Int = 0,
    val lastFitAtEpochMs: Long? = null,
    /** Out-of-sample median error of the adopted curve, when there is one. */
    val errorPercent: Double? = null
)

/**
 * What the app has learnt about **this bike's** power curve (PLAN 2.2a).
 *
 * Deliberately its own DataStore rather than a column on `profiles` or a row in
 * Room. Two reasons, both of them 2.2a.2:
 *
 *  - **A household bike has several profiles and one resistance mechanism.**
 *    Calibration is a property of the machine, so it must be shared by every
 *    rider on it and must never sync as if it were personal data (15).
 *  - It is derived state. Losing it costs a few weeks of passive accumulation
 *    and nothing else, which is not worth a schema migration and the exported
 *    schema and `MigrationTestHelper` test that every one of those needs.
 */
class CalibrationRepository(context: Context) {

    private val dataStore = context.applicationContext.calibrationDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<CalibrationState> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            val grid = decodeGrid(prefs[Keys.GRID])
            val curve = decodeCurve(prefs[Keys.CURVE])
            CalibrationState(
                curve = curve ?: ShippedPowerCurve,
                isCalibrated = curve != null,
                coverage = grid.coverageFraction,
                resistanceLevelsCovered = grid.wellSampledResistanceLevels,
                sampleCount = grid.sampleCount,
                lastFitAtEpochMs = prefs[Keys.LAST_FIT_AT],
                errorPercent = prefs[Keys.ERROR_PERCENT]?.toDoubleOrNull()
            )
        }

    /** The curve to estimate with right now, read once at ride start. */
    suspend fun activeCurve(): PowerCurve = readGridAndCurve().second ?: ShippedPowerCurve

    /**
     * Folds one finished ride into the grid and re-fits.
     *
     * Called only for rides whose watts were **measured** (2.2a.7): a
     * simulated ride's power came out of `PowerModel` itself, so learning from
     * it would be the model teaching itself its own answer.
     */
    suspend fun recordRide(samples: List<CalibrationSample>): FitOutcome {
        val (existing, _) = readGridAndCurve()

        // Aged once per ride, not per sample — a long ride should not discount
        // its own beginning.
        val grid = existing.decayed().plusRide(samples)
        val outcome = PowerCurveFitter.fit(grid)

        dataStore.edit { prefs ->
            prefs[Keys.GRID] = json.encodeToString(GridDto.serializer(), grid.toDto())
            when (outcome) {
                is FitOutcome.Adopted -> {
                    prefs[Keys.CURVE] = json.encodeToString(
                        CurveDto.serializer(),
                        outcome.curve.toDto()
                    )
                    prefs[Keys.LAST_FIT_AT] = System.currentTimeMillis()
                    prefs[Keys.ERROR_PERCENT] = outcome.candidateErrorPercent.toString()
                }
                // A rejected fit leaves whatever was adopted before in place.
                // The alternative — dropping back to the shipped curve on one
                // bad ride — would make the rider's prescribed band jump around
                // for no reason they could see.
                else -> Unit
            }
        }

        Log.i(TAG, "Calibration after ride: $outcome (${grid.sampleCount} samples)")
        return outcome
    }

    /** Throws away everything learnt and returns to the shipped curve. */
    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private suspend fun readGridAndCurve(): Pair<CalibrationGrid, PowerCurve?> {
        val prefs = runCatching { dataStore.data.first() }.getOrNull()
            ?: return CalibrationGrid() to null
        return decodeGrid(prefs[Keys.GRID]) to decodeCurve(prefs[Keys.CURVE])
    }

    private fun decodeGrid(raw: String?): CalibrationGrid {
        if (raw.isNullOrBlank()) return CalibrationGrid()
        return runCatching { json.decodeFromString(GridDto.serializer(), raw).toDomain() }
            .onFailure { Log.w(TAG, "Discarding unreadable calibration grid", it) }
            .getOrDefault(CalibrationGrid())
    }

    private fun decodeCurve(raw: String?): CalibratedPowerCurve? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(CurveDto.serializer(), raw).toDomain() }
            .onFailure { Log.w(TAG, "Discarding unreadable calibration curve", it) }
            .getOrNull()
    }

    // ── Wire types ──────────────────────────────────────────────────
    //
    // A list, not a map: the cell carries its own bins, so the key is
    // redundant, and a data-class map key has no natural JSON form.

    @Serializable
    private data class CellDto(
        val r: Int,
        val c: Int,
        val cadence: Double,
        val resistance: Double,
        val watts: Double,
        val samples: Int,
        val weight: Double
    )

    @Serializable
    private data class GridDto(val cells: List<CellDto> = emptyList())

    @Serializable
    private data class CurveDto(
        val a: Double,
        val b: Double,
        val k: Double,
        val c: Double
    )

    private fun CalibrationGrid.toDto() = GridDto(
        cells.values.map {
            CellDto(
                r = it.resistanceBin,
                c = it.cadenceBin,
                cadence = it.meanCadenceRpm,
                resistance = it.meanResistancePercent,
                watts = it.meanWatts,
                samples = it.samples,
                weight = it.weight
            )
        }
    )

    private fun GridDto.toDomain() = CalibrationGrid(
        cells = cells.associate { dto ->
            CalibrationGrid.CellKey(dto.r, dto.c) to CalibrationCell(
                resistanceBin = dto.r,
                cadenceBin = dto.c,
                meanCadenceRpm = dto.cadence,
                meanResistancePercent = dto.resistance,
                meanWatts = dto.watts,
                samples = dto.samples,
                weight = dto.weight
            )
        }
    )

    private fun CalibratedPowerCurve.toDto() = CurveDto(a, b, k, c)

    private fun CurveDto.toDomain() = CalibratedPowerCurve(a, b, k, c)

    private object Keys {
        val GRID = stringPreferencesKey("calibration_grid")
        val CURVE = stringPreferencesKey("calibration_curve")
        val LAST_FIT_AT = longPreferencesKey("calibration_last_fit_at")

        /** Stored as text: DataStore has no double key type. */
        val ERROR_PERCENT = stringPreferencesKey("calibration_error_percent")
    }

    private companion object {
        const val TAG = "CalibrationRepository"
    }
}

private val Context.calibrationDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "pelonot_bike_calibration")
