package de.livegigplayer.pro.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.livegigplayer.pro.data.Playlist
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Palette ──────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0A0A0A)
private val BgCard      = Color(0xFF1A1A1A)
private val BgTrack     = Color(0xFF2A2A2A)
private val BgBatch     = Color(0x33EAB308)  // #EAB308 @ 20%
private val BgPlayer    = Color(0xFF1E293B)
private val Volt        = Color(0xFFE8FF00)
private val VoltDim     = Color(0x8CE8FF00)
private val White       = Color(0xFFFFFFFF)
private val Gray        = Color(0xFF777777)
private val RedStop     = Color(0xFFDC2626)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun MainScreen(vm: PlayerViewModel = viewModel()) {
    val context       = LocalContext.current
    val currentSong   by vm.currentSong.collectAsState()
    val isPlaying     by vm.isPlaying.collectAsState()
    val trackMode     by vm.trackMode.collectAsState()
    val showMixer     by vm.showMixer.collectAsState()
    val positionMs    by vm.positionMs.collectAsState()
    val durationMs    by vm.durationMs.collectAsState()
    val isScanning    by vm.isScanning.collectAsState()
    val scanProgress  by vm.scanProgress.collectAsState()
    val nextSong      by vm.nextSong.collectAsState()
    val loopActive    by vm.loopActive.collectAsState()

    var selectedTab   by remember { mutableStateOf(0) }  // 0=Archiv 1=Playlist
    var isLocked      by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) Toast.makeText(context, "❌ Kein Ordner gewählt (URI=null)", Toast.LENGTH_LONG).show()
        else { Toast.makeText(context, "✅ Ordner erkannt – starte Import…", Toast.LENGTH_SHORT).show(); vm.importFolder(context, uri) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(BgDeep).systemBarsPadding()
        ) {
            // Top bar (enthält Tab-Navigation)
            TopBar(
                selectedTab   = selectedTab,
                onTabSelect   = { selectedTab = it },
                isLocked      = isLocked,
                onLockToggle  = { isLocked = !isLocked },
                onMixerToggle = { vm.toggleMixer() },
                onImport      = { importLauncher.launch(null) },
                onDeleteAll   = { vm.deleteAllSongs() }
            )

            // Tab content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ArchivTab(vm = vm, isLocked = isLocked)
                    1 -> PlaylistTab(vm = vm, isLocked = isLocked, loopActive = loopActive)
                }
            }

            // Mini-Player (96dp, always visible)
            MiniPlayer(
                song       = currentSong,
                nextSong   = nextSong,
                isPlaying  = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onPause    = { vm.togglePlayPause() }
            )
        }

        // Mixer overlay
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

        // Scan overlay
        if (isScanning) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Volt, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SCANNE ORDNER", color = Volt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (scanProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(scanProgress, color = Gray, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    selectedTab: Int, onTabSelect: (Int) -> Unit,
    isLocked: Boolean, onLockToggle: () -> Unit,
    onMixerToggle: () -> Unit, onImport: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var menuExpanded        by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(BgDeep)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab-Icons links
        IconButton(onClick = { onTabSelect(0) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = "Archiv",
                tint = if (selectedTab == 0) Volt else Gray,
                modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = { onTabSelect(1) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.QueueMusic, contentDescription = "Playlist",
                tint = if (selectedTab == 1) Volt else Gray,
                modifier = Modifier.size(30.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Aktions-Icons rechts
        IconButton(onClick = onImport) {
            Icon(Icons.Filled.AddCircleOutline, contentDescription = "Import",
                tint = Gray, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onMixerToggle) {
            Icon(Icons.Filled.Tune, contentDescription = "Mixer",
                tint = Gray, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Lock",
                tint = if (isLocked) Volt else Gray,
                modifier = Modifier.size(26.dp)
            )
        }
        if (selectedTab == 0) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menü",
                        tint = Gray, modifier = Modifier.size(26.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = BgCard
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle Songs löschen", color = RedStop) },
                        onClick = { menuExpanded = false; showDeleteAllDialog = true }
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor   = BgCard,
            title = { Text("Alle Songs löschen?", color = White, fontWeight = FontWeight.Bold) },
            text  = { Text(
                "Wirklich ALLE Songs aus dem Archiv löschen? Diese Aktion kann nicht rückgängig gemacht werden.",
                color = Gray, fontSize = 13.sp
            ) },
            confirmButton = {
                TextButton(onClick = { showDeleteAllDialog = false; onDeleteAll() }) {
                    Text("Alle löschen", color = RedStop, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Abbrechen", color = Gray)
                }
            }
        )
    }
}

// ── Tab A: Archiv ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArchivTab(vm: PlayerViewModel, isLocked: Boolean) {
    val context       = LocalContext.current
    val songs         by vm.filteredSongs.collectAsState()
    val currentSong   by vm.currentSong.collectAsState()
    val selectedIds   by vm.selectedIds.collectAsState()
    val editingSongId by vm.editingSongId.collectAsState()
    val importStatus  by vm.importStatus.collectAsState()
    val searchQuery   by vm.searchQuery.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()

    var searchActive  by remember { mutableStateOf(false) }
    var editSheet     by remember { mutableStateOf<Song?>(null) }
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope         = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            active = searchActive,
            query  = searchQuery,
            onToggle = { searchActive = !searchActive; if (!searchActive) vm.setSearchQuery("") },
            onChange = { vm.setSearchQuery(it) }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (songs.isEmpty() && importStatus.isNotEmpty()) {
                item { Text(importStatus, color = Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(songs) { index, song ->
                ArchivSongRow(
                    index           = index + 1,
                    song            = song,
                    selected        = song.id == currentSong?.id,
                    isBatchSelected = song.id in selectedIds,
                    isEditing       = song.id == editingSongId,
                    isLocked        = isLocked,
                    selectionMode   = selectionMode,
                    onPlay          = { if (!isLocked) vm.selectSong(song, context) },
                    onToggleSelect  = { vm.toggleSelect(song.id) },
                    onActivateBatch = { vm.toggleSelect(song.id) },
                    onTitleSave     = { newTitle -> vm.updateTitle(song, newTitle) },
                    onEditStart     = { vm.startEditing(song.id) },
                    onOpenSheet     = { editSheet = song },
                    onDelete        = { vm.deleteSong(song) },
                    onQueueNext     = { vm.addToQueueNext(song) },
                    onQueueEnd      = { vm.addToQueueEnd(song) }
                )
            }
        }

        // Batch genre bar
        if (selectionMode) {
            GenreBar(
                count   = selectedIds.size,
                onGenre = { vm.applyGenre(it) },
                onClear = { vm.clearSelection() }
            )
        }
    }

    // Bottom-Sheet Editor
    if (editSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { editSheet = null },
            sheetState       = sheetState,
            containerColor   = BgCard
        ) {
            SongEditorSheet(
                song             = editSheet!!,
                songs            = songs,
                onSave           = { t, ar, bpmStr ->
                    val bpm = bpmStr.toIntOrNull() ?: editSheet!!.bpm
                    vm.updateTitle(editSheet!!, t)
                    vm.updateArtist(editSheet!!, ar)
                    scope.launch { sheetState.hide(); editSheet = null }
                },
                onAutoStopChange = { enabled -> vm.updateAutoStop(editSheet!!, enabled) },
                onCapoChange     = { delta -> vm.updateCapo(editSheet!!, delta) },
                onNavigate       = { newSong -> editSheet = newSong },
                onDismiss        = { scope.launch { sheetState.hide(); editSheet = null } }
            )
        }
    }
}

// ── Archiv Song Row ───────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivSongRow(
    index: Int, song: Song,
    selected: Boolean, isBatchSelected: Boolean, isEditing: Boolean,
    isLocked: Boolean, selectionMode: Boolean,
    onPlay: () -> Unit, onToggleSelect: () -> Unit, onActivateBatch: () -> Unit,
    onTitleSave: (String) -> Unit, onEditStart: () -> Unit,
    onOpenSheet: () -> Unit, onDelete: () -> Unit,
    onQueueNext: () -> Unit, onQueueEnd: () -> Unit
) {
    val editFR = remember { FocusRequester() }
    LaunchedEffect(isEditing) { if (isEditing) editFR.requestFocus() }

    var dragX            by remember { mutableStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val bgColor = when {
        isBatchSelected -> BgBatch
        selected        -> BgTrack
        else            -> BgCard
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = BgCard,
            title = { Text("Song löschen?", color = White, fontWeight = FontWeight.Bold) },
            text  = { Text("\"${song.title}\" wirklich aus dem Archiv löschen?",
                color = Gray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Löschen", color = RedStop, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen", color = Gray)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bgColor, shape = MaterialTheme.shapes.small)
            .pointerInput(selectionMode, isLocked) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!isLocked && !selectionMode) {
                            if (dragX > 80f) onQueueNext()
                            else if (dragX < -80f) onQueueEnd()
                        }
                        dragX = 0f
                    }
                ) { _, delta -> dragX += delta }
            }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (!isLocked) onPlay() },
                onLongClick = { if (!isLocked && !selectionMode) onActivateBatch() }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (isBatchSelected || selected) Volt else VoltDim,
            fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isEditing) {
                var editText by remember(song.id) { mutableStateOf(song.title) }
                BasicTextField(
                    value = editText, onValueChange = { editText = it }, singleLine = true,
                    textStyle = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Volt),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onTitleSave(editText) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(editFR)
                )
            } else {
                Text(
                    text = song.title, color = White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect()
                            else if (!isLocked) { if (selected) onEditStart() else onPlay() }
                        },
                        onLongClick = { if (!isLocked && !selectionMode) onActivateBatch() }
                    )
                )
            }
            val bpmTxt = if (song.bpmExact > 0f) "%.1f BPM".format(song.bpmExact) else "${song.bpm} BPM"
            val pre    = if (song.artist.isNotEmpty()) "${song.artist}  ·  " else ""
            val suf    = if (song.genre.isNotEmpty()) "  ·  ${song.genre}" else ""
            Text("$pre$bpmTxt  |  ${song.duration}$suf", color = Gray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Edit + Delete icons (hidden in batch/selection mode)
        if (!selectionMode) {
            Spacer(modifier = Modifier.width(6.dp))
            Column(
                modifier = Modifier.width(24.dp).height(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Icon(
                    Icons.Filled.Edit, contentDescription = "Bearbeiten",
                    tint = Gray, modifier = Modifier.size(18.dp).clickable { onOpenSheet() }
                )
                Icon(
                    Icons.Filled.Delete, contentDescription = "Löschen",
                    tint = RedStop, modifier = Modifier.size(18.dp).clickable { showDeleteDialog = true }
                )
            }
        }
    }
}

