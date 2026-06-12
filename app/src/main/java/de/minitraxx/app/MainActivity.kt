package de.minitraxx.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.minitraxx.app.ui.screens.HomeScreen
import de.minitraxx.app.ui.screens.LiveScreen
import de.minitraxx.app.ui.screens.SetlistDetailScreen
import de.minitraxx.app.ui.screens.SongEditorScreen
import de.minitraxx.app.ui.theme.MiniTraxxTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MiniTraxxTheme {
                AppNav()
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenSetlist = { nav.navigate("setlist/$it") },
                onOpenSong = { nav.navigate("song/$it") },
            )
        }
        composable(
            "setlist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            SetlistDetailScreen(
                setlistId = id,
                onBack = { nav.popBackStack() },
                onStartLive = { index -> nav.navigate("live/$id?index=$index") },
                onOpenSong = { nav.navigate("song/$it") },
            )
        }
        composable(
            "song/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            SongEditorScreen(
                songId = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "live/{setlistId}?index={index}",
            arguments = listOf(
                navArgument("setlistId") { type = NavType.LongType },
                navArgument("index") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            LiveScreen(
                setlistId = entry.arguments!!.getLong("setlistId"),
                startIndex = entry.arguments!!.getInt("index"),
                onExit = { nav.popBackStack() },
            )
        }
    }
}
