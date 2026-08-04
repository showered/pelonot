package com.pelonot.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.pelonot.ui.screen.AccountScreen
import com.pelonot.ui.screen.ClassDetailScreen
import com.pelonot.ui.screen.ClassLibraryScreen
import com.pelonot.ui.screen.FtpProgressScreen
import com.pelonot.ui.screen.HistoryScreen
import com.pelonot.ui.screen.MainDashboardScreen
import com.pelonot.ui.screen.PostRideSummaryScreen
import com.pelonot.ui.screen.PreRideIntentPrompt
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.NewProfile
import com.pelonot.ui.screen.ProfileAccountOfferStep
import com.pelonot.ui.screen.ProfileCreationDialog
import com.pelonot.ui.screen.ProfileSelectorScreen
import com.pelonot.ui.screen.RideDetailScreen
import com.pelonot.ui.screen.RideScreen
import com.pelonot.ui.screen.RidingScreen
import com.pelonot.ui.screen.SettingsScreen
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.core.Features
import com.pelonot.domain.social.ClassRival
import com.pelonot.core.Formatters
import com.pelonot.ui.viewmodel.AppUiState
import com.pelonot.ui.viewmodel.InterruptedRide
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
    onCreateProfile: (profile: NewProfile, onCreated: (Int) -> Unit) -> Unit,
    onSelectProfile: (Int?) -> Unit,
    onRecoverWorkout: (onRecovered: (String) -> Unit) -> Unit = {},
    /** 8.3d — carry on riding the interrupted ride rather than filing it. */
    onResumeWorkout: (onResuming: (String) -> Unit) -> Unit = {},
    onDiscardRecoverableWorkout: () -> Unit = {},
    onRenameProfile: (com.pelonot.data.local.entity.UserEntity, String) -> Unit = { _, _ -> },
    onDeleteProfile: (com.pelonot.data.local.entity.UserEntity) -> Unit = {},
    /** "Not now" on the backup reminder (23.3.1) — moves the line, does not silence it. */
    onDismissBackupReminder: () -> Unit = {},
    /** "Don't ask me again" on the dashboard's account offer (15.8.4) — per profile. */
    onDismissAccountOffer: () -> Unit = {},
    /** Put back the FTP an auto change replaced (7.10.4). */
    onRevertFtpChange: (Int) -> Unit = {},
    /** The household's board for one class (24.1.2). A Room read, never a network one. */
    onLoadLeaderboard: suspend (classId: String, youId: Int?) -> ClassLeaderboard =
        { classId, _ -> ClassLeaderboard(classId) },
    /** Rides of this class that can be raced live (24.3.3). Always a Room read. */
    onLoadRivals: suspend (classId: String, youId: Int?) -> List<ClassRival> =
        { _, _ -> emptyList() }
) {
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var pendingClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var showIntentPrompt by rememberSaveable { mutableStateOf(false) }

    // 24.3.3. Chosen on class detail, carried across the intent prompt the
    // same way the class id is, and cleared with it — the two are one answer
    // to "what am I about to ride, and against whom".
    var pendingRivalId by rememberSaveable { mutableStateOf<String?>(null) }

    // 15.8.7: neither account offer may appear on a build with no cloud
    // configured — a self-hoster's app must not advertise a backup it cannot
    // perform. Read once; whether a build has a cloud does not change while
    // it runs.
    val cloudConfigured = remember { ServiceLocator.accountRepository.cloudConfigured }

    // 11.1a.5. Opening the app while a class is already recording — from the
    // ride notification, from the launcher, or from the strip after the task
    // was swiped away — used to land on "Who's riding?" with the ride running
    // behind it and no route back to it. Nothing outside WorkoutService knew a
    // ride existed.
    //
    // Only from the start destination, which is what makes this a cold-start
    // door and not a trap: a ride begun the ordinary way also sets this, and
    // the rider is already on the ride screen by then. Dashboard is pushed
    // underneath so the back stack matches the ordinary path exactly —
    // otherwise the summary's own popUpTo(Dashboard) has nothing to pop to and
    // the rider finishes the ride into a dead end.
    val activeRide = uiState.activeRide
    LaunchedEffect(activeRide) {
        if (activeRide != null &&
            navController.currentDestination?.route == Destination.ProfileSelector.route
        ) {
            navController.navigate(Destination.Dashboard.route)
            navController.navigate(
                Destination.Ride.of(activeRide.classId, activeRide.intentId)
            )
        }
    }

    val contentModifier = Modifier
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)

    // An unfinished ride means the process died mid-workout. Ask before the
    // rider starts anything else, since starting a new ride would leave the old
    // row sitting incomplete forever.
    uiState.recoverableWorkout?.let { interrupted ->
        InterruptedRideDialog(
            interrupted = interrupted,
            onResume = {
                onResumeWorkout { workoutId ->
                    // Dashboard underneath, for the reason 8.3c and 11.1a.5
                    // both found the hard way: this door opens from "Who's
                    // riding?", where Dashboard has never been on the stack, so
                    // a later popBackStack to it silently does nothing and
                    // strands the rider.
                    navController.navigate(
                        Destination.Ride.resuming(workoutId, interrupted.workout.classId)
                    ) {
                        popUpTo(Destination.ProfileSelector.route) { inclusive = false }
                    }
                }
            },
            onKeep = {
                onRecoverWorkout { workoutId ->
                    navController.navigate(Destination.PostRide.of(workoutId))
                }
            },
            onDiscard = onDiscardRecoverableWorkout
        )
    }

    if (showProfileDialog) {
        val leaveProfileCreation = {
            showProfileDialog = false
            navController.navigate(Destination.Dashboard.route) {
                popUpTo(Destination.ProfileSelector.route) { inclusive = false }
            }
        }

        ProfileCreationDialog(
            onProfileCreated = { newProfile ->
                onCreateProfile(newProfile) { _ ->
                    // 15.8.1: the profile is already persisted at this point.
                    // With no account offer to follow it, this is also the
                    // rider's cue that profile creation is finished; with one,
                    // onAccountOfferFinished carries that job instead.
                    if (!cloudConfigured) leaveProfileCreation()
                }
            },
            onDismiss = { showProfileDialog = false },
            accountOffer = if (cloudConfigured) {
                { onDone -> ProfileAccountOfferStep(onDone = onDone) }
            } else {
                null
            },
            onAccountOfferFinished = leaveProfileCreation
        )
    }

    if (showIntentPrompt) {
        PreRideIntentPrompt(
            onIntentSelected = { intent ->
                showIntentPrompt = false
                val classId = pendingClassId
                val rivalId = pendingRivalId
                pendingClassId = null
                pendingRivalId = null
                navController.navigate(Destination.Ride.of(classId, intent.id, rivalId))
            },
            onDismiss = {
                showIntentPrompt = false
                pendingClassId = null
                pendingRivalId = null
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
            val selected = uiState.selectedProfile
            // 15.8.4: the two moments this app already knows a rider is
            // thinking about identity are creating a profile and selecting
            // one that has ridden offline — this is the second. Never for a
            // profile that already has an account, never once dismissed, and
            // never on a build with no cloud (15.8.7, same gate as the offer
            // at profile creation).
            val showAccountOffer = cloudConfigured && selected != null &&
                selected.authUserId == null && !selected.accountOfferDismissed &&
                uiState.dashboardStats.hasRidden

            MainDashboardScreen(
                userName = uiState.selectedProfile?.name ?: "Guest",
                ftp = uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP,
                ftpTrend = uiState.ftpTrend,
                stats = uiState.dashboardStats,
                householdRecent = uiState.householdRecent,
                youId = uiState.selectedProfile?.localUserId,
                backupReminder = uiState.backupReminder,
                onDismissBackupReminder = onDismissBackupReminder,
                showAccountOffer = showAccountOffer,
                onAccountOffer = { navController.navigate(Destination.Account.route) },
                onDismissAccountOffer = onDismissAccountOffer,
                onJustRide = {
                    pendingClassId = null
                    showIntentPrompt = true
                },
                onBeginClass = { navController.navigate(Destination.ClassLibrary.route) },
                onHistory = { navController.navigate(Destination.History.route) },
                onSettings = { navController.navigate(Destination.Settings.route) },
                ridingHistory = uiState.ridingHistory,
                onFtpProgress = { navController.navigate(Destination.FtpProgress.route) },
                onRiding = { navController.navigate(Destination.Riding.route) }
            )
        }

        composable(Destination.FtpProgress.route) {
            FtpProgressScreen(
                trend = uiState.ftpTrend,
                onBack = navController::popBackStack,
                // The same destination history uses, so a ride opened from a
                // breakthrough is the ride, not a second rendering of one.
                onOpenRide = { workoutId ->
                    navController.navigate(Destination.RideDetail.of(workoutId))
                },
                onRevert = { change -> onRevertFtpChange(change.from) }
            )
        }

        composable(Destination.Riding.route) {
            RidingScreen(
                history = uiState.ridingHistory,
                onBack = navController::popBackStack
            )
        }

        composable(Destination.Account.route) {
            AccountScreen(onBack = navController::popBackStack)
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

            // 24.1.2. Read once per class rather than held in AppUiState:
            // it belongs to the class on screen, not to the app.
            val youId = uiState.selectedProfile?.localUserId
            val leaderboard by produceState<ClassLeaderboard?>(null, classId, youId) {
                value = classId?.let { onLoadLeaderboard(it, youId) }
            }

            // 24.3.3, read the same way and for the same reason — and off by
            // default since 24.3.11. The live leaderboard needs nothing chosen
            // here: it is everybody who qualifies, assembled when the ride
            // starts. With the flag off this stays empty and the *Ride
            // against* card is not drawn, which is the same path a class
            // nobody has ridden already takes.
            val rivals by produceState(emptyList<ClassRival>(), classId, youId) {
                value = if (Features.singleRivalGhost) {
                    classId?.let { onLoadRivals(it, youId) }.orEmpty()
                } else {
                    emptyList()
                }
            }
            // Held here rather than inside the screen so it survives the
            // intent prompt, which composes over this destination.
            var selectedRivalId by rememberSaveable(classId) {
                mutableStateOf<String?>(null)
            }

            ClassDetailScreen(
                plan = plan,
                ftp = (uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP).toDouble(),
                onBack = navController::popBackStack,
                onStart = {
                    pendingClassId = plan?.id
                    pendingRivalId = selectedRivalId
                    showIntentPrompt = true
                },
                leaderboard = leaderboard,
                rivals = rivals,
                selectedRivalId = selectedRivalId,
                onPickRival = { selectedRivalId = it }
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
                },
                navArgument(Destination.ARG_RESUME_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.ARG_RIVAL_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments
                ?.getString(Destination.ARG_CLASS_ID)
                ?.takeIf { it.isNotBlank() }
            val intent = RideIntent.fromId(
                backStackEntry.arguments?.getString(Destination.ARG_INTENT_ID)
            )
            val resumeWorkoutId = backStackEntry.arguments
                ?.getString(Destination.ARG_RESUME_ID)
                ?.takeIf { it.isNotBlank() }
            val rivalWorkoutId = backStackEntry.arguments
                ?.getString(Destination.ARG_RIVAL_ID)
                ?.takeIf { it.isNotBlank() }
            val plan = remember(classId, uiState.classes) {
                uiState.classes.firstOrNull { it.id == classId }
            }

            RideScreen(
                plan = plan,
                intent = intent,
                ftp = uiState.selectedProfile?.ftpWatts
                    ?: com.pelonot.data.local.entity.UserEntity.DEFAULT_FTP,
                userId = uiState.selectedProfile?.localUserId,
                resumeWorkoutId = resumeWorkoutId,
                rivalWorkoutId = rivalWorkoutId,
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
            SettingsScreen(
                onBack = navController::popBackStack,
                onOpenAccount = { navController.navigate(Destination.Account.route) }
            )
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
                // 12.6.2. The same destination the crash-recovery dialog uses,
                // and it replaces the summary rather than stacking on it:
                // system back from a resumed ride must not land on the summary
                // of the ride still being ridden.
                onResume = { resumedId, classId ->
                    navController.navigate(Destination.Ride.resuming(resumedId, classId)) {
                        popUpTo(Destination.PostRide.route) { inclusive = true }
                    }
                },
                onDone = {
                    // 8.3c. `popBackStack` returns false when the destination
                    // asked for was never on the stack, and that Boolean was
                    // read by nobody — the same shape as everything in the
                    // Corrections table. The crash-recovery dialog navigates
                    // here straight from "Who's riding?", where Dashboard has
                    // never been pushed, so *both* buttons on this screen did
                    // nothing whatever and the rider was stranded on the
                    // summary of a ride they had just been asked to keep.
                    //
                    // Back to the profile selector rather than to a Dashboard
                    // that was never entered: at this point nobody has said who
                    // is riding.
                    val popped = navController.popBackStack(
                        Destination.Dashboard.route,
                        inclusive = false
                    )
                    if (!popped) {
                        navController.navigate(Destination.ProfileSelector.route) {
                            popUpTo(Destination.PostRide.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Offered when the app finds a ride it never finished.
 *
 * **It offers to resume as well as to keep (8.3d).** 8.3a deliberately did not,
 * on the grounds that restarting the clock would splice a gap of unknown length
 * into the record — but the gap is arithmetic rather than unknown
 * (`RideInterruption`), and `elapsedSeconds()` has excluded paused time since
 * Phase 3, so the series has never meant *seconds since the ride started*. A
 * crash is a pause nobody got to press. What 8.3a was right about survives as
 * `workouts.resume_count` / `interrupted_sec`: the break is written down rather
 * than smoothed over.
 *
 * Resume is offered *only* while it is still the same ride — see
 * [InterruptedRide.canResume]. A ride abandoned yesterday gets the original two
 * answers, because picking that class back up would be a new workout wearing an
 * old one's interval clock.
 */
@Composable
private fun InterruptedRideDialog(
    interrupted: InterruptedRide,
    onResume: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit
) {
    val workout = interrupted.workout
    val started = remember(workout.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(workout.timestamp))
    }

    AlertDialog(
        onDismissRequest = { /* Deliberately not dismissible: it needs an answer. */ },
        title = { Text("You have an unfinished ride") },
        text = {
            Text(
                if (interrupted.canResume) {
                    // The elapsed figure is the one the rider does not have in
                    // their head, and it is what makes "carry on" a different
                    // offer from "keep it" rather than two words for one thing.
                    val ridden = Formatters.duration(
                        interrupted.interruption?.lastRecordedSec ?: 0
                    )
                    "Pelonot closed part-way through a ride started on $started. " +
                        "You had ridden $ridden, and all of it is still here — carry " +
                        "on from where you stopped, keep it as a finished ride, or " +
                        "throw it away."
                } else {
                    "Pelonot was closed part-way through a ride started on $started. " +
                        "Everything recorded up to that point is still here — keep it " +
                        "as a completed ride, or throw it away."
                }
            )
        },
        confirmButton = {
            if (interrupted.canResume) {
                TextButton(onClick = onResume) { Text("Carry on riding") }
            } else {
                TextButton(onClick = onKeep) { Text("Keep it") }
            }
        },
        dismissButton = {
            // Three answers do not fit the two slots an AlertDialog gives, so
            // keep joins discard on this side when resume takes the confirm
            // slot. Discard goes *first* rather than last: an AlertDialog lays
            // the dismiss slot out to the left of the confirm one, so leaving
            // the order as written put the only irreversible answer in the
            // middle, between two safe ones and a thumb's width from the
            // primary action. Furthest from the default is where it belongs.
            Row {
                TextButton(onClick = onDiscard) { Text("Discard") }
                if (interrupted.canResume) {
                    TextButton(onClick = onKeep) { Text("Keep it") }
                }
            }
        }
    )
}
