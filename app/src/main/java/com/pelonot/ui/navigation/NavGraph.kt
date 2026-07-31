package com.pelonot.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.domain.model.RideIntent
import com.pelonot.ui.screen.ClassDetailScreen
import com.pelonot.ui.screen.ClassLibraryScreen
import com.pelonot.ui.screen.HistoryScreen
import com.pelonot.ui.screen.MainDashboardScreen
import com.pelonot.ui.screen.PostRideSummaryScreen
import com.pelonot.ui.screen.PreRideIntentPrompt
import com.pelonot.ui.screen.ProfileCreationDialog
import com.pelonot.ui.screen.ProfileSelectorScreen
import com.pelonot.ui.screen.RideDetailScreen
import com.pelonot.ui.screen.RideScreen
import com.pelonot.ui.screen.SettingsScreen
import com.pelonot.ui.viewmodel.AppUiState
import java.text.DateFormat
import java.util.Date

private const val TRANSITION_MS = 300

/**
 * The app's navigation graph.
 *
 * Ride screens are real destinations. Previously the graph checked two
 * `remember`ed booleans *before* the `NavHost` and, when either was set,
 * rendered the ride screen and `return`ed — so the ride existed entirely
 * outside navigation. Nothing was on the back stack, system back dropped
 * straight out of the app mid-workout, and the state was lost on rotation.
 */
