package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.components.BirthYearPicker
import com.pelonot.ui.components.birthYearToMillis
import com.pelonot.ui.components.millisToBirthYear
import com.pelonot.domain.model.FitnessLevel
import com.pelonot.domain.model.NewProfile
import com.pelonot.domain.model.FtpEstimator
import com.pelonot.domain.model.UnitSystem
import com.pelonot.ui.theme.WideGrid
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units
import java.text.DateFormat
import java.util.Date

/**
 * The first thing the app ever says to a rider (PLAN 20.3).
 *
 * It replaces an `AlertDialog` with three stacked text fields, the third of
 * which was labelled `FTP (Watts)` and prefilled with a number. In the owner's
 * words that version *"can't go into production"*, and the reason is that
 * **nobody in their right mind knows their FTP** — so the app's opening
 * question was one its rider could only answer by guessing into a box the app
 * would then treat as fact.
 *
 * Three steps, and the shape of them is the design:
 *
 * 1. **A name.** The one genuinely unavoidable text field. Nothing else on
 *    screen, because a first screen carrying five controls is how the previous
 *    version came to be the least designed surface in the app.
 * 2. **Three things a person knows about themselves** — how much they weigh,
 *    when they were born, and which of three sentences describes their riding.
 *    No keyboard on any of them except the weight, and no jargon on any of
 *    them at all: this is a rider being asked about themselves rather than
 *    reading a measurement, which is CLAUDE.md's standing rule about where a
 *    unit belongs.
 * 3. **The number the app worked out**, said once, with where it came from
 *    (20.3.4) and what is going to happen to it (20.3.5).
 *
 * **There is no Skip**, and that is 20.3.2's decision rather than an omission.
 * A skip in front of the questions makes them feel optional and hands Route A's
 * single default to everybody in a hurry. The escape is on the *answer*
 * instead — *"Set it myself"* on step 3 — so it is reached only by a rider who
 * has seen the estimate and disagrees with it, which is exactly the rider who
 * should be typing a number.
 *
 * Laid out for the bike's 1280 × 720 dp landscape panel (HARDWARE.md): the
 * steps use the width where they have a set of things to show — the three
 * descriptions are a [WideGrid] — and the prose is capped with [readableText],
 * which is 22.4.3's "capped column inside a wider frame" rather than a
 * `readableColumn` over the whole screen.
 *
 * **A fourth step on a build with a cloud** (15.8.1): once the number is
 * accepted, the profile is written immediately — never held back for what
 * happens next — and [accountOffer] offers to back it up. Skipping it is as
 * first-class as answering the three questions above; this screen's job was
 * always "get the rider onto the bike", and an account is something offered
 * to them on the way, not a second gate.
 */
