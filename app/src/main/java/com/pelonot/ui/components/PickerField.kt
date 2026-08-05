package com.pelonot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * A question answered by tapping, drawn as the field beside it (PLAN 20.4.1,
 * 20.4.5).
 *
 * **The owner watched somebody meet this app for the first time** and reported
 * that *"year born and weight inputs are different, visually"*. They were: the
 * weight is an `OutlinedTextField` — a labelled box with a border — and the
 * birth year was an `OutlinedButton` whose entire content was the question
 * itself, at a different height, a different corner radius, with the text
 * centred rather than left and no label anywhere. Side by side in one row they
 * read as **a field and an action**, when they are two facts about the rider.
 *
 * That is not a cosmetic complaint. A first-time rider does not know the year
 * control is a picker, so a control that does not look like the thing next to
 * it is the screen failing to say what kind of answer it wants.
 *
 * **The first version of this drew the box itself, and the owner reported the
 * pair out of alignment a second time (20.4.5).** Copying Material's numbers —
 * 56 dp, the extra-small corner, a 1 dp outline — is not the same as being the
 * thing: an `OutlinedTextField` reserves space *above* its box for the label to
 * sit on the border when it floats, so at the same y in the same row its border
 * started 12 px lower and ended 12 px lower than a hand-drawn 56 dp box.
 * Measured on the tablet AVD: the field is 64 dp tall and the copy was 56 dp.
 * Nothing in the source said so, and nothing could — the reserve is half the
 * label's own measured height, so it moves with the font scale and cannot be
 * typed in as a constant.
 *
 * So this is Material's own decoration, `OutlinedTextFieldDefaults
 * .DecorationBox`, with a `Text` where the text field's editor would be. The
 * two controls are then the same component drawn twice, and the label floats
 * onto the border when the question is answered exactly as the weight's does.
 *
 * **Why not `OutlinedTextField` with `readOnly = true`.** That is the usual
 * trick and it costs more than it saves here: a read-only field still takes
 * focus and shows a caret, so the rider is given a text cursor in a box they
 * cannot type into — and on this particular step a stray keyboard is the
 * *other* fault in the same note (20.4.2). A decoration box with no editor in
 * it cannot summon an IME by construction, which on this screen is a property
 * worth having rather than a detail.
 *
 * **It is a button, and it says so.** `Role.Button` rather than a text field's
 * semantics, because that is what it does — the alternative would tell a screen
 * reader the rider can type here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerField(
    /**
     * The question, in the place a text field puts its label: centred in the
     * box until it is answered, on the border afterwards. That is not a style
     * choice — it is what the field beside it does, and the pair being the same
     * in *every* state is the whole point of the control.
     */
    label: String,
    /** The answer, or null if there is not one yet. */
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The click lives on the outer box rather than inside the decoration, so
    // the whole field is the target — including the outline and the padding a
    // rider aiming at a 375 dp control will actually hit.
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()

    Box(
        // The decoration box takes no modifier of its own, so the width the
        // caller gave this control has to reach it as a *minimum* constraint or
        // it draws at its own intrinsic width and the label spills out of the
        // outline — which is what happened the first time this was measured.
        propagateMinConstraints = true,
        modifier = modifier
            // The reserve, in the order `OutlinedTextField` applies it: padding
            // first, then the minimum. It is 8 **sp**, not dp — the room a
            // floated label needs is a property of the text, so it grows with
            // the rider's font scale and cannot be a dp constant. Material
            // keeps this value private, which is the one thing here that has to
            // be copied rather than called.
            .padding(top = with(LocalDensity.current) { LABEL_FLOAT_RESERVE.toDp() })
            .defaultMinSize(
                minWidth = OutlinedTextFieldDefaults.MinWidth,
                minHeight = OutlinedTextFieldDefaults.MinHeight
            )
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value.orEmpty(),
            innerTextField = {
                Text(
                    text = value.orEmpty(),
                    // The editor's own style, set here because a decoration box
                    // decorates whatever it is given and does not impose one.
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            // Never focused, so the outline keeps its resting colour — which is
            // right for a control that hands off to a dialog rather than
            // holding a caret.
            interactionSource = interactionSource,
            label = {
                Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            colors = colors,
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors
                )
            }
        )
    }
}

/**
 * `OutlinedTextFieldTopPadding`, transcribed. Material declares it `internal`,
 * so a control that wants to stand beside a text field has to carry its own
 * copy — which is why 20.4.5 is a *measurement* on the AVD rather than a
 * reading of this file: if Material moves it, only the screen will say so.
 */
private val LABEL_FLOAT_RESERVE = 8.sp
