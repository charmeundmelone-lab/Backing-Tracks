package de.minitraxx.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.minitraxx.app.R
import de.minitraxx.app.audio.NativeEngine
import de.minitraxx.app.audio.PlaybackController
import de.minitraxx.app.data.GigRepository
import de.minitraxx.app.data.SettingsStore
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.util.ChordPro
import de.minitraxx.app.util.formatFrames
import de.minitraxx.app.util.formatRemaining
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCROLL_LEAD_MS = 600L

/**
 * Live-Screen: großer Restzeit-Countdown, nächster Song, Play/Pause.
 * Safe-Mode "Sperre + Langdruck": gesperrt sind alle Bedienelemente außer
 * Play/Pause; entsperrt wird per Langdruck (~1,5 s) auf das Schloss.
 *
 * Hat der Song ChordPro-Text mit {section:}-Markern und gespeicherte
 * Tap-Once-Sync-Daten, scrollt die Anzeige sektionsgenau mit der Musik —
 * kein Slider, keine Geschwindigkeit, nur einmal Tippen im Sync-Modus.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(setlistId: Long, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { PlaybackController.get(context) }
    val repo = remember { SongRepository.get(context) }
    val gigRepo = remember { GigRepository.get(context) }
    val store = remember { SettingsStore.get(context) }
    val state by controller.state.collectAsState()
    val settings by store.settings.collectAsState()
    val scope = rememberCoroutineScope()

    val activeGig by gigRepo.gigDao.observeActiveGig().collectAsState(null)
    val playedSongIds by gigRepo.playedSongIdsForActiveGig.collectAsState(emptySet())
    val playCounts by gigRepo.playCountsForActiveGig.collectAsState(emptyMap())

    var locked by rememberSaveable { mutableStateOf(true) }
    var showSongSheet by remember { mutableStateOf(false) }
    var lyricsFullscreen by rememberSaveable { mutableStateOf(false) }
    var isSyncMode by remember { mutableStateOf(false) }
    var syncTaps by remember { mutableStateOf(listOf<Long>()) }
    var syncSaved by remember { mutableStateOf(false) }
    var showOffsetSheet by remember { mutableStateOf(false) }

    val hasLyrics = !state.currentSong?.chordPro.isNullOrBlank()

    val sections = remember(state.currentSong?.chordPro) {
        ChordPro.parse(state.currentSong?.chordPro ?: "")
            .filter { it.kind == ChordPro.Kind.SECTION }
            .map { it.text }
    }
    val hasSections = sections.isNotEmpty()

    val onSyncTap: () -> Unit = {
        if (isSyncMode && syncTaps.size < sections.size) {
            val posMs = state.positionFrames * 1000L / 48_000L
            val newTaps = syncTaps + posMs
            if (newTaps.size >= sections.size) {
                // Letzter Tipp: sofort speichern, Modus beenden, kurz grün zeigen.
                val songId = state.currentSong?.songId
                if (songId != null) {
                    controller.saveSyncData(songId, newTaps.joinToString(" "))
                }
                isSyncMode = false
                syncTaps = emptyList()
                syncSaved = true
            } else {
                syncTaps = newTaps
            }
        }
    }

    val onCancelSync: () -> Unit = {
        isSyncMode = false
        syncTaps = emptyList()
    }

    LaunchedEffect(syncSaved) {
        if (syncSaved) {
            kotlinx.coroutines.delay(2000L)
            syncSaved = false
        }
    }

    LaunchedEffect(setlistId, startIndex) {
        controller.startSetlist(setlistId, startIndex)
    }

    // Sync-Modus bei Songwechsel beenden.
    LaunchedEffect(state.currentSong?.songId) {
        if (isSyncMode) {
            isSyncMode = false
            syncTaps = emptyList()
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            controller.stopSession()
        }
    }

    BackHandler(enabled = locked) {}

    val onFontScale: (Float) -> Unit = { zoom ->
        store.update { it.copy(lyricsFontSp = (it.lyricsFontSp * zoom).coerceIn(10f, 40f)) }
    }

    @Composable
    fun SyncTapButton() {
        if (!isSyncMode && !syncSaved) return
        val saved = syncSaved && !isSyncMode
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    if (saved) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    RoundedCornerShape(12.dp),
                )
                .then(if (!saved) Modifier.clickable(onClick = onSyncTap) else Modifier)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (saved) "✓  Sync gespeichert"
                else "TIPPE HIER  •  ${sections.getOrElse(syncTaps.size) { "" }}  •  ${syncTaps.size + 1}/${sections.size}",
                color = if (saved) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }

    @Composable
    fun SyncButton(enabled: Boolean) {
        if (!hasSections) return
        val syncColor = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isSyncMode -> MaterialTheme.colorScheme.error
            !state.currentSong?.syncData.isNullOrBlank() -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground
        }
        Box(
            Modifier
                .size(48.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        if (isSyncMode) onCancelSync()
                        else if (state.currentSong?.syncData.isNullOrBlank()) {
                            syncTaps = emptyList()
                            isSyncMode = true
                        }
                        // has sync data → short-press does nothing; long-press to re-sync
                    },
                    onLongClick = {
                        if (!isSyncMode) {
                            syncTaps = emptyList()
                            isSyncMode = true
                        } else {
                            showOffsetSheet = true
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isSyncMode) Icons.Filled.Check else Icons.Filled.Adjust,
                "Tap-Sync",
                tint = syncColor,
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (hasLyrics && lyricsFullscreen) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { lyricsFullscreen = false }, enabled = !locked) {
                        Icon(
                            Icons.Filled.FullscreenExit,
                            stringResource(R.string.lyrics_collapse),
                            tint = if (locked) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    SyncButton(enabled = !locked)
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatRemaining(state.positionFrames, state.durationFrames),
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    LockButton(locked, onLock = { locked = true }, onUnlock = { locked = false })
                }
                LyricsPane(
                    chordPro = state.currentSong?.chordPro ?: "",
                    syncData = state.currentSong?.syncData ?: "",
                    syncOffsetMs = settings.syncOffsetMs,
                    isSyncMode = isSyncMode,
                    syncTapIndex = syncTaps.size,
                    fontSp = settings.lyricsFontSp,
                    positionFrames = state.positionFrames,
                    durationFrames = state.durationFrames,
                    isPlaying = state.isPlaying,
                    allowGestures = !locked && !isSyncMode,
                    onFontScale = onFontScale,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                )
                SyncTapButton()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.nextSong?.let { stringResource(R.string.next_song, it.title) }
                            ?: stringResource(R.string.last_song),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        size = 72,
                        onClick = { controller.playPause() },
                    )
                }
            }
        } else {
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
                    if (hasLyrics) {
                        IconButton(onClick = { lyricsFullscreen = true }, enabled = !locked) {
                            Icon(
                                Icons.Filled.Fullscreen,
                                stringResource(R.string.lyrics_expand),
                                tint = if (locked) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        SyncButton(enabled = !locked)
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
                    LockButton(locked, onLock = { locked = true }, onUnlock = { locked = false })
                }

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
                if (!state.currentSong?.notes.isNullOrBlank()) {
                    Text(
                        state.currentSong?.notes ?: "",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }

                val nearEnd = state.durationFrames > 0 &&
                    state.durationFrames - state.positionFrames < 15L * 48_000
                val countdownColor =
                    if (nearEnd && state.isPlaying) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary

                if (hasLyrics) {
                    Text(
                        formatRemaining(state.positionFrames, state.durationFrames),
                        fontSize = 44.sp,
                        color = countdownColor,
                    )
                    LyricsPane(
                        chordPro = state.currentSong?.chordPro ?: "",
                        syncData = state.currentSong?.syncData ?: "",
                        syncOffsetMs = settings.syncOffsetMs,
                        isSyncMode = isSyncMode,
                        syncTapIndex = syncTaps.size,
                        fontSp = settings.lyricsFontSp,
                        positionFrames = state.positionFrames,
                        durationFrames = state.durationFrames,
                        isPlaying = state.isPlaying,
                        allowGestures = !locked && !isSyncMode,
                        onFontScale = onFontScale,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    SyncTapButton()
                } else {
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatRemaining(state.positionFrames, state.durationFrames),
                        fontSize = 96.sp,
                        color = countdownColor,
                    )
                    Spacer(Modifier.weight(1f))
                }

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

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        size = 112,
                        onClick = { controller.playPause() },
                    )
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
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSongSheet) {
        val setlists by repo.setlistDao.observeAll().collectAsState(initial = emptyList())
        var listMenuOpen by remember { mutableStateOf(false) }
        var selectedGenre by remember { mutableStateOf<String?>(null) }
        val queueGenres = remember(state.queue) {
            state.queue.map { it.genre }.filter { it.isNotEmpty() }.distinct().sorted()
        }
        val displayedQueue = remember(state.queue, selectedGenre) {
            if (selectedGenre == null) state.queue
            else state.queue.filter { it.genre == selectedGenre }
        }

        ModalBottomSheet(onDismissRequest = { showSongSheet = false }) {
            // Gig-Button
            GigStatusButton(
                activeGig = activeGig,
                playedCount = playCounts.values.sumOf { 1 }.coerceAtLeast(playedSongIds.size),
                onStartGig = { scope.launch { gigRepo.startGig() } },
                onEndGig = { scope.launch { activeGig?.id?.let { gigRepo.endGig(it) } } },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // Setlist-Header
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

            // Genre-Chips (nur wenn vorhanden)
            if (queueGenres.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedGenre == null,
                        onClick = { selectedGenre = null },
                        label = { Text("Alle") },
                    )
                    queueGenres.forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { selectedGenre = if (selectedGenre == genre) null else genre },
                            label = { Text(genre) },
                        )
                    }
                }
            }

            HorizontalDivider()

            LazyColumn {
                itemsIndexed(displayedQueue) { _, song ->
                    val queueIndex = state.queue.indexOf(song)
                    val isCurrent = queueIndex == state.currentIndex
                    val playCount = playCounts[song.songId] ?: 0
                    val wasPlayed = song.songId in playedSongIds
                    ListItem(
                        headlineContent = {
                            Text(
                                "${queueIndex + 1}.  ${song.title}",
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    wasPlayed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                formatFrames(song.durationFrames),
                                color = if (wasPlayed && !isCurrent)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (wasPlayed) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            if (playCount > 1) "${playCount}×" else "✓",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                                if (isCurrent) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickableItem {
                            controller.loadIndex(queueIndex)
                            showSongSheet = false
                        },
                    )
                }
            }
        }
    }

    if (showOffsetSheet) {
        ModalBottomSheet(onDismissRequest = { showOffsetSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(
                    "Reaktionszeit-Ausgleich: ${settings.syncOffsetMs} ms",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = settings.syncOffsetMs.toFloat(),
                    onValueChange = { store.update { s -> s.copy(syncOffsetMs = it.roundToInt()) } },
                    valueRange = 0f..500f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    "Tippst du etwas nach dem Sektionswechsel? Erhöhe den Wert. " +
                        "Standard: 200 ms. Gilt für alle Songs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GigStatusButton(
    activeGig: de.minitraxx.app.data.GigEntity?,
    playedCount: Int,
    onStartGig: () -> Unit,
    onEndGig: () -> Unit,
) {
    if (activeGig == null) {
        TextButton(
            onClick = onStartGig,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Text(
                "＋  Neuen Gig starten",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        val progress = remember { Animatable(0f) }
        var pressing by remember { mutableStateOf(false) }
        val errorColor = MaterialTheme.colorScheme.error

        LaunchedEffect(pressing) {
            if (pressing) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
                )
                if (pressing) {
                    pressing = false
                    onEndGig()
                }
            } else {
                progress.snapTo(0f)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            tryAwaitRelease()
                            pressing = false
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⊙",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Gig läuft  •  $playedCount Song${if (playedCount == 1) "" else "s"} gespielt",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (pressing) {
                    Text(
                        "halten…",
                        color = errorColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (pressing && progress.value > 0f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth(),
                    color = errorColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockButton(locked: Boolean, onLock: () -> Unit, onUnlock: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .background(
                if (locked) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                CircleShape,
            )
            .combinedClickable(
                onClick = { if (!locked) onLock() },
                onLongClick = onUnlock,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            stringResource(if (locked) R.string.unlock_hint else R.string.lock),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayPauseButton(isPlaying: Boolean, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(R.string.play_pause),
            Modifier.size((size * 4 / 7).dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Textanzeige mit zwei Scroll-Modi:
 *
 * – Ohne Sync-Daten: lineare Positionskopplung (LazyColumn-Item-Fraction).
 * – Mit Sync-Daten: sektionsbasiertes Scrollen via animateScrollToItem —
 *   kein Pixel-Tracking, zuverlässig auf jedem Gerät.
 */