@Composable
fun PelonotNavGraph(
    navController: NavHostController,
    uiState: AppUiState,
    onCreateProfile: (name: String, weightKg: Double?, ftpWatts: Int, onCreated: (Int) -> Unit) -> Unit,
    onSelectProfile: (Int?) -> Unit,
    onRecoverWorkout: (onRecovered: (String) -> Unit) -> Unit = {},
    onDiscardRecoverableWorkout: () -> Unit = {},
    onRenameProfile: (com.pelonot.data.local.entity.UserEntity, String) -> Unit = { _, _ -> },
    onDeleteProfile: (com.pelonot.data.local.entity.UserEntity) -> Unit = {}
) {
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var pendingClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var showIntentPrompt by rememberSaveable { mutableStateOf(false) }

    val contentModifier = Modifier
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)

    // An unfinished ride means the process died mid-workout. Ask before the
    // rider starts anything else, since starting a new ride would leave the old
    // row sitting incomplete forever.
    uiState.recoverableWorkout?.let { interrupted ->
        InterruptedRideDialog(
            workout = interrupted,
            onKeep = {
                onRecoverWorkout { workoutId ->
                    navController.navigate(Destination.PostRide.of(workoutId))
                }
            },
            onDiscard = onDiscardRecoverableWorkout
        )
    }

    if (showProfileDialog) {
        ProfileCreationDialog(
            onProfileCreated = { name, weightKg, ftpWatts ->
                onCreateProfile(name, weightKg, ftpWatts) {
                    showProfileDialog = false
                    navController.navigate(Destination.Dashboard.route) {
                        popUpTo(Destination.ProfileSelector.route) { inclusive = false }
                    }
                }
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showIntentPrompt) {
        PreRideIntentPrompt(
            onIntentSelected = { intent ->
                showIntentPrompt = false
                val classId = pendingClassId
                pendingClassId = null
                navController.navigate(Destination.Ride.of(classId, intent.id))
            },
            onDismiss = {
                showIntentPrompt = false
                pendingClassId = null
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Destination.ProfileSelector.route,
        modifier = contentModifier,
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) }
    ) {

        composable(Destination.ProfileSelector.route) {
            ProfileSelectorScreen(
                profiles = uiState.profiles,
                onProfileSelected = { user ->
                    onSelectProfile(user.localUserId)
                    navController.navigate(Destination.Dashboard.route)
                },
                onGuestSelected = {
                    onSelectProfile(null)
                    navController.navigate(Destination.Dashboard.route)
                },
                onCreateProfile = { showProfileDialog = true },
                onRenameProfile = onRenameProfile,
                onDeleteProfile = onDeleteProfile
            )
        }

        composable(Destination.Dashboard.route) {
            MainDashboardScreen(
                userName = uiState.selectedProfile?.name ?: "Guest",
                ftp = uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP,
                stats = uiState.dashboardStats,
                onJustRide = {
                    pendingClassId = null
                    showIntentPrompt = true
                },
                onBeginClass = { navController.navigate(Destination.ClassLibrary.route) },
                onHistory = { navController.navigate(Destination.History.route) },
                onSettings = { navController.navigate(Destination.Settings.route) }
            )
        }

        composable(Destination.History.route) {
            HistoryScreen(
                onBack = navController::popBackStack,
                onRideSelected = { workoutId ->
                    navController.navigate(Destination.RideDetail.of(workoutId))
                }
            )
        }

        composable(
            route = Destination.RideDetail.route,
            arguments = listOf(
                navArgument(Destination.ARG_WORKOUT_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            RideDetailScreen(
                workoutId = backStackEntry.arguments
                    ?.getString(Destination.ARG_WORKOUT_ID)
                    .orEmpty(),
                onBack = navController::popBackStack
            )
        }

        composable(Destination.ClassLibrary.route) {
            ClassLibraryScreen(
                classes = uiState.classes,
                onClassSelected = { plan ->
                    navController.navigate(Destination.ClassDetail.of(plan.id))
                },
                onBack = navController::popBackStack
            )
        }

        composable(
            route = Destination.ClassDetail.route,
            arguments = listOf(
                navArgument(Destination.ARG_CLASS_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString(Destination.ARG_CLASS_ID)
            val plan = remember(classId, uiState.classes) {
                uiState.classes.firstOrNull { it.id == classId }
            }

            ClassDetailScreen(
                plan = plan,
                ftp = (uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP).toDouble(),
                onBack = navController::popBackStack,
                onStart = {
                    pendingClassId = plan?.id
                    showIntentPrompt = true
                }
            )
        }

        composable(
            route = Destination.Ride.route,
            arguments = listOf(
                navArgument(Destination.ARG_CLASS_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.ARG_INTENT_ID) {
                    type = NavType.StringType
                    defaultValue = RideIntent.DEFAULT.id
                }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments
                ?.getString(Destination.ARG_CLASS_ID)
                ?.takeIf { it.isNotBlank() }
            val intent = RideIntent.fromId(
                backStackEntry.arguments?.getString(Destination.ARG_INTENT_ID)
            )
            val plan = remember(classId, uiState.classes) {
                uiState.classes.firstOrNull { it.id == classId }
            }

            RideScreen(
                plan = plan,
                intent = intent,
                ftp = uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP,
                userId = uiState.selectedProfile?.localUserId,
                onEndRide = { workoutId ->
                    if (workoutId != null) {
                        navController.navigate(Destination.PostRide.of(workoutId)) {
                            // Drop the ride off the back stack so system back
                            // from the summary cannot re-enter a finished ride.
                            popUpTo(Destination.Dashboard.route) { inclusive = false }
                        }
                    } else {
                        navController.popBackStack(Destination.Dashboard.route, inclusive = false)
                    }
                }
            )
        }

        // (Settings and the post-ride summary follow.)
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = navController::popBackStack)
        }

        composable(
            route = Destination.PostRide.route,
            arguments = listOf(
                navArgument(Destination.ARG_WORKOUT_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments
                ?.getString(Destination.ARG_WORKOUT_ID)
                .orEmpty()

            PostRideSummaryScreen(
                workoutId = workoutId,
                isGuest = uiState.selectedProfile == null,
                onDone = {
                    navController.popBackStack(Destination.Dashboard.route, inclusive = false)
                }
            )
        }
    }
}

/**
 * Offered when the app finds a ride it never finished.
 *
 * It does not offer to *resume*. The rider stopped pedalling when the app went
 * away, and restarting the clock would splice a gap of unknown length into the
 * middle of the record. What it offers instead is to keep what was actually
 * measured — the per-second series survived, because it is written as the ride
 * happens rather than at the end — with the totals rebuilt from those samples.
 */
@Composable
private fun InterruptedRideDialog(
    workout: WorkoutEntity,
    onKeep: () -> Unit,
    onDiscard: () -> Unit
) {
    val started = remember(workout.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(workout.timestamp))
    }

    AlertDialog(
        onDismissRequest = { /* Deliberately not dismissible: it needs an answer. */ },
        title = { Text("You have an unfinished ride") },
        text = {
            Text(
                "Pelonot was closed part-way through a ride started on $started. " +
                    "Everything recorded up to that point is still here — keep it as a " +
                    "completed ride, or throw it away."
            )
        },
        confirmButton = { TextButton(onClick = onKeep) { Text("Keep it") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard") } }
    )
}
