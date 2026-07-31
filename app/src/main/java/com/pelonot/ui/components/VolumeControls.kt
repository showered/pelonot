package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.ui.theme.spacing
import kotlin.math.roundToInt

/**
 * The media and coach sliders, shared between Settings and the HUD.
 *
 * One composable rather than two because they must behave identically: the HUD
 * is where a rider *discovers* the film is too loud, and Settings is where they
 * set it deliberately, but a slider that behaved differently in the two places
 * would be a trap on a surface read at a glance mid-effort.
 */
@Composable
fun VolumeSliders(
    mediaVolume: Float,
    coachVolume: Float,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            if (compact) MaterialTheme.spacing.extraSmall else MaterialTheme.spacing.small
        )
    ) {
        VolumeSlider(
            label = "Everything else",
            // The rider does not think "STREAM_MUSIC"; they think of the film
            // they have on.
            hint = "Films, music, and anything else playing on this tablet",
            value = mediaVolume,
            onValueChange = onMediaVolumeChange,
            icon = if (mediaVolume <= 0f) Icons.AutoMirrored.Filled.VolumeOff
            else Icons.AutoMirrored.Filled.VolumeUp,
            compact = compact
        )

        VolumeSlider(
            label = "Coach",
            hint = "Set independently, so the coach can sit under the film rather " +
                "than over it",
            value = coachVolume,
            onValueChange = onCoachVolumeChange,
            icon = if (coachVolume <= 0f) Icons.Default.VoiceOverOff
            else Icons.Default.RecordVoiceOver,
            compact = compact
        )

        // 11.5.7: said out loud rather than left as a slider that moved and
        // changed nothing.
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun VolumeSlider(
    label: String,
    hint: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    compact: Boolean
) {
    val percent = (value.coerceIn(0f, 1f) * 100).roundToInt()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = label,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            // The slider's own value announcement is a bare fraction; this says
            // which volume it is, which is the part that matters when there are
            // two of them on the screen.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "$label volume, $percent percent"
            }
        )

        if (!compact) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
