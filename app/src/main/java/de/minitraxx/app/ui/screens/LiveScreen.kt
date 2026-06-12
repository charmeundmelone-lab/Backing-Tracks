package de.minitraxx.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.minitraxx.app.R
import de.minitraxx.app.audio.PlaybackController
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.util.formatFrames
import de.minitraxx.app.util.formatRemaining

/**
 * Live-Screen: großer Restzeit-Countdown, nächster Song, Play/Pause.
 * Safe-Mode "Sperre + Langdruck": gesperrt sind alle Bedienelemente außer
 * Play/Pause; entsperrt wird per Langdruck (~1,5 s) auf das Schloss.
 * Entsperrt öffnet ein Tipp auf den Songtitel das Setlist-Sheet:
 * Song direkt anwählen (laden + warten) oder oben die Setlist wechseln.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(setlistId: Long, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { PlaybackController.get(context) }
    val repo = remember { SongRepository.get(context) }
    val state by controller.state.collectAsState()

    var locked by rememberSaveable { mutableStateOf(true) }
    var showSongSheet by remember { mutableStateOf(false) }

    LaunchedEffect(setlistId, startIndex) {
        controller.startSetlist(setlistId, startIndex)
    }

    // Display darf auf der Bühne nie ausgehen.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            controller.stopSession()
        }
    }

    // Zurück-Geste im gesperrten Zustand schlucken.
    BackHandler(enabled = locked) {}

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit, enabled = !locked) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back),
                        tint = if (locked) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.queue.isNotEmpty()) {
                        stringResource(
                            R.string.song_counter,
                            state.currentIndex + 1,
                            state.queue.size,
                        )
                    } else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // Schloss: Tippen sperrt sofort, Langdruck entsperrt.
                Box(
                    Modifier
                        .size(56.dp)
                        .background(
                            if (locked) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            CircleShape,
                        )
                        .combinedClickable(
                            onClick = { if (!locked) locked = true },
                            onLongClick = { locked = false },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        stringResource(
                            if (locked) R.string.unlock_hint else R.string.lock
                        ),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (locked) Modifier
                else Modifier.clickableItem { showSongSheet = true },
            ) {
                Text(
                    state.currentSong?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (!locked) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        stringResource(R.string.choose_song),
                        Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!state.currentSong?.artist.isNullOrBlank()) {
                Text(
                    state.currentSong?.artist ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Großer Restzeit-Countdown — Kernanzeige für die Bühne.
            val nearEnd = state.durationFrames > 0 &&
                state.durationFrames - state.positionFrames < 15L * 48_000
            Text(
                formatRemaining(state.positionFrames, state.durationFrames),
                fontSize = 96.sp,
                color = if (nearEnd && state.isPlaying) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )

            LinearProgressIndicator(
                progress = {
                    if (state.durationFrames > 0) {
                        state.positionFrames.toFloat() / state.durationFrames
                    } else 0f
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            Text(
                state.nextSong?.let { stringResource(R.string.next_song, it.title) }
                    ?: stringResource(R.string.last_song),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )

            state.error?.let {
                Text(
                    it,
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { controller.previous() },
                    enabled = !locked,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        stringResource(R.string.previous),
                        Modifier.size(48.dp),
                        tint = if (locked) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                    )
                }

                // Play/Pause bleibt auch im gesperrten Zustand bedienbar.
                Box(
                    Modifier
                        .size(112.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .combinedClickable(onClick = { controller.playPause() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(R.string.play_pause),
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                IconButton(
                    onClick = { controller.next() },
                    enabled = !locked,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        stringResource(R.string.next),
                        Modifier.size(48.dp),
                        tint = if (locked) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Text(
                formatFrames(state.positionFrames) + " / " + formatFrames(state.durationFrames),
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSongSheet) {
        val setlists by repo.setlistDao.observeAll().collectAsState(initial = emptyList())
        var listMenuOpen by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { showSongSheet = false }) {
            // Setlist-Wechsler — Player wird dafür nie verlassen.
            Box {
                ListItem(
                    headlineContent = { Text(state.setlistName) },
                    supportingContent = { Text(stringResource(R.string.switch_setlist)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    trailingContent = { Icon(Icons.Filled.ExpandMore, null) },
                    modifier = Modifier.clickableItem { listMenuOpen = true },
                )
                DropdownMenu(
                    expanded = listMenuOpen,
                    onDismissRequest = { listMenuOpen = false },
                ) {
                    setlists.forEach { setlist ->
                        DropdownMenuItem(
                            text = { Text(setlist.name) },
                            onClick = {
                                listMenuOpen = false
                                if (setlist.id != state.setlistId) {
                                    controller.startSetlist(setlist.id, 0)
                                }
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            LazyColumn {
                itemsIndexed(state.queue) { index, song ->
                    val isCurrent = index == state.currentIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                "${index + 1}.  ${song.title}",
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { Text(formatFrames(song.durationFrames)) },
                        trailingContent = {
                            if (isCurrent) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        // Manuelle Wahl: laden + warten (kein Sofortstart).
                        modifier = Modifier.clickableItem {
                            controller.loadIndex(index)
                            showSongSheet = false
                        },
                    )
                }
            }
        }
    }
}