@Composable
fun ProfileCreationScreen(
    onProfileCreated: (NewProfile) -> Unit,
    onCancel: () -> Unit,
    /**
     * Offered after the profile exists, never before (15.8.1). Null on a build
     * with no cloud configured, which is 15.8.7 — a self-hoster's app must not
     * advertise a backup it cannot perform.
     */
    accountOffer: (@Composable (onDone: () -> Unit) -> Unit)? = null,
    /**
     * Called once the account offer is finished — skipped, linked, or backed
     * out of. Unused when [accountOffer] is null: there, [onProfileCreated]
     * alone is the caller's signal that this screen is done, exactly as
     * before 15.8 (see [PostRideSummaryScreen], which never passes an offer).
     */
    onAccountOfferFinished: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis()
) {
    var step by remember { mutableStateOf(Step.Name) }

    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf<Long?>(null) }
    var level by remember { mutableStateOf<FitnessLevel?>(null) }

    // Opens on whatever the rest of the app is using, which on a fresh install
    // is the locale's guess. 13.8: the unit is asked, not assumed — a 77 kg
    // rider was once stored as 34.9 kg by this screen's ancestor.
    val preferredUnits = MaterialTheme.units
    var weightUnits by remember(preferredUnits) { mutableStateOf(preferredUnits) }

    val weightKg = weight.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?.let(weightUnits::weightToKg)
    val ageYears = FtpEstimator.ageYearsAt(birthDate, nowMillis)
    val estimate = FtpEstimator.estimate(weightKg, level, ageYears)

    // The escape hatch of 20.3.2, and it is deliberately null until the rider
    // opens it: a typed value that merely *equals* the estimate is still a
    // different claim about where the number came from.
    var typedFtp by remember { mutableStateOf<String?>(null) }
    val typedWatts = typedFtp?.toIntOrNull()?.takeIf { it in MIN_TYPED_FTP..MAX_TYPED_FTP }

    val resolvedFtp = typedWatts ?: estimate ?: UserEntity.DEFAULT_FTP
    val resolvedSource =
        if (typedWatts != null) FtpChangeSource.ProfileCreated
        else if (estimate != null) FtpChangeSource.Estimated
        // Neither: the rider gave no weight or no description, so the app is
        // falling back to the one number it has always had. That is not an
        // estimate and must not claim to be one.
        else FtpChangeSource.ProfileCreated

    fun finish() = onProfileCreated(
        NewProfile(
            name = name.trim(),
            weightKg = weightKg,
            ftpWatts = resolvedFtp,
            ftpSource = resolvedSource,
            birthDate = birthDate,
            fitnessLevel = level
        )
    )

    StepScaffold(
        heading = step.heading(name),
        subheading = step.subheading(),
        onBack = if (step == Step.Name) onCancel else ({ step = step.previous() }),
        backLabel = if (step == Step.Name) "Cancel" else "Back"
    ) {
        when (step) {
            Step.Name -> NameStep(
                name = name,
                onNameChange = { name = it },
                onContinue = { step = Step.About }
            )

            Step.About -> AboutStep(
                weight = weight,
                onWeightChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                weightUnits = weightUnits,
                onUnitsChange = { weightUnits = it },
                birthDate = birthDate,
                onBirthDateChange = { birthDate = it },
                level = level,
                onLevelChange = { level = it },
                onContinue = { step = Step.Result }
            )

            Step.Result -> ResultStep(
                watts = resolvedFtp,
                isEstimate = resolvedSource == FtpChangeSource.Estimated,
                level = level,
                usedAge = ageYears != null,
                typedFtp = typedFtp,
                onTypedFtpChange = { typedFtp = it?.filter(Char::isDigit) },
                onCreate = {
                    // 15.8.1: the profile is written the moment the rider
                    // leaves this step, whether or not an account offer
                    // follows — never held back for what happens next, so a
                    // rider who walks away mid-offer still has a rideable
                    // bike with a real profile on it.
                    finish()
                    if (accountOffer != null) step = Step.Account
                },
                createLabel = if (accountOffer != null) "Continue" else "Start riding"
            )

            Step.Account -> accountOffer?.invoke(onAccountOfferFinished)
        }
    }
}

private enum class Step {
    Name, About, Result, Account;

    fun previous(): Step = when (this) {
        Name -> Name
        About -> Name
        Result -> About
        // Going back from the account offer would re-ask a question the rider
        // has already answered about a profile that, by 15.8.1, already exists.
        Account -> Account
    }

    fun heading(name: String): String = when (this) {
        Name -> "Who's riding?"
        About -> if (name.isBlank()) "A bit about you" else "A bit about you, ${name.trim()}"
        Result -> "Here's where we'll start you"
        Account -> "Keep your rides safe"
    }

    fun subheading(): String? = when (this) {
        Name -> "This is the name on the bike, not an account. You can change it later."
        About -> "Three things, and then you're riding. None of them leave this bike."
        Result -> null
        Account -> null
    }
}

