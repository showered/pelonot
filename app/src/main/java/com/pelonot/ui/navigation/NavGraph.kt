package com.pelonot.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.screen.*

@Composable
fun PelonotNavGraph(
    navController: NavHostController,
    users: List<UserEntity>,
    classTemplates: List<ClassTemplateEntity>,
    onSaveUser: (UserEntity) -> Unit,
    onChangeTheme: (Boolean) -> Unit
) {
    var hasJustRideStarted by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var savedFtp by remember { mutableStateOf(200) }
    var savedWeight by remember { mutableStateOf<Double?>(null) }
    var isDarkTheme by remember { mutableStateOf(true) }

    if (showProfileDialog) {
        ProfileCreationDialog(
            onProfileCreated = { name, weightKg, ftpWatts ->
                val nextId = users.maxOfOrNull { it.localUserId }?.plus(1) ?: 1
                val newUser = UserEntity(
                    localUserId = nextId,
                    name = name,
                    weightKg = weightKg ?: 70.0,
                    ftpWatts = ftpWatts
                )
                onSaveUser(newUser)
                showProfileDialog = false
                navController.navigate("dashboard/${newUser.localUserId}") {
                    popUpTo("profile_selector") { inclusive = false }
                }
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (hasJustRideStarted) {
        JustRideScreen(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            ftp = savedFtp,
            onEndRide = {
                hasJustRideStarted = false
                navController.navigate("post_ride/true") {
                    popUpTo("dashboard") { inclusive = false }
                }
            }
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = "profile_selector",
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        composable(
            "profile_selector",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            ProfileSelectorScreen(
                users = users,
                onProfileSelected = { user ->
                    navController.navigate("dashboard/${user.localUserId}") {
                        // Pop profile selector off the back stack so dashboard is the root
                        popUpTo("profile_selector") { inclusive = false }
                    }
                },
                onGuestSelected = {
                    navController.navigate("dashboard/-1") {
                        popUpTo("profile_selector") { inclusive = false }
                    }
                },
                onCreateProfile = {
                    showProfileDialog = true
                }
            )
        }

        composable(
            "dashboard/{userId}",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: -1
            val user = users.find { it.localUserId == userId }

            MainDashboardScreen(
                userName = user?.name ?: "Guest",
                ftp = user?.ftpWatts ?: savedFtp,
                onJustRide = {
                    hasJustRideStarted = true
                },
                onBeginClass = {
                    navController.navigate("class_library")
                },
                onSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("class_library") {
            ClassLibraryScreen(
                classTemplates = classTemplates,
                onClassSelected = { classTemplate ->
                    navController.navigate("class_detail/${classTemplate.id}") {
                        popUpTo("class_library") { inclusive = false }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            "class_detail/{classId}",
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: return@composable
            val classTemplate = classTemplates.find { it.id == classId }
            if (classTemplate != null) {
                ClassDetailScreen(
                    classTemplate = classTemplate,
                    ftp = savedFtp.toDouble(),
                    onBack = {
                        navController.popBackStack()
                    },
                    onStart = { _ ->
                        navController.navigate("post_ride/false") {
                            popUpTo("dashboard") { inclusive = false }
                        }
                    }
                )
            } else {
                navController.popBackStack()
            }
        }

        composable("settings") {
            SettingsScreen(
                currentFtp = savedFtp,
                currentWeight = savedWeight,
                isDarkTheme = isDarkTheme,
                onFtpChange = { newFtp -> savedFtp = newFtp },
                onWeightChange = { newWeight -> savedWeight = newWeight },
                onThemeToggle = { dark ->
                    isDarkTheme = dark
                    onChangeTheme(dark)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("post_ride/{isGuest}") { backStackEntry ->
            val isGuest = backStackEntry.arguments?.getString("isGuest")?.toBoolean() ?: false
            PostRideSummaryScreen(
                durationSec = 0,
                totalOutputKj = 0.0,
                avgPower = 0.0,
                avgCadence = 0.0,
                avgHeartRate = null,
                distanceKm = 0.0,
                isGuest = isGuest,
                onSave = {
                    navController.popBackStack("dashboard", inclusive = false)
                },
                onDiscard = {
                    navController.popBackStack("dashboard", inclusive = false)
                }
            )
        }
    }
}