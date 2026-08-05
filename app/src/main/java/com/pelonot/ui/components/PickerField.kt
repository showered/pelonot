package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelonot.ui.theme.spacing

/**
 * A question answered by tapping, drawn as the field beside it (PLAN 20.4.1).
 *
 * **The owner watched somebody meet this app for the first time** and reported
 * that *"year born and weight inputs are different, visually"*. They are: the
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
 * **Why this is not simply an `OutlinedTextField` with `readOnly = true`.**
 * That is the usual trick and it costs more than it saves here: a read-only
 * field still takes focus and shows a caret, so the rider is given a text
 * cursor in a box they cannot type into — and on this particular step a stray
 * keyboard is the *other* fault in the same note (20.4.2). Drawing the
 * container directly means the control cannot summon an IME by construction,
 * which on this screen is a property worth having rather than a detail.
 *
 * **It is a button, and it says so.** `Role.Button` rather than a text field's
 * semantics, because that is what it does — the alternative would tell a screen
 * reader the rider can type here.
 *
 * The measurements are Material's own text-field metrics rather than invented
 * ones, so the two controls line up at any font scale: a 56 dp minimum height,
 * the extra-small corner, a 1 dp outline, and the label above the value in the
 * same two styles `OutlinedTextField` uses.
 */
@Composable
fun PickerField(
    /** The question, in the place a text field puts its label. Always shown. */
    label: String,
    /**
     * The answer, or null if there is not one yet. Null draws [placeholder] in
     * the dimmed colour — never the question again, which is what the
     * `OutlinedButton` did and is how the control came to have no label at all.
     */
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tap to choose"
) {
    val answered = value != null

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = FIELD_MIN_HEIGHT)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = OUTLINE_WIDTH,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.extraSmall
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (answered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Material's own text-field height, so the two controls line up in a row. */
private val FIELD_MIN_HEIGHT = 56.dp

private val OUTLINE_WIDTH = 1.dp