// ── Song Editor Bottom Sheet ───────────────────────────────────────────────────
@Composable
private fun SongEditorSheet(
    song: Song,
    songs: List<Song>,
    onSave: (String, String, String) -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onCapoChange: (Int) -> Unit,
    onNavigate: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var title    by remember(song.id) { mutableStateOf(song.title) }
    var artist   by remember(song.id) { mutableStateOf(song.artist) }
    var bpm      by remember(song.id) { mutableStateOf(song.bpm.toString()) }
    var capo     by remember(song.id) { mutableStateOf(song.capoPosition) }
    var autoStop by remember(song.id) { mutableStateOf(song.autoStop) }

    val idx     = songs.indexOfFirst { it.id == song.id }
    val hasPrev = idx > 0
    val hasNext = idx in 0 until songs.size - 1

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        // Navigation header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(enabled = hasPrev, onClick = { onNavigate(songs[idx - 1]) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Zurück",
                    tint = if (hasPrev) Volt else Gray, modifier = Modifier.size(28.dp))
            }
            Text(
                text = if (idx >= 0) "${idx + 1} / ${songs.size}" else "—",
                color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(enabled = hasNext, onClick = { onNavigate(songs[idx + 1]) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Weiter",
                    tint = if (hasNext) Volt else Gray, modifier = Modifier.size(28.dp))
            }
        }
        Text("Song bearbeiten", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp))
        SheetField("Titel", title) { title = it }
        Spacer(modifier = Modifier.height(12.dp))
        SheetField("Künstler", artist) { artist = it }
        Spacer(modifier = Modifier.height(12.dp))
        SheetField("BPM", bpm) { bpm = it }
        Spacer(modifier = Modifier.height(12.dp))
        // Capo stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capo", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("−", color = if (capo > 0) Volt else Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = capo > 0) { capo--; onCapoChange(-1) }
                    .padding(horizontal = 14.dp, vertical = 4.dp))
            Text(capo.toString(), color = if (capo > 0) Volt else Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(30.dp))
            Text("+", color = if (capo < 11) Volt else Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = capo < 11) { capo++; onCapoChange(1) }
                    .padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Auto-Stop toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Stop", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Song stoppt automatisch am Ende", color = Gray, fontSize = 11.sp)
            }
            Switch(
                checked = autoStop,
                onCheckedChange = { autoStop = it; onAutoStopChange(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Volt,
                    uncheckedThumbColor = Gray,
                    uncheckedTrackColor = BgTrack
                )
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BgTrack)) {
                Text("Abbrechen", color = Gray)
            }
            Button(onClick = { onSave(title, artist, bpm) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Volt)) {
                Text("Speichern", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SheetField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label, color = Gray) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = White, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Volt, unfocusedBorderColor = Gray,
            focusedTextColor = White, unfocusedTextColor = White,
            cursorColor = Volt, focusedLabelColor = Volt, unfocusedLabelColor = Gray
        )
    )
}

