package com.example.englishoralpractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.englishoralpractice.library.ui.LibraryScreen
import com.example.englishoralpractice.player.PlayerScreen
import com.example.englishoralpractice.ui.theme.EnglishOralPracticeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnglishOralPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToPlayer = { videoId ->
                    navController.navigate(Screen.Player.createRoute(videoId))
                }
            )
        }
        
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.LongType }
            )
        ) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    
    data object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: Long) = "player/$videoId"
    }
}
