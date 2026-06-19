package de.livegigplayer.pro.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.livegigplayer.pro.data.Song

private val BgDeep    = Color(0xFF0A0A0A)
private val BgCard    = Color(0xFF1A1A1A)
private val BgTrack   = Color(0xFF2A2A2A)
private val BgBatch   = Color(0xFF3A3A00)
private val Volt      = Color(0xFFE8FF00)
private val VoltDim   = Color(0x8CE8FF00)
private val White     = Color(0xFFFFFFFF)
private val Gray      = Color(0xFF777777)

@Composable
fun MainScreen(vm: PlayerViewModel = viewModel()) {
    val context       = LocalContext.current
    val songs         by vm.filteredSongs.collectAsState()
    val currentSong   by vm.currentSong.collectAsState()
    val isPlaying     by vm.isPlaying.collectAsState()
    val trackMode     by vm.trackMode.collectAsState()
    val showMixer     by vm.showMixer.collectAsState()
    val positionMs    by vm.positionMs.collectAsState()
    val durationMs    by vm.durationMs.collectAsState()
    val isScanning    by vm.isScanning.collectAsState()
    val scanProgress  by vm.scanProgress.collectAsState()
    val importStatus  by vm.importStatus.collectAsState()
    val searchQuery   by vm.searchQuery.collectAsState()
    val selectedIds   by vm.selectedIds.collectAsState()
    val editingSongId by vm.editingSongId.collectAsState()

    var isLocked      by remember { mutableStateOf(false) }
    var searchActive  by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "❌ Kein Ordner gewählt (URI=null)", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "✅ Ordner erkannt – starte Import…", Toast.LENGTH_SHORT).show()
            vm.importFolder(context, uri)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .systemBarsPadding()
        ) {
            TopBar(
                isLocked       = isLocked,
                onLockToggle   = { isLocked = !isLocked },
                onMixerToggle  = { vm.toggleMixer() },
                onImport       = { importLauncher.launch(null) },
                searchActive   = searchActive,
                onSearchToggle = {
                    searchActive = !searchActive
                    if (!searchActive) vm.setSearchQuery("")
                },
                searchQuery    = searchQuery,
                onSearchChange = { vm.setSearchQuery(it) }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (songs.isEmpty() && importStatus.isNotEmpty()) {
                    item {
                        Text(
                            text = importStatus,
                            color = Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                itemsIndexed(songs) { index, song ->
                    SongRow(
                        index          = index + 1,
                        song           = song,
                        selected       = song.id == currentSong?.id,
                        isBatchSelected = song.id in selectedIds,
                        isEditing      = song.id == editingSongId,
                        isLocked       = isLocked,
                        selectionMode  = selectionMode,
                        onPlay         = { if (!isLocked) vm.selectSong(song, context) },
                        onToggleSelect = { vm.toggleSelect(song.id) },
                        onActivateBatch = { vm.toggleSelect(song.id) },
                        onCapoChange   = { delta -> vm.updateCapo(song, delta) },
                        onTitleSave    = { newTitle -> vm.updateTitle(song, newTitle) },
                        onEditStart    = { vm.startEditing(song.id) }
                    )
                }
            }

            if (selectionMode) {
                GenreBar(
                    count   = selectedIds.size,
                    onGenre = { genre -> vm.applyGenre(genre) },
                    onClear = { vm.clearSelection() }
                )
            } else {
                BottomPlayer(
                    song       = currentSong,
                    isPlaying  = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPrevious = { vm.skipPrevious() },
                    onPlayPause = { vm.togglePlayPause() },
                    onNext     = { vm.skipNext() }
                )
            }
        }

        MixerOverlay(
            visible        = showMixer,
            song           = currentSong,
            trackMode      = trackMode,
            isPlaying      = isPlaying,
            onVolumeChange = { name, db -> vm.updateMixerVolume(name, db) },
            onReset        = { vm.resetAllMixer() },
            onPlayPause    = { vm.togglePlayPause() },
            onStop         = { vm.stopPlayback() },
            onClose        = { vm.closeMixer() }
        )

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Volt, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SCANNE ORDNER", color = Volt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (scanProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = scanProgress,
                            color = Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onMixerToggle: () -> Unit,
    onImport: () -> Unit,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxWidth().background(BgDeep)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live-Gig-Player Pro",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = "Suchen",
                    tint = if (searchActive) Volt else Gray,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Ordner importieren",
                    tint = Gray, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onMixerToggle) {
                Icon(Icons.Filled.Tune, contentDescription = "Mixer",
                    tint = Gray, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onLockToggle) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (isLocked) "Gesperrt" else "Entsperrt",
                    tint = if (isLocked) Volt else Gray,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (searchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Titel, Artist, BPM, Genre…", color = Gray, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor        = White,
                    unfocusedTextColor      = White,
                    focusedIndicatorColor   = Volt,
                    unfocusedIndicatorColor = Gray,
                    cursorColor             = Volt
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    index: Int,
    song: Song,
    selected: Boolean,
    isBatchSelected: Boolean,
    isEditing: Boolean,
    isLocked: Boolean,
    selectionMode: Boolean,
    onPlay: () -> Unit,
    onToggleSelect: () -> Unit,
    onActivateBatch: () -> Unit,
    onCapoChange: (Int) -> Unit,
    onTitleSave: (String) -> Unit,
    onEditStart: () -> Unit
) {
    val editFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditing) {
        if (isEditing) editFocusRequester.requestFocus()
    }

    val bgColor = when {
        isBatchSelected -> BgBatch
        selected        -> BgTrack
        else            -> BgCard
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(bgColor, shape = MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelect()
                    else if (!isLocked) onPlay()
                },
                onLongClick = {
                    if (!isLocked && !selectionMode) onActivateBatch()
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (isBatchSelected || selected) Volt else VoltDim,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            if (isEditing) {
                var editText by remember(song.id) { mutableStateOf(song.title) }
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(Volt),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onTitleSave(editText) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editFocusRequester)
                )
            } else {
                Text(
                    text = song.title,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect()
                            else if (!isLocked) {
                                if (selected) onEditStart() else onPlay()
                            }
                        },
                        onLongClick = {
                            if (!isLocked && !selectionMode) onActivateBatch()
                        }
                    )
                )
            }
            val bpmText   = if (song.bpmExact > 0f) "%.1f BPM".format(song.bpmExact) else "${song.bpm} BPM"
            val artistPfx = if (song.artist.isNotEmpty()) "${song.artist}  ·  " else ""
            val genreSfx  = if (song.genre.isNotEmpty()) "  ·  ${song.genre}" else ""
            Text(
                text = "$artistPfx$bpmText  |  ${song.duration}$genreSfx",
                color = Gray,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Capo stepper
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "−",
                    color = if (isLocked) Gray else Volt,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = !isLocked) { onCapoChange(-1) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = song.capoPosition.toString(),
                    color = if (song.capoPosition > 0) Volt else Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = "+",
                    color = if (isLocked) Gray else Volt,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = !isLocked) { onCapoChange(1) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text("Kapo", color = Gray, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun GenreBar(count: Int, onGenre: (String) -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count Song${if (count == 1) "" else "s"} markiert",
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Auswahl aufheben", tint = Gray,
                    modifier = Modifier.size(22.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Pop/Rock", "Folk/Country", "Deutsch", "Groove").forEach { genre ->
                Button(
                    onClick = { onGenre(genre) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                    contentPadding = PaddingValues(
                        horizontal = 6.dp, vertical = 8.dp
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(genre, color = Volt, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BottomPlayer(
    song: Song?, isPlaying: Boolean, positionMs: Long, durationMs: Long,
    onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    val remainMs = (durationMs - positionMs).coerceAtLeast(0L)
    val remSec   = remainMs / 1000
    val timeText = if (song == null) "-00:00" else "-%02d:%02d".format(remSec / 60, remSec % 60)

    Column(modifier = Modifier.fillMaxWidth().background(BgDeep)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(timeText, color = VoltDim, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Volt, trackColor = BgTrack, drawStopIndicator = {}
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            PlayerButton(Icons.Filled.SkipPrevious, "ZURÜCK",  Modifier.weight(1f), onClick = onPrevious)
            PlayerButton(
                icon     = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label    = if (isPlaying) "PAUSE" else "PLAY",
                modifier = Modifier.weight(1f),
                tint     = if (isPlaying) Volt else White,
                onClick  = onPlayPause
            )
            PlayerButton(Icons.Filled.Repeat,   "LOOP",   Modifier.weight(1f), onClick = {})
            PlayerButton(Icons.Filled.SkipNext,  "WEITER", Modifier.weight(1f), onClick = onNext)
        }
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    tint: Color = White, onClick: () -> Unit
) {
    Column(
        modifier = modifier.height(110.dp).background(BgCard).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(42.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
