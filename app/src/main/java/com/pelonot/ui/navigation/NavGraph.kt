package com.pelonot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.screen.*

@Composable
fun PelonotNavGraph(
    navController: NavHostController,
    users: List<UserEntity>,
    classTemplates: List<ClassTemplateEntity>
) {
    NavHost(
        navController = navController,
        startDestination = "profile_selector"
    ) {
        composable("profile_selector") {
            ProfileSelectorScreen(
                users = users,
                onProfileSelected = { user ->
                    // TODO: Navigate to dashboard with user
                    navController.navigate("dashboard/${user.localUserId}")
                },
                onGuestSelected = {
                    navController.navigate("dashboard/-1")
                },
                onCreateProfile = {
                    // TODO: Show profile creation dialog
                }
            )
        }
        
        composable("dashboard/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: -1
            val user = users.find { it.localUserId == userId }
            
            MainDashboardScreen(
                userName = user?.name ?: "Guest",
                ftp = user?.ftpWatts ?: 200,
                onJustRide = {
                    // TODO: Show intent prompt
                    navController.navigate("intent_prompt")
                },
                onBeginClass = {
                    navController.navigate("class_library")
                },
                onSettings = {
                    navController.navigate("settings")
                }
            )
        }
        
        composable("intent_prompt") {
            PreRideIntentPrompt(
                onIntentSelected = { intent ->
                    // TODO: Start workout with intent
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("class_library") {
            ClassLibraryScreen(
                classTemplates = classTemplates,
                onClassSelected = { classTemplate ->
                    // TODO: Navigate to class detail
                    navController.navigate("class_detail/${classTemplate.id}")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("class_detail/{classId}") { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: return@composable
            // TODO: Show class detail and start button
            // For now, just navigate back
            navController.popBackStack()
        }
        
        composable("settings") {
            SettingsScreen(
                currentFtp = 200,
                currentWeight = null,
                isDarkTheme = true,
                onFtpChange = { /* TODO */ },
                onWeightChange = { /* TODO */ },
                onThemeToggle = { /* TODO */ },
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