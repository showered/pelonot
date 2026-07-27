package com.pelonot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.pelonot.data.local.AppDatabase
import com.pelonot.ui.navigation.PelonotNavGraph
import com.pelonot.ui.theme.PelonotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelonotTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    
    // TODO: Replace with actual data from ViewModel
    val users = emptyList<com.pelonot.data.local.entity.UserEntity>()
    val classTemplates = emptyList<com.pelonot.data.local.entity.ClassTemplateEntity>()
    
    PelonotNavGraph(
        navController = navController,
        users = users,
        classTemplates = classTemplates
    )
}
