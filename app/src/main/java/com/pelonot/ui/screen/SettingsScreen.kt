package com.pelonot.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.data.repository.CalibrationState
import com.pelonot.data.repository.ThemeMode
import com.pelonot.data.sensor.HeartRateStatus
import com.pelonot.data.sensor.SensorMode
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.HudOpacity
import com.pelonot.ui.components.VolumeSliders
import com.pelonot.domain.model.UnitSystem
import com.pelonot.ui.overlay.OverlayPermissionHelper
import com.pelonot.ui.theme.DarkSurfaceContainerLowest
import com.pelonot.ui.theme.DarkTextPrimary
import com.pelonot.ui.theme.HudMinimumOpacity
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.readableColumn
import com.pelonot.ui.theme.spacing
import kotlinx.coroutines.launch
import com.pelonot.ui.viewmodel.SettingsViewModel
import androidx.compose.ui.unit.dp
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.FtpHistoryEntity
import com.pelonot.domain.cloud.CloudSyncStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings, backed by [SettingsViewModel] so every change persists.
 *
 * The previous screen took its values as parameters from `remember`ed state in
 * the navigation graph and wrote them back through callbacks that updated
 * those same transient variables, so nothing survived leaving the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The media volume is a system value; anything on the device may have moved
    // it since this screen was last looked at.
    LaunchedEffect(Unit) { viewModel.refreshVolume() }

    val scanForHeartRate = rememberHeartRateScan(viewModel)

    // 19.1.3 / 12.4.4. Through the system's own pickers, like the ride export
    // (12.4.3): the rider says where the file goes and where it comes from, and
    // no FileProvider or storage permission is involved on any API level.
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val say: (String) -> Unit = { message -> scope.launch { snackbarHost.showSnackbar(message) } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        // A cancelled picker is not a failure and does not deserve a message.
        if (uri != null) viewModel.backupTo(uri, say)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Confirmed *after* the file is chosen, so the warning can be about
        // this restore rather than about restores in general.
        if (uri != null) pendingRestore = uri
    }

    pendingRestore?.let { uri ->
        RestoreConfirmDialog(
            onConfirm = {
                pendingRestore = null
                viewModel.restoreFrom(
                    source = uri,
                    onRefused = say,
                    onRestored = { restartApp(context) }
                )
            },
            onDismiss = { pendingRestore = null }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                // 22.2.6. A form field 1200 dp wide with a two-word label on it
                // is not easier to use than the same field at 700.
                .readableColumn()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            if (state.isGuest) {
                SettingsSection("Profile") {
                    Text(
                        text = "You're riding as a guest, so FTP and weight can't be " +
                            "saved. Create a profile to track them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                RiderSection(
                    ftp = state.ftpWatts,
                    weightKg = state.weightKg,
                    units = state.settings.unitSystem,
                    lastFtpChange = state.lastFtpChange,
                    previousFtpWatts = state.previousFtpWatts,
                    onSave = viewModel::saveRider
                )
            }

            if (!state.isGuest) {
                HouseholdSection(
                    visible = state.profile?.householdVisible ?: true,
                    onVisibleChange = viewModel::setHouseholdVisible
                )
            }

            UnitsSection(
                units = state.settings.unitSystem,
                onUnitsChange = viewModel::setUnitSystem
            )

            AppearanceSection(
                themeMode = state.settings.themeMode,
                useDynamicColor = state.settings.useDynamicColor,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor
            )

            RideHudSection(
                hudEnabled = state.settings.hudEnabled,
                dock = state.settings.hudDock,
                hudOpacity = state.settings.hudOpacity,
                coachStyle = state.settings.coachStyle,
                overlayGranted = OverlayPermissionHelper.canDrawOverlays(context),
                onHudEnabledChange = viewModel::setHudEnabled,
                onDockChange = viewModel::setHudDock,
                onHudOpacityChange = viewModel::setHudOpacity,
                onCoachStyleChange = viewModel::setCoachStyle,
                onRequestPermission = {
                    OverlayPermissionHelper.requestOverlayPermission(context)
                }
            )

            VolumeSection(
                mediaVolume = state.mediaVolume,
                coachVolume = state.settings.coachVolume,
                error = state.volumeError,
                onMediaVolumeChange = viewModel::setMediaVolume,
                onCoachVolumeChange = viewModel::setCoachVolume
            )

            SensorSection(
                sensorMode = state.settings.sensorMode,
                onSensorModeChange = viewModel::setSensorMode
            )

            CalibrationSection(
                calibration = state.calibration,
                onReset = viewModel::resetCalibration
            )

            HeartRateSection(
                status = state.heartRateStatus,
                deviceCount = state.heartRateDevices.size,
                selectedAddress = state.settings.heartRateDeviceAddress,
                onScan = scanForHeartRate,
                onForget = { viewModel.selectHeartRateDevice(null) }
            )

            HeartRateZonesSection(
                maxHrBpm = state.profile?.maxHrBpm,
                birthDate = state.profile?.birthDate,
                highestRecorded = state.highestRecordedHr,
                resolved = state.maxHeartRate,
                onAskForHighest = viewModel::loadHighestRecordedHeartRate,
                onSave = viewModel::saveHeartRateBasis
            )

            CloudSection(
                onOpenAccount = onOpenAccount,
                cloudConfigured = state.cloudConfigured,
                hasAccount = state.profile?.hasAccount == true,
                signedInHere = state.sessionMatchesProfile,
                ridesWaiting = state.ridesWaiting,
                backupEnabled = state.settings.cloudSyncEnabled,
                onBackupEnabledChange = viewModel::setCloudSyncEnabled,
                syncStatus = state.cloudSync
            )

            BackupSection(
                ridesAreLocalOnly = state.profile?.hasAccount != true,
                onBackup = { backupLauncher.launch(viewModel.backupFileName()) },
                onRestore = { restoreLauncher.launch(arrayOf("*/*")) }
            )

            Spacer(Modifier.size(MaterialTheme.spacing.large))
        }
    }
}

