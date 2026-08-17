package com.pelonot.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelonot.domain.identity.AvatarFace
import com.pelonot.domain.identity.AvatarPhoto
import com.pelonot.domain.identity.AvatarPaint
import com.pelonot.ui.theme.AvatarPalette
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Choosing a face (20.2.1, 20.2.3, 20.6.1).
 *
 * **A colour and, if the rider wants one, a face** — two rows rather than a
 * grid of every combination, because eight colours times twenty faces is a
 * hundred and sixty tiles and a decision nobody asked for. Phase 26's rule is
 * about saying less and it applies to controls as much as to sentences (26.3):
 * this is a *pick*, so it is allowed more than three answers, but the ceiling
 * is how many things are told apart at a glance rather than how many exist.
 *
 * **Twenty faces at the owner's own choice** (20.6.1), and they are drawn
 * people rather than the six Material icons this dialog used to offer — *"the
 * ones we have are not good"*. Each swatch shows the face on the colour that is
 * currently chosen, so the row is a preview of the outcome rather than a
 * catalogue: changing the colour re-tints all twenty at once.
 *
 * **The rider's own initial is the first option in the face row and it is
 * selected by default.** It is not an absence dressed up as a choice: an
 * initial is unambiguous between two housemates with different names, and a
 * face is what serves the household where two names start with the same letter
 * — the one case an initial genuinely cannot.
 *
 * Nothing here is a required step. A rider who never opens this dialog has a
 * face already, derived from their row id, and the column stays null so the app
 * can still tell that they never chose.
 *
 * **A photograph is not one of the answers here, and that is 20.7.1.** It used
 * to be the second swatch in the face row — a door into the system gallery —
 * and the owner's note of 18 August 2026 took it out of this app: a bike has no
 * camera and therefore no camera roll, so the door opened onto an empty room.
 * Setting one belongs to the companion web app (20.2.8). A rider who already
 * has one still wears it everywhere the app draws a face, and tapping anything
 * in this row is still how they take it off.
 *
 * **It lives here rather than inside a screen** (20.6.2). Two screens draw it
 * now — the selector's press-and-hold dialog and the last step of profile
 * creation — and a private copy in one of them is exactly how the avatar came
 * to be drawn off the power-zone palette in the first place. `RiderAvatar`'s
 * own KDoc tells that story; this file is the same lesson applied one layer up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(
    paint: AvatarPaint,
    face: AvatarFace?,
    onPaint: (AvatarPaint) -> Unit,
    onFace: (AvatarFace?) -> Unit,
    /**
     * The name whose initial the first swatch shows.
     *
     * It is the rider's own rather than a stand-in `A`, because that swatch is
     * the *option of having no face* and a rider has to be able to see what
     * that option looks like for them. A generic letter on a screen where the
     * preview above it says `S` is two answers to one question.
     */
    name: String = "",
    /**
     * The rider's photograph, if they are wearing one.
     *
     * **Nothing here can set it** (20.7.1) — a photograph is chosen on the
     * companion web app now, and this parameter exists because a rider who has
     * one is not currently wearing any of the options below. It is what keeps
     * the initial and the twenty faces all unselected while a picture is on the
     * disc, so the row is honest about the fact that their face came from
     * somewhere else.
     */
    photo: AvatarPhoto? = null,
    /**
     * Called by *this* component whenever a drawn face or the initial is
     * chosen, because a photograph and a face are one answer and the rider has
     * just given the other one.
     *
     * It lives here rather than in the caller's `onFace` handler so that the
     * two cannot come apart: forgetting to clear a face under a photograph is
     * invisible — `Avatar.store` and [RiderAvatar] both prefer the photograph —
     * but forgetting to clear the *photograph* would leave a rider tapping face
     * after face while their picture stays on the disc, which reads as the app
     * having stopped responding.
     */
    onDropPhoto: () -> Unit = {}
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        AvatarPaint.entries.forEach { option ->
            Swatch(
                selected = option == paint,
                fill = AvatarPalette[option.ordinal],
                label = "Colour ${option.ordinal + 1}",
                onClick = { onPaint(option) }
            ) {}
        }
    }

    Spacer(Modifier.size(MaterialTheme.spacing.small))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        // The initial first, because it is what the rider already has.
        Swatch(
            selected = face == null && photo == null,
            fill = AvatarPalette[paint.ordinal],
            label = "Your initial",
            size = FACE_SWATCH,
            onClick = {
                onDropPhoto()
                onFace(null)
            }
        ) {
            Text(
                text = name.take(1).uppercase().ifBlank { "A" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        AvatarFace.entries.forEach { option ->
            Swatch(
                selected = option == face && photo == null,
                fill = AvatarPalette[paint.ordinal],
                // The ids are seeds and mean nothing to a rider (`lark`,
                // `dune`), so a screen reader is told the position instead:
                // there is no honest name for a drawn stranger's face, and
                // inventing one would name a person the app knows nothing
                // about.
                label = "Face ${option.ordinal + 1}",
                size = FACE_SWATCH,
                onClick = {
                    onDropPhoto()
                    onFace(option)
                }
            ) {
                Image(
                    painter = painterResource(option.drawable),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * One option in the picker.
 *
 * **The selection is a ring with a gap inside it, and the gap is the whole
 * fix.** The first version drew the ring as a border *on* the disc, in the
 * brand teal — which was legible on the mark row, where every swatch is a dark
 * grey, and very nearly invisible on the colour row, because one of the eight
 * colours *is* a turquoise and a teal ring on it reads as an edge rather than
 * as a choice. Watched on the tablet AVD and it is the state a rider is in most
 * often: the swatch they are looking at is the one they already have. Insetting
 * the fill lets the dialog's own surface show between the ring and the colour,
 * so the signal is the **separation** rather than a hue that some of the
 * options can defeat.
 *
 * A tick on top was the other candidate and is worse here: it hides part of the
 * thing being chosen. 48 dp is the touch target the rest of this screen is
 * built to — 20.1.2's tile floor exists for the same reason, a thumb on a bike.
 */
@Composable
private fun Swatch(
    selected: Boolean,
    fill: Color,
    label: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.expressiveShapes.pill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.expressiveShapes.pill
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // Unselected fills to the outline, so only the chosen one
                // changes shape and the row does not shimmer as it is scanned.
                .size(if (selected) size - SWATCH_INSET else size)
                .clip(MaterialTheme.expressiveShapes.pill)
                .background(fill),
            contentAlignment = Alignment.Center,
            content = { content() }
        )
    }
}

/**
 * The face swatches are larger than the colour ones, and the reason is that
 * they carry a drawing rather than a flat fill. At 48 dp — the colour row's
 * size, and the touch target the profile selector is built to — a selected Open
 * Peeps figure is inset to 38 dp and stops being a person you can tell from the
 * one beside it, which is the entire basis on which the set was chosen
 * (`avatars/README.md`).
 */
private val FACE_SWATCH = 64.dp

/** How far the fill is inset when a swatch is the chosen one (20.2.3a). */
private val SWATCH_INSET = 10.dp