// ── Tab B: Playlist ───────────────────────────────────────────────────────────
@Composable
private fun PlaylistTab(vm: PlayerViewModel, isLocked: Boolean, loopActive: Boolean) {
    val context     = LocalContext.current
    val playlists   by vm.playlists.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying   by vm.isPlaying.collectAsState()
    val positionMs  by vm.positionMs.collectAsState()
    val durationMs  by vm.durationMs.collectAsState()

    var expandedId  by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Accordion list
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (playlists.isEmpty()) {
                item {
                    Text("Keine Sets vorhanden.\nSongs importieren und im Archiv Sets zuweisen.",
                        color = Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }
            playlists.forEach { playlist ->
                item(key = playlist.id) {
                    SetHeader(
                        playlist  = playlist,
                        expanded  = expandedId == playlist.id,
                        onToggle  = { expandedId = if (expandedId == playlist.id) null else playlist.id }
                    )
                }
                if (expandedId == playlist.id) {
                    item(key = "songs_${playlist.id}") {
                        SetSongList(
                            vm          = vm,
                            playlistId  = playlist.id,
                            currentSong = currentSong,
                            isLocked    = isLocked,
                            context     = context
                        )
                    }
                }
            }
        }

        // Stage transport controls
        StageTransport(
            isPlaying    = isPlaying,
            isLocked     = isLocked,
            loopActive   = loopActive,
            onPrevious   = { vm.skipPrevious() },
            onPlayPause  = { vm.togglePlayPause() },
            onToggleLoop = { vm.toggleLoop() },
            onNext       = { vm.skipNext() }
        )
    }
}