/**
 * The frame every step shares: a heading, the step's own content, and a way
 * back.
 *
 * The content is **vertically centred when it does not fill the panel**, which
 * is 22.7.1's rule arriving on a fourth screen. Top-aligning a two-control step
 * on a 720 dp panel hangs it off the heading with a hole underneath, and this
 * screen has steps of very different heights.
 */
@Composable
private fun StepScaffold(
    heading: String,
    subheading: String?,
    onBack: () -> Unit,
    backLabel: String,
    content: @Composable () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.doubleExtraLarge)
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.readableText()
            )
            if (subheading != null) {
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                Text(
                    text = subheading,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readableText()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            TextButton(onClick = onBack) { Text(backLabel) }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 560.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
        Button(
            onClick = onContinue,
            enabled = name.isNotBlank(),
            modifier = Modifier.width(PRIMARY_BUTTON_WIDTH)
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutStep(
    weight: String,
    onWeightChange: (String) -> Unit,
    weightUnits: UnitSystem,
    onUnitsChange: (UnitSystem) -> Unit,
    birthDate: Long?,
    onBirthDateChange: (Long?) -> Unit,
    level: FitnessLevel?,
    onLevelChange: (FitnessLevel) -> Unit,
    onContinue: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        BirthYearPicker(
            currentSelection = millisToBirthYear(birthDate),
            onSelected = {
                onBirthDateChange(birthYearToMillis(it))
                picking = false
            },
            onDismiss = { picking = false }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 900.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text("Weight (${weightUnits.weightLabel})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                // 13.8 again: the unit is the rider's to choose, and this is
                // still the only route to one before a profile exists.
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                    UnitSystem.entries.forEach { option ->
                        FilterChip(
                            selected = weightUnits == option,
                            onClick = { onUnitsChange(option) },
                            label = { Text(option.weightLabel) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    // The year, not a date, because the year is what was
                    // asked and echoing back "1 January 1986" would claim the
                    // rider told the app their birthday.
                    text = millisToBirthYear(birthDate)?.let { "Born in $it" }
                        ?: "What year were you born?"
                )
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacing.doubleExtraLarge))

        Text(
            text = "How would you describe your riding?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(MaterialTheme.spacing.large))

        // Three things in a set, looked at rather than read — WideGrid, not a
        // capped column (22.4). Equal heights because one description wraps to
        // two lines and a ragged row reads as a mistake (22.7.2).
        WideGrid(
            items = FitnessLevel.entries,
            minCellWidth = 260.dp,
            equalHeightRows = true,
            modifier = Modifier.widthIn(max = 1000.dp)
        ) { option ->
            FitnessLevelCard(
                level = option,
                selected = level == option,
                onSelect = { onLevelChange(option) }
            )
        }

        Spacer(Modifier.height(MaterialTheme.spacing.doubleExtraLarge))

        Button(
            onClick = onContinue,
            // Weight and a description are what the estimate multiplies, so
            // without both there is nothing to show on the next step. The date
            // is genuinely optional — it only adjusts a number that already
            // exists — which is why it is not in this condition.
            enabled = level != null && weight.toDoubleOrNull()?.let { it > 0.0 } == true,
            modifier = Modifier.width(PRIMARY_BUTTON_WIDTH)
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FitnessLevelCard(
    level: FitnessLevel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)) {
            Text(
                text = level.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = level.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * The number, said once, with where it came from and what happens to it.
 *
 * This is the only screen in the app where a watt appears beside a rider who
 * is making a choice rather than reading a measurement, and it earns the
 * exception: the whole point of 20.3.4 is that the rider must be able to see
 * this is an estimate and not something they told the app. An estimate the
 * rider cannot see is one they will never correct.
 */
@Composable
private fun ResultStep(
    watts: Int,
    isEstimate: Boolean,
    level: FitnessLevel?,
    /**
     * Whether the age term actually applied — the birth year is optional, and
     * the caption must not name an input the estimate did not use.
     */
    usedAge: Boolean,
    typedFtp: String?,
    onTypedFtpChange: (String?) -> Unit,
    onCreate: () -> Unit,
    createLabel: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 640.dp)
    ) {
        Text(
            text = "$watts W",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))

        Text(
            text = if (isEstimate) {
                // 20.3.4: it must be visibly a calculation from things the
                // rider said, not a number the app is claiming they gave it.
                //
                // And it names only the inputs that were *used*. The birth year
                // is optional, and the first version said "from your weight,
                // your age, and …" to a rider who had skipped it — which is a
                // small lie of exactly the kind this screen exists to stop
                // telling. Seen on the tablet AVD, not reasoned about.
                val basis = level?.estimateBasis ?: "riding"
                if (usedAge) {
                    "Our estimate, from your weight, your age, and that you're $basis."
                } else {
                    "Our estimate, from your weight and that you're $basis."
                }
            } else {
                "Your number."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.readableText()
        )

        if (isEstimate) {
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
            Text(
                // 20.3.5. The estimate's whole defence is that it is temporary,
                // and a rider who is never told the app will correct it has
                // been given a wrong number rather than a starting point.
                text = "We'll work it out properly from your first few rides.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.readableText()
            )
        }

        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))

        if (typedFtp == null) {
            // The escape, and its placement is 20.3.2's decision: after the
            // answer rather than in front of the questions, so it is reached
            // only by a rider who has seen the estimate and disagrees.
            TextButton(onClick = { onTypedFtpChange("") }) {
                Text("I know my FTP — set it myself")
            }
        } else {
            OutlinedTextField(
                value = typedFtp,
                onValueChange = { onTypedFtpChange(it) },
                label = { Text("FTP (watts)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Between $MIN_TYPED_FTP and $MAX_TYPED_FTP") },
                isError = typedFtp.isNotBlank() &&
                    typedFtp.toIntOrNull()?.let { it in MIN_TYPED_FTP..MAX_TYPED_FTP } != true,
                modifier = Modifier.width(240.dp)
            )
            TextButton(onClick = { onTypedFtpChange(null) }) {
                Text("Use the estimate instead")
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))

        Button(onClick = onCreate, modifier = Modifier.width(PRIMARY_BUTTON_WIDTH)) {
            Text(createLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A control-sized button rather than a band across the panel (22.6).
 *
 * The same correction the Start Class screen needed: a primary action stretched
 * to 1200 dp reads as a banner and not as a thing to press.
 */
private val PRIMARY_BUTTON_WIDTH = 420.dp

/**
 * Bounds on a *typed* FTP, and they are a fence rather than a clamp: an
 * out-of-range value is refused, never quietly corrected. Same argument as
 * `TelemetryBounds`.
 */
private const val MIN_TYPED_FTP = 40
private const val MAX_TYPED_FTP = 600

/**
 * Hosts [ProfileCreationScreen] full-bleed over whatever called it.
 *
 * Kept as a dialog rather than a nav destination because both call sites —
 * the profile selector and a guest keeping their ride from the post-ride
 * summary — raise it *conditionally over a screen they must return to*, and a
 * nav destination would need a back stack entry each and a result channel to
 * carry the answer home. `usePlatformDefaultWidth = false` is what makes it a
 * screen: without it Compose caps a dialog at a phone's width and the whole
 * point of 20.3.3 is lost on a 1280 dp panel.
 */
@Composable
fun ProfileCreationDialog(
    onProfileCreated: (NewProfile) -> Unit,
    onDismiss: () -> Unit,
    accountOffer: (@Composable (onDone: () -> Unit) -> Unit)? = null,
    onAccountOfferFinished: () -> Unit = onDismiss
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ProfileCreationScreen(
            onProfileCreated = onProfileCreated,
            onCancel = onDismiss,
            accountOffer = accountOffer,
            onAccountOfferFinished = onAccountOfferFinished
        )
    }
}
