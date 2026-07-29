 package com.pelonot.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationRail
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.ui.theme.FitnessTypography
import com.pelonot.ui.theme.PelonotGradients
import com.pelonot.ui.theme.elevationTokens
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.iconSizes
import com.pelonot.ui.theme.spacing

// ==========================================
// ExpressiveNavigationRail component
// ==========================================
@Composable
fun ExpressiveNavigationRail(
    destinations: List<NavigationDestination>,
    selectedDestination: NavigationDestination,
    onDestinationClick: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
    elevation: Int = elevationTokens.navigationRailElevation,
    shape: Shape = expressiveShapes.navigationRailShape
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(NavigationRailDefaults.Width)
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape
            ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = { _, _ ->ndiceDestinationItem(
            icon = icons.info,
            label = "Test",
            destination = destinations[0]
        )},
        itemContent = { index, isSelected ->
            val destination = destinations[index]
            val isItemSelected = destination == selectedDestination
                  
            NavigationRailItem(
                selected = isItemSelected,
                onClick = { onDestinationClick(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier
                            .size(iconSizes.navigationRailIcon)
                            .align(Alignment.CenterHorizontally)
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs, bottom = spacing.xs)
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    unselectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xs, vertical = spacing.xs)
            )
        },
        contentPadding = NavigationRailDefaults.ContentPadding
    )
}

// Elevation tokens
object elevationTokens {
    const val navigationRailElevation = 3 // dp
}

// Expressive shapes
object expressiveShapes {
    val navigationRailShape = MaterialTheme.shapes.roundedRect(28.dp)
}
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Smooth spring animation for button press tactile scale-down
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ButtonScale"
    )

    val buttonAlpha = if (enabled) 1.0f else 0.5f

    Box(
        modifier = modifier
            .scale(scale)
            .clip(MaterialTheme.expressiveShapes.pill)
            .background(
                brush = Brush.horizontalGradient(PelonotGradients.TealFlow),
                alpha = buttonAlpha
            )
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null, // Disable default grey ripple in favor of custom spring scale
                onClick = onClick
            )
            .padding(
                horizontal = MaterialTheme.spacing.extraLarge,
                vertical = MaterialTheme.spacing.large
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null && !isLoading) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MaterialTheme.iconSizes.medium)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
            }
            Text(
                text = if (isLoading) "Loading..." else text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// SecondaryButton
// ==========================================
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SecondaryButtonScale"
    )

    val contentColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(MaterialTheme.expressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isPressed) MaterialTheme.colorScheme.primary else borderColor,
                shape = MaterialTheme.expressiveShapes.pill
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = MaterialTheme.spacing.extraLarge,
                vertical = MaterialTheme.spacing.large
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(MaterialTheme.iconSizes.medium)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// HeroCard (Calm, Premium container)
// ==========================================
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.container,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (actionText != null && onActionClick != null) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(MaterialTheme.expressiveShapes.pill)
                            .clickable(onClick = onActionClick)
                            .padding(
                                horizontal = MaterialTheme.spacing.small,
                                vertical = MaterialTheme.spacing.extraSmall
                            )
                    )
                }
            }

            if (content != null) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                content()
            }
        }
    }
}

// ==========================================
// SectionHeader
// ==========================================
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        if (action != null) {
            Box(modifier = Modifier.padding(start = MaterialTheme.spacing.medium)) {
                action()
            }
        }
    }
}

// ==========================================
// MetricCard
// ==========================================
@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onValueClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.expressiveShapes.extraLarge)
            .clickable(enabled = onValueClick != null, onClick = { onValueClick?.invoke() }),
        shape = MaterialTheme.expressiveShapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                // Smooth fade-through animated metric updates
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
                    },
                    label = "MetricValueTransition"
                ) { targetValue ->
                    Text(
                        text = targetValue,
                        style = FitnessTypography.MetricValue,
                        color = accentColor,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

// ==========================================
// HeroMetric (Ultra premium oversized stat)
// ==========================================
@Composable
fun HeroMetric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
                },
                label = "HeroMetricValueTransition"
            ) { targetValue ->
                Text(
                    text = targetValue,
                    style = FitnessTypography.HeroMetricGiant,
                    color = accentColor,
                    fontWeight = FontWeight.Black
                )
            }
            
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
            
            Text(
                text = unit,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// StatusChip
// ==========================================
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val containerBg by animateColorAsState(
        targetValue = color.copy(alpha = 0.12f),
        label = "ChipBgTransition"
    )

    Row(
        modifier = modifier
            .clip(MaterialTheme.expressiveShapes.medium)
            .background(containerBg)
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = MaterialTheme.expressiveShapes.medium
            )
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(MaterialTheme.iconSizes.small)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// InfoCard (Insightful panels)
// ==========================================
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Default.Info,
    tintColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(MaterialTheme.spacing.large)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "InfoIcon",
                tint = tintColor,
                modifier = Modifier
                    .size(MaterialTheme.iconSizes.medium)
                    .padding(top = 1.dp)
            )
            
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
            
            Column {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ==========================================
// ExpressiveTopBar
// ==========================================
@Composable
fun ExpressiveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    subtitle: String? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = MaterialTheme.elevationTokens.level0
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.medium
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = { actions() }
                )
            }
        }
    }
}

// ==========================================
// BottomActionBar
// ==========================================
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = MaterialTheme.elevationTokens.level3,
        shape = MaterialTheme.expressiveShapes.topRoundedLarge
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large)
        ) {
            content()
        }
    }
}

// ==========================================
// ExpressiveNavigationRail (8.11.16)
// ==========================================

/**
 * Expressive navigation rail with animated icon states.
 * Supports hover/press animations with spring-based transitions.
 */
@Composable
fun ExpressiveNavigationRail(
    modifier: Modifier = Modifier,
    navItems: List<NavRailItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NavRailScale"
    )
    
    NavigationRail(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .padding(MaterialTheme.spacing.small),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = MaterialTheme.elevationTokens.level2
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(MaterialTheme.motionTokens.durationMedium1),
                    label = "IconColor"
                )
                
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "SelectedIconScale"
                )
                
                IconButton(
                    interactionSource = interactionSource,
                    onClick = { onItemClick(index) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            .clip(MaterialTheme.expressiveShapes.small)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Text(
                    text = item.label,
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall)
                )
            }
        }
    }
}

data class NavRailItem(
    val icon: ImageVector,
    val label: String
)
