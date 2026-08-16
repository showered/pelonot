package com.pelonot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.chart.ClassProfile
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.spacing

/**
 * A whole class at a glance — height for zone, width for time (PLAN 22.7.2).
 *
 * **This is the one thing on the class detail screen that is *looked at* rather
 * than read**, so it takes the full panel (22.4) while the prose beside it
 * stays capped. It is drawn at the width of the tablet on purpose: time is the
 * horizontal axis, and a thirty-minute class squeezed into 760 dp loses the
 * thing it exists to show, which is how long the work goes on for relative to
 * the recoveries.
 *
 * **No value axis, and that is deliberate.** The vertical is a zone ordering,
 * not a scale — the gap between Z1 and Z2 is not the gap between Z6 and Z7 in
 * watts, and drawing gridlines would claim it was. Three clock labels
 * underneath, matching `ChartFrame`'s idiom (16.1.8), and the zones are
 * identified by the colours the whole app already uses for them.
 *
 * A screen reader gets the sentence, never the canvas (16.2.4).
 */
@Composable
fun ClassProfileChart(
    profile: ClassProfile,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    /**
     * The three clock labels under the drawing (22.9.4).
     *
     * On the class detail screen they are the axis and belong there. On the
     * dashboard's *Ride this* card the same drawing is a **glance** beside a
     * line that already says `30 min`, and three more times under it is the
     * screen answering one question twice — which is Phase 26's rule and also
     * 22.9.1's, since a label row is height that adds no information.
     */
    showClock: Boolean = true
) {
    if (profile.blocks.isEmpty()) return

    val zoneColors = remember(profile) { profile.blocks.map { it.zone.color } }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = profile.summary }
        ) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                // A hairline the blocks stand on, so a class opening in zone 1
                // reads as a low bar rather than as an empty left-hand edge.
                drawRect(
                    color = trackColor,
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx())
                )
                profile.blocks.forEachIndexed { index, block ->
                    drawBlock(
                        left = block.startFraction * size.width,
                        width = block.widthFraction * size.width,
                        heightFraction = block.heightFraction,
                        color = zoneColors[index]
                    )
                }
            }
        }

        if (showClock) {
            Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
            Row(Modifier.fillMaxWidth()) {
                ClockLabel(Formatters.duration(0))
                Spacer(Modifier.weight(1f))
                ClockLabel(Formatters.duration(profile.totalSec / 2))
                Spacer(Modifier.weight(1f))
                ClockLabel(Formatters.duration(profile.totalSec))
            }
        }
    }
}

/**
 * One block, inset by half a pixel of gap on each side.
 *
 * The gap is what makes two adjacent blocks at the same zone readable as two —
 * `The Long Climb 30` has a pair of tempo blocks that differ only in cadence,
 * and without the seam they draw as one long bar that misdescribes the class.
 * It is subtracted rather than added so the profile still ends exactly at the
 * right-hand edge.
 */
private fun DrawScope.drawBlock(
    left: Float,
    width: Float,
    heightFraction: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val gap = 1.dp.toPx()
    val drawn = (width - gap).coerceAtLeast(1f)
    val barHeight = size.height * heightFraction
    drawRoundRect(
        color = color,
        topLeft = Offset(left, size.height - barHeight),
        size = Size(drawn, barHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
}

@Composable
private fun ClockLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
