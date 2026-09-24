package com.pelonot.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pelonot.domain.model.StrapBattery

@Composable
fun StrapBatteryLabel(percent: Int, modifier: Modifier = Modifier) {
    Text(
        text = StrapBattery.label(percent),
        style = MaterialTheme.typography.labelMedium,
        color = if (StrapBattery.isLow(percent)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
