package de.minitraxx.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.minitraxx.app.audio.PedalManager
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.ui.screens.HomeScreen
import de.minitraxx.app.ui.screens.LiveScreen
import de.minitraxx.app.ui.screens.SetlistDetailScreen
import de.minitraxx.app.ui.screens.SongEditorScreen
import de.minitraxx.app.ui.theme.MiniTraxxTheme
import de.minitraxx.app.util.PdfChordImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Über „Teilen an MiniTraxx" empfangener Import (PDF oder Text). */
data class ImportRequest(val pdf: Uri?, val text: String?, val title: String?)

object ShareImport {
    val request = mutableStateOf<ImportRequest?>(null)
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MiniTraxxTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Liest ein per Teilen/Öffnen hereingereichtes PDF oder Text aus dem Intent. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> when {
                intent.type == "application/pdf" -> {
                    val uri = IntentCompat.getParcelableExtra(
                        intent, Intent.EXTRA_STREAM, Uri::class.java,
                    ) ?: return
                    ShareImport.request.value = ImportRequest(uri, null, displayName(uri))
                }
                intent.type == "text/plain" -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                    val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ShareImport.request.value = ImportRequest(null, text, title)
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (intent.type == "application/pdf" || uri.toString().endsWith(".pdf", true)) {
                    ShareImport.request.value = ImportRequest(uri, null, displayName(uri))
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull()

    // Bluetooth-Pedale melden sich als Tastatur — Tastendrücke hier abfangen,
    // damit sie unabhängig von Fokus und Display-Sperre wirken.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (PedalManager.get(this).onKeyDown(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (PedalManager.get(this).consumesKey(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val context = LocalContext.current

    // Geteilten Import verarbeiten: Song anlegen, ChordPro setzen, Editor öffnen.
    val request by ShareImport.request
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        ShareImport.request.value = null
        if (r.pdf == null && r.text == null) return@LaunchedEffect
        val repo = SongRepository.get(context)
        val chordPro: String? = try {
            when {
                r.pdf != null -> withContext(Dispatchers.IO) { PdfChordImporter.import(context, r.pdf) }
                else -> r.text
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: context.getString(R.string.import_failed),
                Toast.LENGTH_LONG,
            ).show()
            return@LaunchedEffect
        }
        val title = r.title?.removeSuffix(".pdf")?.removeSuffix(".PDF")?.trim()
            ?.ifBlank { null } ?: context.getString(R.string.imported_song)
        val id = repo.createSong(title, "")
        if (!chordPro.isNullOrBlank()) {
            repo.songDao.getWithStems(id)?.song?.let {
                repo.songDao.update(it.copy(chordPro = chordPro))
            }
        }
        nav.navigate("song/$id")
    }

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