/**
 * Asks for what a BLE scan needs, then scans (11.6.9).
 *
 * Scan used to report `HeartRateStatus.PermissionRequired` and stop there: the
 * screen said what was wrong and offered no way to put it right, so a strap
 * could never be paired from inside the app. `heartRatePermissions()` existed
 * on the ViewModel and nothing called it.
 *
 * Lifted out of [SettingsScreen] because [RideSettingsSheet] needs exactly the
 * same thing, and a second copy of a permission dance is a second thing to get
 * wrong — note that below API 31 the BLE scan permission is
 * `ACCESS_FINE_LOCATION`, which has already cost this project one defect.
 */
@Composable
private fun rememberHeartRateScan(viewModel: SettingsViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Scan only on success. A denial leaves the status where it was, which
        // is the message explaining what is missing.
        if (granted.values.all { it }) viewModel.scanForHeartRateMonitors()
    }
    return {
        val needed = viewModel.heartRatePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) viewModel.scanForHeartRateMonitors()
        else launcher.launch(needed.toTypedArray())
    }
}

/**
 * The settings a rider discovers they need **while riding** (11.6.10).
 *
 * A sheet over the ride rather than a navigation away from it: pairing a strap,
 * changing the telemetry source and fixing the coach volume were all things
 * that cost the rider their ride, because the only route to any of them was
 * Settings and the only route to Settings was out of the ride screen.
 *
 * Deliberately three sections and not the whole of Settings. FTP, units, theme
 * and backup are not mid-ride questions, and 24.1.5's rule applies from the
 * other direction: this adds a control, not a screenful of numbers. It reuses
 * the same section composables Settings draws, so the two cannot disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanForHeartRate = rememberHeartRateScan(viewModel)

    // The media volume is a system value; anything on the device may have moved
    // it since — including the film the rider is watching.
    LaunchedEffect(Unit) { viewModel.refreshVolume() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            Text(
                text = "During the ride",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Your ride keeps recording while this is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HeartRateSection(
                status = state.heartRateStatus,
                deviceCount = state.heartRateDevices.size,
                selectedAddress = state.settings.heartRateDeviceAddress,
                onScan = scanForHeartRate,
                onForget = { viewModel.selectHeartRateDevice(null) }
            )

            HeartRateZonesSection(
                maxHrBpm = state.profile?.maxHrBpm,
                birthDate = state.profile?.birthDate,
                highestRecorded = state.highestRecordedHr,
                resolved = state.maxHeartRate,
                onAskForHighest = viewModel::loadHighestRecordedHeartRate,
                onSave = viewModel::saveHeartRateBasis
            )

            VolumeSection(
                mediaVolume = state.mediaVolume,
                coachVolume = state.settings.coachVolume,
                error = state.volumeError,
                onMediaVolumeChange = viewModel::setMediaVolume,
                onCoachVolumeChange = viewModel::setCoachVolume
            )

            // Changing this mid-ride restarts the telemetry pipeline, which is
            // exactly what a rider whose board has died is here to do.
            SensorSection(
                sensorMode = state.settings.sensorMode,
                onSensorModeChange = viewModel::setSensorMode
            )

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
        }
    }
}

@Composable
private fun RiderSection(
    ftp: Int,
    weightKg: Double?,
    units: UnitSystem,
    lastFtpChange: FtpHistoryEntity?,
    previousFtpWatts: Int?,
    /** One call, not two — see `SettingsViewModel.saveRider`. */
    onSave: (ftpWatts: Int?, weightKg: Double?) -> Unit
) {
    // Seeded from the persisted value, then re-seeded whenever it changes
    // underneath us (e.g. an accepted FTP breakthrough) — or whenever the unit
    // preference changes, since 72 kg and 159 lb are the same rider.
    var ftpText by remember(ftp) { mutableStateOf(ftp.toString()) }
    var weightText by remember(weightKg, units) {
        mutableStateOf(
            weightKg?.let { String.format(Locale.US, "%.1f", units.weightFromKg(it)) }.orEmpty()
        )
    }

    val ftpValue = ftpText.toIntOrNull()
    // The field is in the rider's units; the profile row is always kilograms.
    val weightValue = weightText.toDoubleOrNull()?.let(units::weightToKg)
    val ftpError = ftpText.isNotBlank() && (ftpValue == null || ftpValue !in MIN_FTP..MAX_FTP)
    val weightError = weightText.isNotBlank() &&
        (weightValue == null || weightValue !in MIN_WEIGHT..MAX_WEIGHT)

    SettingsSection("Rider") {
        OutlinedTextField(
            value = ftpText,
            onValueChange = { ftpText = it.filter(Char::isDigit) },
            label = { Text("FTP (watts)") },
            supportingText = {
                Text(
                    if (ftpError) "Enter a value between $MIN_FTP and $MAX_FTP"
                    else "Functional Threshold Power — the basis of every zone target"
                )
            },
            isError = ftpError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        FtpLastChanged(lastFtpChange, previousFtpWatts)

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight (${units.weightLabel})") },
            supportingText = {
                if (weightError) {
                    Text(
                        "Enter a value between " +
                            "${units.weightFromKg(MIN_WEIGHT).toInt()} and " +
                            "${units.weightFromKg(MAX_WEIGHT).toInt()} ${units.weightLabel}"
                    )
                }
            },
            isError = weightError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Button(
            onClick = {
                onSave(
                    ftpValue?.takeIf { it in MIN_FTP..MAX_FTP },
                    weightValue?.takeIf { it in MIN_WEIGHT..MAX_WEIGHT }
                )
            },
            enabled = !ftpError && !weightError,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.expressiveShapes.pill
        ) {
            Text("Save")
        }
    }
}

/**
 * When this rider's FTP last moved, and who moved it (PLAN 7.10.3).
 *
 * The reason it says *who* is 7.10.4: **an accepted auto-FTP change is the app
 * editing the rider's own record**, and until 7.9 there was no way for a rider
 * who did not remember agreeing to it to find out that it had happened. A
 * number that changed by itself and cannot be traced is indistinguishable from
 * a bug.
 *
 * Silent until the number has actually moved. A rider's first row is the value
 * their profile started with, and reporting that as a change would be the app
 * announcing an event that never took place.
 */
@Composable
private fun FtpLastChanged(entry: FtpHistoryEntity?, previousWatts: Int?) {
    if (entry == null) return

    val direction = when {
        previousWatts == null -> null
        entry.ftpWatts > previousWatts -> "up from $previousWatts W"
        entry.ftpWatts < previousWatts -> "down from $previousWatts W"
        else -> null
    }
    val who = when (FtpChangeSource.fromName(entry.source)) {
        FtpChangeSource.ManualEdit -> "you set it"
        FtpChangeSource.AutoBreakthrough -> "the app measured it from a ride"
        FtpChangeSource.GuidedTest -> "an FTP test"
        FtpChangeSource.PulledFromCloud -> "another device"
        FtpChangeSource.AutoBreakthroughReverted -> "you put back the app's change"
        FtpChangeSource.ProfileCreated -> "when the profile was made"
        FtpChangeSource.Estimated -> "the app's first guess"
        // Every value seeded by migration 7→8, and any write path that changes
        // the number without saying why. The date is still true.
        FtpChangeSource.Unknown -> null
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.changedAt))

    Text(
        text = listOfNotNull("Last changed $date", direction, who).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
    )
}

