package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.data.service.WorkoutService
import com.pelonot.data.sensor.SensorReading
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.ui.theme.CadenceCyan
import com.pelonot.ui.theme.HeartRateGreen
import com.pelonot.ui.theme.PowerCoral
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JustRideScreen(
    modifier: Modifier = Modifier,
    ftp: Int,
    onEndRide: () -> Unit
) {
    val context = LocalContext.current
    
    // Simple state management using remember/mutableStateOf
    var debugStatus by remember { mutableStateOf(listOf("INIT")) }
    var serviceStarted by remember { mutableStateOf(false) }
    var serialPortReady by remember { mutableStateOf(false) }
    var sensorDataFlowing by remember { mutableStateOf(false) }
    var sensorReading by remember { mutableStateOf(SensorReading(0.0, 0.0, 0.0, null, 0)) }
    var lastCheckTime by remember { mutableStateOf(0L) }
    
    // Get sensor repository
    val sensorRepository = SensorRepository.getInstance(context)

    // Check serial port status every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val serialPort = java.io.File("/dev/ttyS2")
                val exists = serialPort.exists()
                val readable = serialPort.canRead()
                serialPortReady = exists && readable
                if (!serialPortReady) {
                    debugStatus = debugStatus + listOf(
                        "SERIAL_FAIL: exists=$exists, readable=$readable"
                    )
                }
            } catch (e: Exception) {
                serialPortReady = false
                debugStatus = debugStatus + listOf("SERIAL_EXC: ${e.message}")
            }
            lastCheckTime = System.currentTimeMillis()
            delay(2000)
        }
    }
    
    // Try to start WorkoutService once
    LaunchedEffect(Unit) {
        if (!serviceStarted) {
            try {
                val intent = android.content.Intent(context, WorkoutService::class.java).apply {
                    action = WorkoutService.ACTION_START_WORKOUT
                    putExtra(WorkoutService.EXTRA_CLASS_ID, 0)
                    putExtra(WorkoutService.EXTRA_INTENT_MODIFIER, "Just Stay Fit")
                }
                context.startService(intent)
                serviceStarted = true
                debugStatus = debugStatus + listOf("SERVICE_STARTED")
            } catch (e: Exception) {
                debugStatus = debugStatus + listOf("SERVICE_FAIL: ${e.message}")
            }
        }
    }
    
    // Observe sensor data using Flow
    val sensorReadingState = sensorRepository.sensorReading.collectAsState()
    sensorReading = sensorReadingState.value
    sensorDataFlowing = sensorReadingState.value.powerWatts > 0 || sensorReadingState.value.cadenceRpm > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Just Ride",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ride freely — no intervals, no pressure.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ========== DEBUG SECTION WITH UNHAPPY PATHS ==========
        Text(
            text = "═══ DEBUG STATUS (GREEN=OK, RED=ERROR) ═══",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        // Service Status
        Text(
            text = if (serviceStarted) "✓ Service Started" else "✗ Service NOT Started",
            style = MaterialTheme.typography.bodySmall,
            color = if (serviceStarted) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
        )
        
        // Serial Port Status  
        Text(
            text = if (serialPortReady) "✓ Serial Port Ready (/dev/ttyS2)" else "✗ Serial Port NOT Ready",
            style = MaterialTheme.typography.bodySmall,
            color = if (serialPortReady) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
        )
        
        // Sensor Data Flow
        Text(
            text = if (sensorDataFlowing) "✓ Sensor Data Flowing" else "✗ No Sensor Data",
            style = MaterialTheme.typography.bodySmall,
            color = if (sensorDataFlowing) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Detailed error messages (last 5)
        Text(
            text = "Last 5 Events:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val recentErrors = debugStatus.takeLast(5)
        Text(
            text = recentErrors.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Live sensor values
        Text(
            text = "LIVE SENSOR VALUES:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Power: ${sensorReading.powerWatts.toInt()}W | " +
                   "Cadence: ${sensorReading.cadenceRpm.toInt()}RPM | " +
                   "Resistance: ${sensorReading.resistancePercent.toInt()}% | " +
                   "HR: ${sensorReading.heartRateBpm ?: "--"}BPM",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricDisplay(label = "Power", value = sensorReading.powerWatts.toInt().toString(), unit = "W", color = PowerCoral)
            MetricDisplay(label = "Cadence", value = sensorReading.cadenceRpm.toInt().toString(), unit = "RPM", color = CadenceCyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricDisplay(label = "HR", value = sensorReading.heartRateBpm?.toString() ?: "--", unit = "BPM", color = HeartRateGreen)
            MetricDisplay(label = "Time", value = "00:00", unit = "", color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onEndRide,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PowerCoral
            )
        ) {
            Text(
                text = "End Ride",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}