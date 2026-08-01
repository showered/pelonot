package com.pelonot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * The overlay's form — **the one thing on the HUD that is allowed to move for
 * its own sake, and only while it is new** (25.3.1).
 *
 * Everything else on the strip has been designed *not* to compete for
 * attention: the rider is watching a film and the HUD is furniture at the edge
 * of it. This is the exception, and it earns it by being the only instruction
 * on the whole surface that has to be acted on the instant it arrives. A zone
 * or a cadence is something to settle into over the next thirty seconds; "out
 * of the saddle" is now.
 *
 * Which is exactly why it has to leave. The rule, written before any of it was
 * built: **animate for the transition, then go quiet.** A persistent arrow on
 * a five-minute standing block is a moving object in the corner of somebody's
 * film for five minutes, and it would undo the whole of 11.1b.
 *
 * So [call] is an **edge**, not a state — the caller passes a position only for
 * the few seconds after it changes, and null the rest of the time (see
 * `PositionCallTracker`, which is also what the spoken coach asks). Nothing
 * here reads "the current interval says standing", and nothing here can.
 *
 * Amber rather than the zone accent, for two reasons this surface has already
 * learned: the zone colour is the interval-change flash and this is a different
 * message, and **Zone 1's colour is grey** (11.1b.10), so a warm-up that
 * prescribed a position would announce it in the colour of a stray divider.
 *
 * @param animate false when the rider has turned motion off (`CoachStyle.Off`).
 *   The lozenge still appears — the instruction is the class speaking, not the
 *   app tapping them on the shoulder — it simply holds still.
 */
@Composable
fun HudPositionCall(
    call: RidePosition?,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    AnimatedVisibility(
        visible = call != null,
        // Springs in from small, like the cue band it sits beside. It cannot
        // slide: the strip is docked against a screen edge and anything that
        // translates towards that edge reads as a rendering fault.
        enter = fadeIn() + scaleIn(
            initialScale = 0.7f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        // Held across the exit animation, so the lozenge does not blank its own
        // text on the way out.
        val shown = remember { mutableStateOf(call) }
        call?.let { shown.value = it }
        val position = shown.value ?: return@AnimatedVisibility

        val amber = MaterialTheme.colorScheme.tertiary
        // The arrow travels in the direction of the instruction and fades as it
        // goes — up out of the saddle, down into it. A rider glancing sideways
        // gets the direction before they get the word.
        val travel = if (animate) rememberPulse(periodMs = 620, from = 0f, to = 1f) else 0f
        val rise = if (position == RidePosition.Standing) -travel else travel

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(amber)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .semantics { contentDescription = position.instruction },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = position.icon,
                    contentDescription = null,
                    // Black on amber, for the same reason the cue band is: every
                    // attention colour in this palette is a bright one, so dark
                    // type is the readable direction over any scene behind it.
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            translationY = rise * 7.dp.toPx()
                            alpha = 1f - 0.55f * travel
                        }
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = position.instruction.uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.Black.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private val RidePosition.icon
    get() = when (this) {
        RidePosition.Standing -> Icons.Filled.ArrowUpward
        RidePosition.Seated -> Icons.Filled.ArrowDownward
    }

private fun lerpToward(from: Color, to: Color, t: Float) =
    androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))