/**
 * Miles or kilometres.
 *
 * The default comes from the device locale rather than being fixed at metric,
 * which is what the app did before — wrong for a UK or US rider, and wrong on
 * a Peloton bike whose own display is in miles.
 *
 * Changing it re-renders every existing ride; it does not rewrite one. Distance
 * and weight are stored in SI and converted here at the edge, so flipping this
 * back and forth is lossless.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsSection(
    units: UnitSystem,
    onUnitsChange: (UnitSystem) -> Unit
) {
    SettingsSection("Units") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            UnitSystem.entries.forEach { option ->
                FilterChip(
                    selected = units == option,
                    onClick = { onUnitsChange(option) },
                    label = {
                        Text("${option.displayName} (${option.distanceLabel}, ${option.weightLabel})")
                    }
                )
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.small))

        Text(
            text = "Applies to distance, speed and your body weight. Power, cadence, " +
                "heart rate and output have no imperial form and stay as they are — " +
                "and kilojoules is what the bike measures, so Pelonot doesn't offer " +
                "calories.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    SettingsSection("Appearance") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.name) }
                )
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        SettingsToggle(
            title = "Use wallpaper colours",
            description = "Material You. Overrides the Pelonot palette, including " +
                "the metric accent colours the ride screen uses.",
            checked = useDynamicColor,
            onCheckedChange = onDynamicColorChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SensorSection(
    sensorMode: SensorMode,
    onSensorModeChange: (SensorMode) -> Unit
) {
    SettingsSection("Telemetry source") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            SensorMode.entries.forEach { mode ->
                FilterChip(
                    selected = sensorMode == mode,
                    onClick = { onSensorModeChange(mode) },
                    label = { Text(mode.name) }
                )
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.small))

        Text(
            text = when (sensorMode) {
                SensorMode.Auto ->
                    "Uses the Peloton sensor board when it's present, and simulated " +
                        "data otherwise. Recommended."
                SensorMode.Hardware ->
                    "Requires the sensor board. Keeps retrying rather than falling " +
                        "back, so a ride never records fabricated numbers."
                SensorMode.Simulated ->
                    "Always generates fake telemetry. For trying the app out on a " +
                        "phone — rides recorded this way aren't real."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeartRateSection(
    status: HeartRateStatus,
    deviceCount: Int,
    selectedAddress: String?,
    onScan: () -> Unit,
    onForget: () -> Unit
) {
    SettingsSection("Heart rate monitor") {
        Text(
            text = when (status) {
                HeartRateStatus.Unsupported -> "This device has no Bluetooth LE radio."
                HeartRateStatus.BluetoothOff -> "Bluetooth is switched off."
                HeartRateStatus.PermissionRequired ->
                    "Pelonot needs Bluetooth permission to find your strap."
                HeartRateStatus.Idle ->
                    if (selectedAddress != null) "Paired, waiting to connect."
                    else "No strap paired."
                HeartRateStatus.Scanning -> "Scanning… found $deviceCount so far."
                is HeartRateStatus.Connecting -> "Connecting to ${status.deviceName ?: "strap"}…"
                is HeartRateStatus.Connected -> "Connected to ${status.deviceName ?: "strap"}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            OutlinedButton(onClick = onScan, shape = MaterialTheme.expressiveShapes.pill) {
                Text("Scan")
            }
            if (selectedAddress != null) {
                OutlinedButton(onClick = onForget, shape = MaterialTheme.expressiveShapes.pill) {
                    Text("Forget")
                }
            }
        }
    }
}

/**
 * What heart-rate zones are computed from (21.1, 21.2.2, 21.3.3).
 *
 * **The order of the two fields is the design.** The app does not want
 * anybody's date of birth; it wants a maximum heart rate, and age is only a
 * proxy for one — a poor proxy, with a 10–12 bpm spread between individuals at
 * the same age, which is wider than a zone. So the rider's own number is asked
 * for first and the date is the fallback, which is both more accurate and asks
 * less about the person.
 *
 * Three states, and the third is the one worth getting right: a rider who
 * gives neither gets **no heart-rate zones**, said plainly, rather than zones
 * computed off a number the app made up. Same rule as a null heart rate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateZonesSection(
    maxHrBpm: Int?,
    birthDate: Long?,
    highestRecorded: Int?,
    resolved: MaxHeartRate?,
    onAskForHighest: () -> Unit,
    /** One call, not two — the same rule `saveRider` carries. */
    onSave: (maxHrBpm: Int?, birthDate: Long?) -> Unit
) {
    var maxText by remember(maxHrBpm) { mutableStateOf(maxHrBpm?.toString().orEmpty()) }
    var date by remember(birthDate) { mutableStateOf(birthDate) }
    var picking by remember { mutableStateOf(false) }

    // Asked for once, while the section is on screen, so the offer below is
    // ready before the rider starts typing.
    LaunchedEffect(Unit) { onAskForHighest() }

    val typed = maxText.toIntOrNull()
    val maxError = maxText.isNotBlank() && (typed == null || !MaxHeartRate.isPlausible(typed))

    // What the rider *would* get if they saved right now, so the estimate is
    // visibly a fitness calculation rather than a form harvesting a birthday.
    val preview = MaxHeartRate.resolve(typed?.takeIf { !maxError }, date)

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    date = state.selectedDateMillis
                    picking = false
                }) { Text("Use this date") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state, title = { Text("Date of birth") })
        }
    }

    SettingsSection("Heart-rate zones") {
        OutlinedTextField(
            value = maxText,
            onValueChange = { maxText = it.filter(Char::isDigit) },
            label = { Text("Maximum heart rate (bpm)") },
            supportingText = {
                Text(
                    if (maxError) {
                        "Enter a value between ${MaxHeartRate.MIN_BPM} and ${MaxHeartRate.MAX_BPM}"
                    } else {
                        "If you know your own number, this is the one to use — " +
                            "an estimate can be a whole zone out."
                    }
                )
            },
            isError = maxError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // 21.1.3. The app already holds every sample the rider has ever
        // recorded, so it can make a far better opening guess than a formula.
        // Offered, never written for them: the hardest thirty seconds they have
        // ridden so far is a floor, not a maximum, and only they can say which.
        if (highestRecorded != null) {
            TextButton(onClick = { maxText = highestRecorded.toString() }) {
                Text("Use $highestRecorded — the highest you've recorded")
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Text(
            text = "Don't know it?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pelonot can estimate one from your age — Tanaka's 208 − 0.7 × age. " +
                "It is only an estimate: two riders the same age can be 12 bpm apart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.small))

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { picking = true }, shape = MaterialTheme.expressiveShapes.pill) {
                Text(
                    date?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
                        ?: "Set date of birth"
                )
            }
            if (date != null) {
                TextButton(onClick = { date = null }) { Text("Clear") }
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Button(
            onClick = { onSave(typed?.takeIf { MaxHeartRate.isPlausible(it) }, date) },
            enabled = !maxError,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.expressiveShapes.pill
        ) {
            Text("Save heart-rate settings")
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        // The zones themselves, drawn from whatever is saved — and from the
        // *preview* while the rider is editing, so the estimate visibly moves
        // as they fill the date in.
        HeartRateZoneLadder(preview ?: resolved)
    }
}

/** The five zones with their bpm, or the honest empty state (21.3.3). */
@Composable
private fun HeartRateZoneLadder(max: MaxHeartRate?) {
    if (max == null) {
        Text(
            text = "No heart-rate zones yet. Pelonot won't guess a maximum — " +
                "give it your own number or a date of birth and the zones appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Text(
        text = if (max.isEstimate) {
            "Zones from ${max.bpm} bpm — estimated from your date of birth"
        } else {
            "Zones from ${max.bpm} bpm — your own number"
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 21.2.2: say which basis, wherever the zones are shown.
    Text(
        text = "As a percentage of maximum heart rate",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.size(MaterialTheme.spacing.small))

    HeartRateZone.entries.forEach { zone ->
        val range = zone.bpmRange(max.bpm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(MaterialTheme.expressiveShapes.pill)
                        .background(zone.color)
                )
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "H${zone.number} · ${zone.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${range.first}–${range.last} bpm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Which rung of the identity ladder this rider is on, in one line (23.1.4).
 *
 * A rider with no account gets a statement of fact and nothing else — no
 * toggle, no greyed-out control, no spinner, no mention of Supabase or of
 * credentials that are a fact about the build rather than about them (23.1.5).
 * The offline tier is not a locked door with the cloud visible through it, and
 * this is the surface where a rider finds out that the thing they might have
 * assumed was happening is not.
 */
@Composable
private fun CloudSection(
    onOpenAccount: () -> Unit,
    cloudConfigured: Boolean,
    hasAccount: Boolean,
    signedInHere: Boolean,
    ridesWaiting: Int,
    backupEnabled: Boolean,
    onBackupEnabledChange: (Boolean) -> Unit,
    syncStatus: CloudSyncStatus
) {
    SettingsSection("Your rides") {
        if (!hasAccount) {
            Text(
                text = "This bike only. Your rides are recorded here and stay here — " +
                    "nothing about them leaves this tablet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 23.1.5 and 15.1.4. The offer appears only in a build that has a
            // cloud to offer, and it is an offer rather than a prompt — a rider
            // who wants the local tier sees one line of plain fact above it and
            // is never asked again anywhere else in the app.
            if (cloudConfigured) {
                Spacer(Modifier.size(MaterialTheme.spacing.medium))
                OutlinedButton(onClick = onOpenAccount) { Text("Back up my rides") }
            }
        } else if (!signedInHere) {
            // 15.2.8, and this state was found by driving the AVD rather than
            // by reading the diff. The rider has an account — `auth_user_id` is
            // on their row — but this tablet is not carrying their session, so
            // nothing can go up. The old copy said "Backed up to your account"
            // here, which is a claim the app cannot support and the worst
            // possible one to be wrong about: it is the sentence a rider reads
            // instead of checking.
            Text(
                text = "Signed out on this bike. Your rides are still here and still " +
                    "yours — they just aren't being copied anywhere until you sign " +
                    "back in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ridesWaiting > 0) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = if (ridesWaiting == 1) {
                        "1 ride is waiting to go up."
                    } else {
                        "$ridesWaiting rides are waiting to go up."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            OutlinedButton(onClick = onOpenAccount) { Text("Sign back in") }
        } else {
            Text(
                text = "Backed up to your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            SettingsToggle(
                title = "Back up rides",
                description = "Uploads completed rides when a network is available.",
                checked = backupEnabled,
                onCheckedChange = onBackupEnabledChange
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            SyncStatusLine(syncStatus)
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            OutlinedButton(onClick = onOpenAccount) { Text("Manage account") }
        }
    }
}

/**
 * Whether the rider's rides are actually reaching the cloud (PLAN 14.2.3).
 *
 * **This line is the item.** `SyncOutcome.Failed` has always died in a `Log.w`,
 * on a tablet whose `log.tag` is `W` device-wide — which is how a missing
 * `GRANT`, an unparseable timestamp and a decode that threw on every class
 * fetch all survived the project's entire history without anybody noticing.
 *
 * The decision of *what is true* lives in [CloudSyncStatus] and is tested
 * there; this only chooses words for it. Two rules the words follow:
 *
 * - **A failure names the rides it stranded, not just itself.** Three waiting
 *   since this morning and three waiting since March are the same count and
 *   completely different news.
 * - **"Never" is not a date.** A rider who has never synced gets said so,
 *   rather than being shown a backup from January 1970.
 */
@Composable
private fun SyncStatusLine(status: CloudSyncStatus) {
    val scheme = MaterialTheme.colorScheme

    fun on(atMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(atMs))

    val (text, colour) = when (status) {
        // The section already says "This bike only" for a rider with no
        // account, and repeating it as a status would draw the middle rung of
        // the ladder as a fault.
        CloudSyncStatus.Off -> return

        is CloudSyncStatus.UpToDate -> {
            val last = status.lastSyncAtMs
            if (last == null) {
                // NOT "no rides have gone up yet", which the app cannot know.
                // An empty backlog with no recorded sync is also what a rider
                // sees after restoring a backup file made on another tablet:
                // the rides arrive already marked, and the DataStore mark does
                // not travel with them. Saying nothing is waiting is true in
                // both cases; saying nothing has ever gone up is a guess that
                // is wrong in one of them. Seen on the tablet AVD.
                "Nothing is waiting to go up." to scheme.onSurfaceVariant
            } else {
                "Everything is backed up \u2014 last on ${on(last)}." to scheme.onSurfaceVariant
            }
        }

        is CloudSyncStatus.Pending -> {
            val rides = if (status.rides == 1) "1 ride" else "${status.rides} rides"
            val since = status.oldestRideAtMs?.let { " since ${on(it)}" } ?: ""
            "$rides waiting to go up$since." to scheme.onSurfaceVariant
        }

        is CloudSyncStatus.Failing -> {
            val rides = if (status.rides == 1) "1 ride" else "${status.rides} rides"
            val since = status.oldestRideAtMs?.let { ", oldest from ${on(it)}" } ?: ""
            (
                "Backup is failing. $rides waiting$since.\n" +
                    "Last tried ${on(status.failedAtMs)}: ${status.message}"
                ) to scheme.error
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colour
    )
}

/**
 * The HUD and how loudly it is allowed to interrupt.
 *
 * Placed above the sensor and cloud sections because for most riders this is
 * the app: the HUD is what they look at for the whole class, and whether it
 * speaks is the setting they are most likely to change.
 *
 * The overlay permission is re-checked on each composition rather than cached —
 * the rider grants it in system Settings and comes back, so a cached value is
 * stale exactly when it matters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RideHudSection(
    hudEnabled: Boolean,
    dock: HudDock,
    hudOpacity: Float,
    coachStyle: CoachStyle,
    overlayGranted: Boolean,
    onHudEnabledChange: (Boolean) -> Unit,
    onDockChange: (HudDock) -> Unit,
    onHudOpacityChange: (Float) -> Unit,
    onCoachStyleChange: (CoachStyle) -> Unit,
    onRequestPermission: () -> Unit
) {
    SettingsSection("Ride overlay") {
        SettingsToggle(
            title = "Show the ride overlay over other apps",
            description = "Docks your metrics, targets and interval countdown to one " +
                "edge of the screen while you watch something else.",
            checked = hudEnabled,
            onCheckedChange = onHudEnabledChange
        )

        if (hudEnabled && !overlayGranted) {
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Text(
                text = "Android still needs \"display over other apps\" permission " +
                    "before the overlay can appear.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            OutlinedButton(
                onClick = onRequestPermission,
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text("Grant permission")
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.large))

        Text(
            text = "Position",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            HudDock.entries.forEach { option ->
                FilterChip(
                    selected = dock == option,
                    onClick = { onDockChange(option) },
                    label = { Text(option.displayName) }
                )
            }
        }
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Text(
            text = "You can also drag the overlay's handle to move it between edges " +
                "mid-ride. Top is the default because subtitles live along the bottom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.large))

        HudOpacityControl(opacity = hudOpacity, onOpacityChange = onHudOpacityChange)

        Spacer(Modifier.size(MaterialTheme.spacing.large))

        Text(
            text = "Coaching alerts",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            CoachStyle.entries.forEach { option ->
                FilterChip(
                    selected = coachStyle == option,
                    onClick = { onCoachStyleChange(option) },
                    label = { Text(option.displayName) }
                )
            }
        }
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Text(
            text = coachStyle.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
        Text(
            text = "The countdown into the next interval, and what that interval " +
                "will be, are always shown whichever you choose.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * How much of the film the HUD gives back (11.1b.1).
 *
 * Set here rather than on the strip: it is a decision about how the rider likes
 * to watch, not something to fiddle with while pedalling, and the strip is
 * already carrying one control (11.5.5) that only earns its place because this
 * tablet has nowhere else to put it.
 *
 * The slider stops at [HudMinimumOpacity] rather than at zero (11.1b.2). That
 * floor is calculated, not chosen — see `HudOpacity` — and the preview below it
 * is drawn in the HUD's own two colours over the brightest frame a film can
 * produce, which is the case the floor is derived from.
 */
@Composable
private fun HudOpacityControl(opacity: Float, onOpacityChange: (Float) -> Unit) {
    val floor = HudMinimumOpacity
    val current = HudOpacity.clamp(opacity, floor)
    val percent = (current * 100).roundToInt()

    Text(
        text = "How solid the overlay is",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.size(MaterialTheme.spacing.small))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current,
            onValueChange = onOpacityChange,
            valueRange = floor..HudOpacity.OPAQUE,
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = "Overlay opacity, $percent percent"
                }
        )
        Spacer(Modifier.size(MaterialTheme.spacing.medium))
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    HudOpacityPreview(current)

    Spacer(Modifier.size(MaterialTheme.spacing.small))
    Text(
        text = "It will not go below ${(floor * 100).roundToInt()}% — under that " +
            "the strip's smallest labels stop being readable over a bright scene. " +
            "Check it against something actually moving; a still frame is kinder " +
            "than a film is.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The strip's own colours over the worst backdrop it will ever have. */
@Composable
private fun HudOpacityPreview(opacity: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.expressiveShapes.small)
            .background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFD54F))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurfaceContainerLowest.copy(alpha = opacity))
                .padding(horizontal = MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Text(
                text = "12:04",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = DarkTextPrimary
            )
            // The smallest type the strip carries, which is what the floor is
            // set by — a preview of the big numbers alone would flatter it.
            Text(
                text = "CADENCE 84 RPM · 61% · 212 W",
                style = MaterialTheme.typography.labelSmall,
                color = DarkTextPrimary
            )
        }
    }
}

/**
 * What this bike has learnt about its own power curve (2.2a.6).
 *
 * Shown because **a calibration that silently does nothing is
 * indistinguishable from one that works** — which is the failure mode the
 * whole *Corrections* table in PLAN.md exists to prevent. It says which curve
 * is in force, how much of the resistance range this bike has been ridden
 * across, and when it last re-fitted.
 */
@Composable
private fun CalibrationSection(
    calibration: CalibrationState,
    onReset: () -> Unit
) {
    SettingsSection("This bike's power curve") {
        Text(
            text = if (calibration.isCalibrated) {
                "Calibrated to this bike from your own rides."
            } else {
                "Using the built-in curve."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))

        Text(
            text = if (calibration.isCalibrated) {
                val error = calibration.errorPercent?.let { " Typically within ${it.toInt()}%." }
                    ?: ""
                "Peloton's board measures your watts directly, so this only affects the " +
                    "suggested resistance range and rides without a bike.$error"
            } else {
                // Said plainly rather than hidden: the shipped numbers are
                // measurably wrong, and a rider comparing them to a gym bike
                // deserves to know why they disagree.
                "The built-in curve is a rough guess and is known to be well out. Ride " +
                    "the bike normally and Pelonot will learn this one's own curve — " +
                    "no calibration ride needed. Your recorded watts are unaffected " +
                    "either way: the board measures those."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        LinearProgressIndicator(
            progress = { calibration.coverage },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
        Text(
            text = "Resistance range seen: ${calibration.resistanceLevelsCovered} of " +
                "${calibration.resistanceLevelsNeeded} levels, from " +
                "${calibration.sampleCount} measured seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        calibration.lastFitAtEpochMs?.let { at ->
            val when_ = remember(at) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(at))
            }
            Text(
                text = "Last recalibrated $when_.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (calibration.sampleCount > 0) {
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            OutlinedButton(onClick = onReset, shape = MaterialTheme.expressiveShapes.pill) {
                Text("Start again")
            }
        }
    }
}

/**
 * The only volume control this tablet has (11.5).
 *
 * With Peloton's launcher replaced there is no status bar to pull down, so no
 * system volume panel exists at all, and the owner reports no physical rocker.
 * If the app does not offer this, nothing does.
 */
@Composable
private fun VolumeSection(
    mediaVolume: Float,
    coachVolume: Float,
    error: String?,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit
) {
    SettingsSection("Volume") {
        VolumeSliders(
            mediaVolume = mediaVolume,
            coachVolume = coachVolume,
            onMediaVolumeChange = onMediaVolumeChange,
            onCoachVolumeChange = onCoachVolumeChange,
            error = error
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Text(
            text = "Both are also on the overlay mid-ride, behind the volume button — " +
                "which is when you actually find out the film is too loud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The whole database out to a file, and back in (19.1.3 / 12.4.4).
 *
 * Until accounts exist this is the only backup a rider has. Ride export
 * (12.4.3) gets one ride out in a form Strava understands and gets nothing
 * back in; a wipe, a factory reset or an APK downgrade costs everything ridden.
 */
@Composable
private fun BackupSection(
    ridesAreLocalOnly: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    SettingsSection("Backup") {
        Text(
            text = if (ridesAreLocalOnly) {
                // Backup is the offline rider's only durability story, so the
                // copy has to say what it is for — including the part nobody
                // would guess, that a backup restores onto a replacement
                // tablet (23.3.2).
                "Your rides live on this tablet and nowhere else. A backup is " +
                    "one file — copy it somewhere safe and it can be restored onto " +
                    "any tablet running Pelonot."
            } else {
                "A backup is one file holding every ride on this tablet, including " +
                    "riders without an account. Copy it somewhere safe and it can be " +
                    "restored onto any tablet running Pelonot."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(MaterialTheme.spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Button(onClick = onBackup) { Text("Back up now") }
            OutlinedButton(onClick = onRestore) { Text("Restore from a backup") }
        }
    }
}

/**
 * The one dialog in this app that is genuinely about losing data.
 *
 * A restore is not a merge and cannot be undone, so the sentence says exactly
 * that rather than asking "are you sure?" — and the confirming button is
 * labelled with what it does, not with "OK".
 */
@Composable
private fun RestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace everything with this backup?") },
        text = {
            Text(
                "Every ride, profile and setting on this tablet is replaced by " +
                    "what is in the file. Anything recorded since that backup " +
                    "was made is gone, and Pelonot restarts."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace and restart") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Restarts the app after a restore.
 *
 * The database this process is holding has been closed and its file swapped
 * underneath every DAO, `StateFlow` and open cursor in the app. There is no
 * safe way to carry on in that process, and pretending otherwise would leave a
 * rider looking at the *old* history until something happened to reload it.
 */
private fun restartApp(context: Context) {
    val relaunch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (relaunch != null) {
        relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(relaunch)
    }
    Runtime.getRuntime().exit(0)
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            content()
        }
    }
}

/**
 * Whether this rider is part of household social (PLAN 24.2.3).
 *
 * A rider on a shared bike may not want their numbers on a screen the rest of
 * the house sees, and privacy inside a household is still privacy — it is
 * precisely the kind that gets forgotten because everyone involved knows each
 * other. Phrased as what other people see rather than as a feature switch,
 * because that is the question being asked.
 */
@Composable
private fun HouseholdSection(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit
) {
    SettingsSection("On this bike") {
        SettingsToggle(
            title = "Show me to the others",
            description = "Your rides appear on this bike's leaderboards and on the " +
                "week summary everyone here can see.",
            checked = visible,
            onCheckedChange = onVisibleChange
        )
        Text(
            text = if (visible) {
                "Turn this off and you disappear from those screens. Your own history, " +
                    "dashboard and records are untouched — this is only about what other " +
                    "people on this tablet see."
            } else {
                "You're hidden from this bike's leaderboards and week summary. Your own " +
                    "rides are all still here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(MaterialTheme.spacing.medium))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val MIN_FTP = 30
private const val MAX_FTP = 600
private const val MIN_WEIGHT = 25.0
private const val MAX_WEIGHT = 250.0
