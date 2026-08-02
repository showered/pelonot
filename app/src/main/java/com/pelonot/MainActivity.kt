package com.pelonot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.pelonot.data.repository.ThemeMode
import com.pelonot.ui.navigation.PelonotNavGraph
import com.pelonot.ui.theme.PelonotTheme
import com.pelonot.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // collectAsStateWithLifecycle stops collecting while the activity
            // is stopped, rather than keeping database flows hot in the
            // background as a plain collectAsState would.
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val darkTheme = when (uiState.settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }

            PelonotTheme(
                darkTheme = darkTheme,
                useDynamicColor = uiState.settings.useDynamicColor,
                units = uiState.settings.unitSystem
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (uiState.isLoading) {
                        LoadingIndicator()
                    } else {
                        PelonotNavGraph(
                            navController = rememberNavController(),
                            uiState = uiState,
                            onCreateProfile = viewModel::createProfile,
                            onSelectProfile = viewModel::selectProfile,
                            onRecoverWorkout = viewModel::recoverWorkout,
                            onDiscardRecoverableWorkout = viewModel::discardRecoverableWorkout,
                            onRenameProfile = { user, name ->
                                viewModel.renameProfile(user.localUserId, name)
                            },
                            onDeleteProfile = { user -> viewModel.deleteProfile(user.localUserId) },
                            onDismissBackupReminder = viewModel::snoozeBackupReminder,
                            onRevertFtpChange = viewModel::revertFtpChange,
                            onLoadLeaderboard = viewModel::householdLeaderboard
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
