package com.pelonot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.LiveHeartRateZones
import com.pelonot.ui.theme.color

/** The empty arc is unrecorded time, including the part of a class still ahead. */
@Composable
fun LiveHeartRateRing(
    zones: LiveHeartRateZones,
    elapsedSec: Int,
    durationSec: Int?,
    bpm: Int?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val fractions = zones.fractions(elapsedSec, durationSec)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val description = "Heart-rate zones, " +
        (durationSec?.let { "$elapsedSec of $it seconds" } ?: "$elapsedSec seconds elapsed") +
        HeartRateZone.entries.joinToString( prefix = ". ") {
            "H${it.number}: ${zones.secondsByZone[it] ?: 0} seconds"
        }
    Box(modifier.size(80.dp).semantics { contentDescription = description }, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val bounds = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, inset, bounds, style = Stroke(stroke))
            var start = -90f
            HeartRateZone.entries.forEach { zone ->
                val sweep = ((fractions[zone] ?: 0f) * 360f).coerceIn(0f, 270f - start)
                if (sweep > 0f) drawArc(zone.color, start, sweep, false, inset, bounds, style = Stroke(stroke))
                start += sweep
            }
        }
        BeatingHeart(bpm = bpm, color = accent, size = 40.dp)
    }
}