@Composable
private fun SetHeader(playlist: Playlist, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp)
            .background(BgTrack, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onToggle).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(playlist.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null, tint = Gray, modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SetSongList(
    vm: PlayerViewModel, playlistId: Long, currentSong: Song?,
    isLocked: Boolean, context: android.content.Context
) {
    val songs by vm.songs.collectAsState()
    val setSongs = songs.filter { it.playlistId == playlistId }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        setSongs.take(7).forEachIndexed { index, song ->
            StageSongRow(
                index    = index + 1,
                song     = song,
                selected = song.id == currentSong?.id,
                onClick  = { if (!isLocked) vm.selectSong(song, context) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        if (setSongs.size > 7) {
            Text("+${setSongs.size - 7} weitere Songs", color = Gray, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun StageSongRow(index: Int, song: Song, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp)
            .background(if (selected) BgTrack else BgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString().padStart(2, '0'),
            color = if (selected) Volt else VoltDim,
            fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(44.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Kapo: static text only — no editable controls
            val capoStr = if (song.capoPosition > 0) "Capo ${song.capoPosition}" else "Kein Capo"
            val bpmStr  = if (song.bpmExact > 0f) "%.1f BPM".format(song.bpmExact) else "${song.bpm} BPM"
            Text("$bpmStr  ·  $capoStr  ·  ${song.duration}", color = Gray, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Stage Transport ────────────────────────────────────────────────────────────
@Composable
private fun StageTransport(
    isPlaying: Boolean, isLocked: Boolean, loopActive: Boolean,
    onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onToggleLoop: () -> Unit, onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BgCard),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        TransportButton(Icons.Filled.SkipPrevious, "ZURÜCK",
            Modifier.weight(1f), enabled = !isLocked, onClick = onPrevious)
        TransportButton(
            icon     = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label    = if (isPlaying) "PAUSE" else "PLAY",
            modifier = Modifier.weight(1f),
            tint     = if (isPlaying) Volt else White,
            onClick  = onPlayPause
        )
        TransportButton(
            icon     = Icons.Filled.Repeat,
            label    = "LOOP",
            modifier = Modifier.weight(1f),
            tint     = if (loopActive) Volt else White,
            onClick  = onToggleLoop
        )
        TransportButton(Icons.Filled.SkipNext, "WEITER",
            Modifier.weight(1f), enabled = !isLocked, onClick = onNext)
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    tint: Color = White, enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        modifier = modifier.height(100.dp).background(BgCard)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label,
            tint = if (enabled) tint else Gray, modifier = Modifier.size(42.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = if (enabled) Gray else Color(0xFF444444),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Mini-Player (96dp) ─────────────────────────────────────────────────────────
@Composable
private fun MiniPlayer(
    song: Song?, nextSong: Song?,
    isPlaying: Boolean, positionMs: Long, durationMs: Long,
    onPause: () -> Unit
) {
    val progress  = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    val remainMs  = (durationMs - positionMs).coerceAtLeast(0L)
    val remSec    = remainMs / 1000
    val timeText  = if (song == null) "--:--" else "-%02d:%02d".format(remSec / 60, remSec % 60)
    val titleText = song?.title ?: "Kein Song geladen"

    Column(modifier = Modifier.fillMaxWidth().height(96.dp).background(BgPlayer)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Volt, trackColor = BgTrack, drawStopIndicator = {}
        )
        // Row 1: title + countdown + PAUSE button
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(titleText, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeText, color = VoltDim, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            // Red emergency PAUSE button (48×48)
            Box(
                modifier = Modifier.size(48.dp)
                    .background(RedStop, shape = MaterialTheme.shapes.small)
                    .clickable(onClick = onPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Pause",
                    tint = White, modifier = Modifier.size(28.dp)
                )
            }
        }
        // Row 2: next song preview
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val nextText = if (nextSong != null) {
                val capo = if (nextSong.capoPosition > 0) " (Capo ${nextSong.capoPosition})" else ""
                "Weiter: ${nextSong.title}$capo"
            } else "—"
            Icon(Icons.Filled.SkipNext, contentDescription = null,
                tint = Gray, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(nextText, color = Gray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Tab Buttons ────────────────────────────────────────────────────────────────
@Composable

// ── Search Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun SearchBar(active: Boolean, query: String, onToggle: () -> Unit, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) fr.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().background(BgDeep)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            TextField(
                value = query, onValueChange = onChange,
                placeholder = { Text("Titel, Artist, BPM, Genre…", color = Gray, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(fr),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedIndicatorColor = Volt, unfocusedIndicatorColor = Gray, cursorColor = Volt
                )
            )
            IconButton(onClick = onToggle) {
                Icon(Icons.Filled.Close, contentDescription = "Suche schließen", tint = Gray)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onToggle) {
                Icon(Icons.Filled.Search, contentDescription = "Suchen", tint = Gray,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Genre Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun GenreBar(count: Int, onGenre: (String) -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().height(80.dp).background(BgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$count Song${if (count == 1) "" else "s"} markiert",
                color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Auswahl aufheben",
                    tint = Gray, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Pop/Rock", "Folk/Country", "Deutsch", "Groove").forEach { g ->
                Button(
                    onClick = { onGenre(g) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(g, color = Volt, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
