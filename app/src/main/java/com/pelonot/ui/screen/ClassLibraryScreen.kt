package com.pelonot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.columnsFor
import com.pelonot.ui.theme.spacing

/**
 * Browsable class library with category filtering — PLAN.md item 6.5, which
 * called for a filterable list but shipped as a flat unfiltered one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassLibraryScreen(
    classes: List<ClassPlan>,
    onClassSelected: (ClassPlan) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val categories = remember(classes) {
        classes.map { it.category }.distinct().sorted()
    }
    val visibleClasses = remember(classes, selectedCategory) {
        selectedCategory?.let { category -> classes.filter { it.category == category } }
            ?: classes
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Class Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // 22.4.3, and the owner's rule of 4 August. 72 classes in one 760 dp
        // column is a lot of scrolling past a lot of empty tablet; three across
        // shows most of a category at once, which is what choosing a class
        // actually needs.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
        ) {
            if (categories.size > 1) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = { Text(category) }
                        )
                    }
                }
                Spacer(Modifier.size(MaterialTheme.spacing.small))
            }

            if (visibleClasses.isEmpty()) {
                EmptyLibraryMessage(hasFilter = selectedCategory != null)
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = columnsFor(
                        available = maxWidth - MaterialTheme.spacing.large * 2,
                        minCellWidth = CLASS_CARD_MIN_WIDTH,
                        spacing = MaterialTheme.spacing.small
                    )
                    val rows = visibleClasses.chunked(columns)

                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = MaterialTheme.spacing.large,
                            vertical = MaterialTheme.spacing.small
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                    ) {
                        // Row-major: the order across a row is the order down a
                        // phone's single column, so the library reads the same
                        // way at every width (see `WideGrid`).
                        items(rows, key = { row -> row.first().id }) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.small
                                )
                            ) {
                                row.forEach { plan ->
                                    ClassCard(
                                        plan = plan,
                                        onClick = { onClassSelected(plan) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage(hasFilter: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.doubleExtraLarge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (hasFilter) {
                "No classes in this category yet."
            } else {
                "No classes are installed. They are seeded from the bundled " +
                    "library on first launch."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassCard(plan: ClassPlan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${plan.title}, ${plan.category}, ${Formatters.minutes(plan.durationSec)}"
            },
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = plan.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plan.isMalformed) {
                    // Surfaced rather than silently rendering an empty class.
                    Text(
                        text = "Interval data unreadable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Text(
                text = Formatters.minutes(plan.durationSec),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.expressiveShapes.pill
                    )
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.extraSmall
                    )
            )
        }
    }
}

/**
 * Three class cards across the bike's panel, one on a phone.
 *
 * A card here is a title, a category and a duration pill — it reads fine at a
 * third of the width, and 72 classes in one column is mostly scrolling.
 */
private val CLASS_CARD_MIN_WIDTH = 340.dp
