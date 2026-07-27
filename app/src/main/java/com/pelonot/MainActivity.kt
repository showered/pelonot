package com.pelonot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.rememberNavController
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.navigation.PelonotNavGraph
import com.pelonot.ui.theme.PelonotTheme
import com.pelonot.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            PelonotTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    onThemeChanged = { isDarkTheme = it }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(onThemeChanged: (Boolean) -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var users by remember { mutableStateOf(emptyList<UserEntity>()) }
    var classTemplates by remember { mutableStateOf(emptyList<ClassTemplateEntity>()) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load data from Room on first composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            users = db.userDao().getAllUsers().first()
            classTemplates = db.classTemplateDao().getAllTemplates().first()
        }
        isLoaded = true
    }

    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Loading state
        if (!isLoaded) {
            Text(
                text = "Loading...",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center
            )
            return@Surface
        }

        PelonotNavGraph(
            navController = navController,
            users = users,
            classTemplates = classTemplates,
            onSaveUser = { user ->
                scope.launch(Dispatchers.IO) {
                    AppDatabase.getInstance(context).userDao().insertUser(user)
                    // Reload users list
                    users = AppDatabase.getInstance(context).userDao().getAllUsers().first()
                }
            },
            onChangeTheme = { dark -> onThemeChanged(dark) }
        )
    }
}