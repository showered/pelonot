package com.pelonot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.domain.model.RidePosition
import com.pelonot.ui.theme.spacing

/**
 * Standing or seated, drawn (PLAN 25.2).
 *
 * The rule this file exists to hold: **an absent position is not a third
 * value.** Nothing here draws anything when the interval does not prescribe
 * one — every composable takes a nullable and renders nothing for null — so
 * silence stays silence and never becomes "either, your choice", which would
 * be the app talking when the class deliberately is not.
 */

/** The quiet form, for the class detail interval list. */
@Composable
fun PositionChip(position: RidePosition, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = position.instruction },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = position.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = position.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * The ride-screen form: **the change is the message, not the state** (25.2.2).
 *
 * A rider who has been standing for two minutes does not need telling; a rider
 * who has to stand *now* does. So the call announces itself when the
 * prescription changes and then settles into something small and still. It is
 * keyed on the value rather than on the interval index, because consecutive
 * intervals that both say "seated" are not a change and must not re-announce —
 * `CLB-06` alternates climb and attack six times and would otherwise flash
 * twelve.
 *
 * Nothing moves while the prescription is unchanged, which is the rule 25.3.1
 * asks the overlay to follow and this screen may as well follow too.
 */
@Composable
fun RidePositionCall(
    position: RidePosition?,
    modifier: Modifier = Modifier,
    announceMs: Int = 3500
) {
    if (position == null) return

    val emphasis = remember { Animatable(0f) }
    LaunchedEffect(position) {
        emphasis.snapTo(1f)
        emphasis.animateTo(0f, animationSpec = tween(announceMs, easing = LinearEasing))
    }
    val heat = emphasis.value

    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = modifier
            .graphicsLayer {
                // Grows a little while it is new, then returns to its resting
                // size. Never below 1f: this must not shrink out of the layout
                // and take the row's height with it.
                val scale = 1f + 0.12f * heat
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .background(
                accent.copy(alpha = 0.10f + 0.35f * heat),
                RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.25f + 0.6f * heat),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = 6.dp)
            .semantics { contentDescription = position.instruction },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = position.icon,
            contentDescription = null,
            tint = lerpToward(accent, MaterialTheme.colorScheme.onBackground, heat),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        Text(
            text = position.instruction.uppercase(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = lerpToward(accent, MaterialTheme.colorScheme.onBackground, heat)
        )
    }
}

private val RidePosition.icon
    get() = when (this) {
        RidePosition.Standing -> Icons.Filled.ArrowUpward
        RidePosition.Seated -> Icons.Filled.ArrowDownward
    }

private fun lerpToward(from: Color, to: Color, t: Float) =
    androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))
