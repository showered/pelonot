package com.pelonot.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.data.repository.ThemeMode
import com.pelonot.data.sensor.HeartRateStatus
import com.pelonot.data.sensor.SensorMode
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.ui.overlay.OverlayPermissionHelper
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.SettingsViewModel

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
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
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
                .fillMaxSize()
                .padding(padding)
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
                    onFtpChange = viewModel::setFtp,
                    onWeightChange = viewModel::setWeight
                )
            }

            AppearanceSection(
                themeMode = state.settings.themeMode,
                useDynamicColor = state.settings.useDynamicColor,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor
            )

            RideHudSection(
                hudEnabled = state.settings.hudEnabled,
                dock = state.settings.hudDock,
                coachStyle = state.settings.coachStyle,
                overlayGranted = OverlayPermissionHelper.canDrawOverlays(context),
                onHudEnabledChange = viewModel::setHudEnabled,
                onDockChange = viewModel::setHudDock,
                onCoachStyleChange = viewModel::setCoachStyle,
                onRequestPermission = {
                    OverlayPermissionHelper.requestOverlayPermission(context)
                }
            )

            SensorSection(
                sensorMode = state.settings.sensorMode,
                onSensorModeChange = viewModel::setSensorMode
            )

            HeartRateSection(
                status = state.heartRateStatus,
                deviceCount = state.heartRateDevices.size,
                selectedAddress = state.settings.heartRateDeviceAddress,
                onScan = viewModel::scanForHeartRateMonitors,
                onForget = { viewModel.selectHeartRateDevice(null) }
            )

            CloudSection(
                configured = state.cloudConfigured,
                enabled = state.settings.cloudSyncEnabled,
                onEnabledChange = viewModel::setCloudSyncEnabled
            )

            Spacer(Modifier.size(MaterialTheme.spacing.large))
        }
    }
}

@Composable
private fun RiderSection(
    ftp: Int,
    weightKg: Double?,
    onFtpChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit
) {
    // Seeded from the persisted value, then re-seeded whenever it changes
    // underneath us (e.g. an accepted FTP breakthrough).
    var ftpText by remember(ftp) { mutableStateOf(ftp.toString()) }
    var weightText by remember(weightKg) { mutableStateOf(weightKg?.toString().orEmpty()) }

    val ftpValue = ftpText.toIntOrNull()
    val weightValue = weightText.toDoubleOrNull()
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

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight (kg)") },
            supportingText = {
                if (weightError) Text("Enter a value between $MIN_WEIGHT and $MAX_WEIGHT")
            },
            isError = weightError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Button(
            onClick = {
                ftpValue?.takeIf { it in MIN_FTP..MAX_FTP }?.let(onFtpChange)
                weightValue?.takeIf { it in MIN_WEIGHT..MAX_WEIGHT }?.let(onWeightChange)
            },
            enabled = !ftpError && !weightError,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.expressiveShapes.pill
        ) {
            Text("Save")
        }
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

@Composable
private fun CloudSection(
    configured: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsSection("Cloud sync") {
        if (!configured) {
            Text(
                text = "No Supabase credentials are configured in this build, so " +
                    "Pelonot is running fully offline. Everything works; rides just " +
                    "stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsToggle(
                title = "Back up rides",
                description = "Uploads completed rides when a network is available.",
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
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
    coachStyle: CoachStyle,
    overlayGranted: Boolean,
    onHudEnabledChange: (Boolean) -> Unit,
    onDockChange: (HudDock) -> Unit,
    onCoachStyleChange: (CoachStyle) -> Unit,
    onRequestPermission: () -> Unit
) {
    SettingsSection("Ride HUD") {
        SettingsToggle(
            title = "Show the HUD over other apps",
            description = "Docks your metrics, targets and interval countdown to one " +
                "edge of the screen while you watch something else.",
            checked = hudEnabled,
            onCheckedChange = onHudEnabledChange
        )

        if (hudEnabled && !overlayGranted) {
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Text(
                text = "Android still needs \"display over other apps\" permission " +
                    "before the HUD can appear.",
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
            text = "You can also drag the HUD's handle to move it between edges " +
                "mid-ride. Top is the default because subtitles live along the bottom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