@Composable
private fun LyricsPane(
    chordPro: String,
    syncData: String,
    syncOffsetMs: Int,
    isSyncMode: Boolean,
    syncTapIndex: Int,
    fontSp: Float,
    positionFrames: Long,
    durationFrames: Long,
    isPlaying: Boolean,
    allowGestures: Boolean,
    onFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(chordPro) { ChordPro.parse(chordPro) }
    val lazyState = rememberLazyListState()

    val syncTimestamps = remember(syncData) {
        syncData.trim().split(" ").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }

    // lineIndex → sectionIndex  (nur SECTION-Zeilen)
    val lineToSectionIndex: Map<Int, Int> = remember(lines) {
        var si = 0
        buildMap { lines.forEachIndexed { idx, l -> if (l.kind == ChordPro.Kind.SECTION) put(idx, si++) } }
    }

    // sectionIndex → LazyColumn-Item-Index (Offset +1 wegen führendem Spacer)
    val sectionToItemIndex: Map<Int, Int> = remember(lines) {
        var si = 0
        buildMap { lines.forEachIndexed { idx, l -> if (l.kind == ChordPro.Kind.SECTION) put(si++, idx + 1) } }
    }

    // currentPlaySection für Section-Highlighting (nur Darstellung, kein Scroll).
    val currentPlaySection: Int = remember(positionFrames, syncTimestamps, syncOffsetMs) {
        if (syncTimestamps.isEmpty()) -1
        else {
            val posMs = positionFrames * 1000L / 48_000L
            syncTimestamps.indexOfLast { tsMs -> posMs >= tsMs - syncOffsetMs }
        }
    }

    LaunchedEffect(chordPro) {
        lazyState.scrollToItem(0)
    }

    // Lineare Positionskopplung (Fallback ohne Sync oder im Sync-Modus).
    LaunchedEffect(positionFrames, durationFrames, isPlaying) {
        if (isPlaying && durationFrames > 0 && (syncTimestamps.isEmpty() || isSyncMode)) {
            val targetIdx = ((positionFrames.toDouble() / durationFrames) * lines.size)
                .toInt().coerceIn(0, lines.size)
            lazyState.scrollToItem(targetIdx)
        }
    }

    // Sektionsbasiertes Scrollen mit Innerhalb-Sektion-Interpolation.
    // Zwischen zwei Timestamps wird linear durch die Zeilen der Sektion gescrollt.
    // durationFrames als Key: LaunchedEffect startet neu sobald Song geladen ist.
    // isPlaying ist KEIN Key — kurzes Flackern würde lastTargetItem resetten.
    LaunchedEffect(syncTimestamps, syncOffsetMs, sectionToItemIndex, isSyncMode, durationFrames) {
        if (syncTimestamps.isEmpty() || isSyncMode || durationFrames <= 0) return@LaunchedEffect
        val durationMs = durationFrames * 1000L / 48_000L
        var lastTargetItem = -1
        while (true) {
            delay(100)
            val posMs = NativeEngine.positionFrames() * 1000L / 48_000L
            val sec = syncTimestamps.indexOfLast { ts -> posMs >= ts - syncOffsetMs }
            val targetItem = if (sec < 0) {
                0
            } else {
                val secStart = syncTimestamps[sec]
                val secEnd = syncTimestamps.getOrNull(sec + 1) ?: durationMs
                val firstItem = sectionToItemIndex[sec] ?: 0
                val nextFirst = sectionToItemIndex[sec + 1] ?: (lines.size + 1)
                val t = if (secEnd > secStart)
                    ((posMs - secStart).toDouble() / (secEnd - secStart)).coerceIn(0.0, 1.0)
                else 0.0
                (firstItem + (nextFirst - firstItem) * t).toInt()
            }
            if (targetItem != lastTargetItem) {
                lastTargetItem = targetItem
                lazyState.scrollToItem(targetItem)
            }
        }
    }

    LazyColumn(
        state = lazyState,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
            .then(
                if (allowGestures) Modifier.pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onFontScale(zoom) }
                } else Modifier
            ),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(lines) { lineIdx, line ->
            when (line.kind) {
                ChordPro.Kind.EMPTY -> Spacer(Modifier.height((fontSp * 0.7f).dp))
                ChordPro.Kind.COMMENT -> Text(
                    line.text,
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.3f).sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                ChordPro.Kind.SECTION -> {
                    val si = lineToSectionIndex[lineIdx] ?: 0
                    val isCurrent = !isSyncMode && si == currentPlaySection
                    val isNextTap = isSyncMode && si == syncTapIndex
                    val dividerAlpha = if (isCurrent || isNextTap) 0.8f else 0.3f
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = dividerAlpha),
                        )
                        Text(
                            "  ${line.text}  ",
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isCurrent || isNextTap) 1f else 0.6f,
                            ),
                            fontSize = (fontSp * 0.85f).sp,
                            fontWeight = if (isCurrent || isNextTap) FontWeight.Bold else FontWeight.Normal,
                        )
                        HorizontalDivider(
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = dividerAlpha),
                        )
                    }
                }
                ChordPro.Kind.LYRIC -> LyricLine(line, fontSp)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * Rendert eine Textzeile als umbrechende Folge von „Wörtern". Jedes Wort trägt
 * seinen Akkord direkt über der richtigen Silbe; lange Zeilen brechen sauber um
 * (Akkorde wandern mit), statt rechts aus dem Bild zu laufen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLine(line: ChordPro.Line, fontSp: Float) {
    val words = remember(line) { ChordPro.toWords(line) }
    if (words.isEmpty()) {
        Spacer(Modifier.height((fontSp * 0.6f).dp))
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = (fontSp * 0.12f).dp),
        horizontalArrangement = Arrangement.spacedBy((fontSp * 0.3f).dp),
    ) {
        for (word in words) {
            Row {
                for (piece in word) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            piece.chord ?: " ",
                            fontSize = (fontSp * 0.82f).sp,
                            lineHeight = (fontSp * 0.92f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Text(
                            piece.text,
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * 1.1f).sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
