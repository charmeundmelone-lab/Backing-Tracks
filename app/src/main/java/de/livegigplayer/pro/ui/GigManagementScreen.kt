package de.livegigplayer.pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SongInSet
import kotlin.math.roundToInt

private val GigBgDeep  = Color(0xFF0A0A0A)
private val GigBgCard  = Color(0xFF1A1A1A)
private val GigBgTrack = Color(0xFF2A2A2A)
private val GigVolt    = Color(0xFFE8FF00)
private val GigWhite   = Color(0xFFFFFFFF)
private val GigGray    = Color(0xFF777777)
private val GigRed     = Color(0xFFDC2626)

@Composable
fun GigManagementScreen(gigVm: GigViewModel, playerVm: PlayerViewModel, isLocked: Boolean = false) {
    val allGigs       by gigVm.allGigs.collectAsState()
    val selectedGigId by gigVm.selectedGigId.collectAsState()
    val setsForGig    by gigVm.setsForSelectedGig.collectAsState()

    val selectedGig = allGigs.find { it.gigId == selectedGigId }

    if (selectedGig == null) {
        GigListView(
            gigs      = allGigs,
            isLocked  = isLocked,
            onSelect  = { gigVm.selectGig(it.gigId) },
            onCreate  = { gigVm.createGig(it) },
            onDelete  = { gigVm.deleteGig(it) }
        )
    } else {
        GigDetailView(
            gigVm     = gigVm,
            playerVm  = playerVm,
            gig       = selectedGig,
            sets      = setsForGig,
            isLocked  = isLocked,
            onBack    = { gigVm.selectGig(null) },
            onCreate  = { gigVm.createSetForGig(selectedGig.gigId, it, setsForGig.size) },
            onDeleteSet = { gigVm.deleteSet(it) }
        )
    }
}

// ── Gig-Liste ────────────────────────────────────────────────────────────────

@Composable
private fun GigListView(
    gigs: List<GigEntity>,
    isLocked: Boolean,
    onSelect: (GigEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (GigEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(GigBgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gigs", color = GigWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = { showDialog = true }, enabled = !isLocked) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Gig",
                    tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigVolt, modifier = Modifier.size(26.dp))
            }
        }

        if (gigs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null,
                        tint = GigGray, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Noch keine Gigs", color = GigGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tippe + um einen Gig anzulegen", color = GigGray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(gigs, key = { it.gigId }) { gig ->
                    GigRow(
                        gig      = gig,
                        isLocked = isLocked,
                        onClick  = { onSelect(gig) },
                        onDelete = { onDelete(gig) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateNameDialog("Neuer Gig", "Name des Gigs …",
            onConfirm = { onCreate(it); showDialog = false },
            onDismiss = { showDialog = false })
    }
}

@Composable
private fun GigRow(gig: GigEntity, isLocked: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = GigBgCard,
            title = { Text("Gig löschen?", color = GigWhite, fontWeight = FontWeight.Bold) },
            text  = { Text("\"${gig.name}\" inklusive aller Sets wirklich löschen? " +
                "Das kann nicht rückgängig gemacht werden.", color = GigGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Löschen", color = GigRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen", color = GigGray) }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth().height(64.dp)
            .background(GigBgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null,
            tint = GigVolt, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(gig.name, color = GigWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { showDeleteDialog = true }, enabled = !isLocked, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Gig löschen",
                tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigRed, modifier = Modifier.size(18.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = GigGray, modifier = Modifier.size(24.dp))
    }
}

// ── Gig-Detail (Sets + Songs) ─────────────────────────────────────────────────

@Composable
private fun GigDetailView(
    gigVm: GigViewModel,
    playerVm: PlayerViewModel,
    gig: GigEntity,
    sets: List<SetEntity>,
    isLocked: Boolean,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onDeleteSet: (SetEntity) -> Unit
) {
    var showDialog   by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SetEntity?>(null) }

    val density        = LocalDensity.current
    val setSlotHeight  = 70.dp
    val setRowHeightPx = with(density) { setSlotHeight.toPx() }

    var sortSetsMode   by remember(gig.gigId) { mutableStateOf(false) }
    var localSetOrder  by remember(gig.gigId) { mutableStateOf(emptyList<Long>()) }
    var draggingSetId  by remember(gig.gigId) { mutableStateOf<Long?>(null) }
    var setDragOffset  by remember(gig.gigId) { mutableStateOf(0f) }

    LaunchedEffect(sets, draggingSetId) {
        if (draggingSetId == null) localSetOrder = sets.map { it.setId }
    }

    fun exitSortSetsMode() {
        gigVm.reorderSets(gig.gigId, localSetOrder)
        sortSetsMode = false
        draggingSetId = null
        setDragOffset = 0f
    }

    if (sortSetsMode) {
        BackHandler { exitSortSetsMode() }
    }

    val setMap = remember(sets) { sets.associateBy { it.setId } }

    Column(modifier = Modifier.fillMaxSize().background(GigBgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück",
                    tint = GigWhite, modifier = Modifier.size(26.dp))
            }
            Text(gig.name, color = GigWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { showDialog = true }, enabled = !isLocked) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Set",
                    tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigVolt, modifier = Modifier.size(26.dp))
            }
            if (sets.size > 1) {
                IconButton(
                    onClick  = { if (sortSetsMode) exitSortSetsMode() else sortSetsMode = true },
                    enabled  = !isLocked || sortSetsMode
                ) {
                    Icon(
                        if (sortSetsMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                        contentDescription = if (sortSetsMode) "Fertig" else "Sets sortieren",
                        tint = when {
                            sortSetsMode -> GigVolt
                            isLocked     -> GigGray.copy(alpha = 0.4f)
                            else         -> GigGray
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (sortSetsMode) {
            Text("Sets werden sortiert — ziehe am Handle, dann Häkchen zum Speichern",
                color = GigVolt, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }

        if (sets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null,
                        tint = GigGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Noch keine Sets in diesem Gig", color = GigGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tippe + um ein Set anzulegen", color = GigGray, fontSize = 12.sp)
                }
            }
        } else if (sortSetsMode) {
            // Set-Zeilen frei positioniert per Drag-Offset — gleiches Muster wie
            // SetSongRowSortable, nur eine Ebene höher (Sets statt Songs im Set).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(setSlotHeight * localSetOrder.size)
            ) {
                localSetOrder.forEachIndexed { index, setId ->
                    val set = setMap[setId] ?: return@forEachIndexed
                    key(setId) {
                        val isDragging = draggingSetId == setId
                        val targetOffset = index * setRowHeightPx
                        val animatedOffset by animateFloatAsState(
                            targetValue = targetOffset, label = "setReorderOffset"
                        )
                        val offsetPx = if (isDragging) targetOffset + setDragOffset else animatedOffset

                        SetRowSortable(
                            set             = set,
                            displayPosition = index,
                            isDragging      = isDragging,
                            modifier        = Modifier
                                .offset { IntOffset(0, offsetPx.roundToInt()) }
                                .zIndex(if (isDragging) 1f else 0f),
                            onDragStart = { draggingSetId = setId; setDragOffset = 0f },
                            onDrag = { delta ->
                                setDragOffset += delta
                                val currentIdx = localSetOrder.indexOf(setId)
                                val newIdx = ((currentIdx * setRowHeightPx + setDragOffset) / setRowHeightPx)
                                    .roundToInt().coerceIn(0, localSetOrder.size - 1)
                                if (newIdx != currentIdx) {
                                    val mutable = localSetOrder.toMutableList()
                                    mutable.removeAt(currentIdx)
                                    mutable.add(newIdx, setId)
                                    localSetOrder = mutable
                                    setDragOffset -= (newIdx - currentIdx) * setRowHeightPx
                                }
                            },
                            onDragEnd = {
                                draggingSetId = null
                                setDragOffset = 0f
                                gigVm.reorderSets(gig.gigId, localSetOrder)
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(sets, key = { it.setId }) { set ->
                    SetCard(
                        set               = set,
                        gigVm             = gigVm,
                        playerVm          = playerVm,
                        isLocked          = isLocked,
                        onDeleteSet       = { onDeleteSet(set) },
                        onRenameSet       = { renameTarget = set },
                        onResetCompleted  = { gigVm.resetCompletedForSet(set.setId) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateNameDialog("Neues Set", "Name des Sets …",
            onConfirm = { onCreate(it); showDialog = false },
            onDismiss = { showDialog = false })
    }

    renameTarget?.let { target ->
        CreateNameDialog(
            title        = "Set umbenennen",
            placeholder  = "Name des Sets …",
            initialValue = target.name,
            confirmLabel = "Speichern",
            onConfirm    = { gigVm.renameSet(target.setId, it); renameTarget = null },
            onDismiss    = { renameTarget = null }
        )
    }
}

@Composable
private fun SetRowSortable(
    set: SetEntity,
    displayPosition: Int,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag       by rememberUpdatedState(onDrag)
    val latestDragEnd    by rememberUpdatedState(onDragEnd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                if (isDragging) GigVolt.copy(alpha = 0.22f) else GigBgCard,
                shape = MaterialTheme.shapes.small
            )
            .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(displayPosition + 1), color = GigVolt,
            fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(set.name, color = GigWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = "Set verschieben",
            tint = if (isDragging) GigVolt else GigGray,
            modifier = Modifier
                .size(44.dp)
                .padding(8.dp)
                .pointerInput(set.setId) {
                    detectVerticalDragGestures(
                        onDragStart  = { latestDragStart() },
                        onDragEnd    = { latestDragEnd() },
                        onDragCancel = { latestDragEnd() }
                    ) { change, dragAmount ->
                        change.consume()
                        latestDrag(dragAmount)
                    }
                }
        )
    }
}

// ── Set-Karte mit Songs ───────────────────────────────────────────────────────

@Composable
private fun SetCard(
    set: SetEntity,
    gigVm: GigViewModel,
    playerVm: PlayerViewModel,
    isLocked: Boolean,
    onDeleteSet: () -> Unit,
    onRenameSet: () -> Unit,
    onResetCompleted: () -> Unit
) {
    val context     = LocalContext.current
    val songs       by gigVm.getSongsInSet(set.setId).collectAsState(emptyList())
    val currentSong by playerVm.currentSong.collectAsState()
    val density     = LocalDensity.current
    val rowHeightPx = with(density) { 72.dp.toPx() }

    var sortMode         by remember(set.setId) { mutableStateOf(false) }
    var editSongsMode    by remember(set.setId) { mutableStateOf(false) }
    var localOrder       by remember(set.setId) { mutableStateOf(emptyList<Long>()) }
    var draggingId       by remember(set.setId) { mutableStateOf<Long?>(null) }
    var dragOffset       by remember(set.setId) { mutableStateOf(0f) }
    var menuExpanded     by remember(set.setId) { mutableStateOf(false) }
    var showDeleteDialog by remember(set.setId) { mutableStateOf(false) }

    LaunchedEffect(set.setId) { gigVm.sanitizeSetPositions(set.setId) }
    LaunchedEffect(set.setId) { gigVm.armSetIfIdle(set.setId, playerVm) }

    // Lokale Reihenfolge folgt dem DB-Flow, außer während eines aktiven Drags —
    // sonst würde der gezogene Song wegspringen, sobald der Flow die noch
    // alte Reihenfolge nachliefert.
    LaunchedEffect(songs, draggingId) {
        if (draggingId == null) localOrder = songs.map { it.song.id }
    }

    fun exitSortMode() {
        gigVm.reorderSongsInSet(set.setId, localOrder, playerVm)
        sortMode = false
        draggingId = null
        dragOffset = 0f
    }

    if (sortMode) {
        BackHandler { exitSortMode() }
    }

    val songMap = remember(songs) { songs.associateBy { it.song.id } }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = GigBgCard,
            title = { Text("Set löschen?", color = GigWhite, fontWeight = FontWeight.Bold) },
            text  = { Text("\"${set.name}\" mit allen ${songs.size} zugeordneten Songs wirklich löschen? " +
                "Die Songs bleiben im Archiv erhalten, ihre Reihenfolge und Markierungen in diesem Set gehen verloren.",
                color = GigGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteSet() }) {
                    Text("Löschen", color = GigRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen", color = GigGray) }
            }
        )
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(GigBgCard, shape = MaterialTheme.shapes.small)
    ) {
        // Set-Header
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("%02d".format(set.position + 1), color = GigVolt,
                fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(set.name, color = GigWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${songs.size}", color = GigGray, fontSize = 12.sp)

            if (songs.size > 1) {
                IconButton(
                    onClick = {
                        if (sortMode) exitSortMode() else { sortMode = true; editSongsMode = false }
                    },
                    enabled = !isLocked || sortMode
                ) {
                    Icon(
                        if (sortMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                        contentDescription = if (sortMode) "Fertig" else "Sortieren",
                        tint = when { sortMode -> GigVolt; isLocked -> GigGray.copy(alpha = 0.4f); else -> GigGray },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (songs.isNotEmpty()) {
                IconButton(
                    onClick = {
                        editSongsMode = if (editSongsMode) false else { sortMode = false; true }
                    },
                    enabled = !isLocked || editSongsMode
                ) {
                    Icon(
                        if (editSongsMode) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (editSongsMode) "Fertig" else "Songs bearbeiten",
                        tint = when { editSongsMode -> GigVolt; isLocked -> GigGray.copy(alpha = 0.4f); else -> GigGray },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !isLocked) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen",
                        tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigGray, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = GigBgCard
                ) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen", color = GigWhite) },
                        onClick = { menuExpanded = false; onRenameSet() }
                    )
                    DropdownMenuItem(
                        text = { Text("Completed zurücksetzen", color = GigWhite) },
                        onClick = { menuExpanded = false; onResetCompleted() }
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen", color = GigRed) },
                        onClick = { menuExpanded = false; showDeleteDialog = true }
                    )
                }
            }
        }

        if (sortMode) {
            Text("Songs werden sortiert — ziehe am Handle, dann Häkchen zum Speichern",
                color = GigVolt, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        } else if (editSongsMode) {
            Text("Song-Bearbeitung aktiv — End-Aktion & Entfernen sichtbar",
                color = GigVolt, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }

        if (sortMode) {
            // Song-Zeilen frei positioniert per Drag-Offset — kein LazyColumn nötig,
            // Zeilenhöhe ist fix (72dp), Reihenfolge wird über localOrder gesteuert.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp * localOrder.size)
            ) {
                localOrder.forEachIndexed { index, songId ->
                    val songInSet = songMap[songId] ?: return@forEachIndexed
                    key(songId) {
                        val isDragging = draggingId == songId
                        val targetOffset = index * rowHeightPx
                        val animatedOffset by animateFloatAsState(
                            targetValue = targetOffset, label = "reorderOffset"
                        )
                        val offsetPx = if (isDragging) targetOffset + dragOffset else animatedOffset

                        SetSongRowSortable(
                            songInSet       = songInSet,
                            displayPosition = index,
                            isDragging      = isDragging,
                            modifier        = Modifier
                                .offset { IntOffset(0, offsetPx.roundToInt()) }
                                .zIndex(if (isDragging) 1f else 0f),
                            onDragStart = { draggingId = songId; dragOffset = 0f },
                            onDrag = { delta ->
                                dragOffset += delta
                                val currentIdx = localOrder.indexOf(songId)
                                val newIdx = ((currentIdx * rowHeightPx + dragOffset) / rowHeightPx)
                                    .roundToInt().coerceIn(0, localOrder.size - 1)
                                if (newIdx != currentIdx) {
                                    val mutable = localOrder.toMutableList()
                                    mutable.removeAt(currentIdx)
                                    mutable.add(newIdx, songId)
                                    localOrder = mutable
                                    dragOffset -= (newIdx - currentIdx) * rowHeightPx
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                gigVm.reorderSongsInSet(set.setId, localOrder, playerVm)
                            }
                        )
                    }
                }
            }
        } else {
            // Song-Zeilen — key() bindet UI-State (dragX, Dialog) an die Song-Identität,
            // nicht an die Listenposition. Verhindert Verwechslung beim Umsortieren.
            songs.forEach { songInSet ->
                key(songInSet.song.id) {
                    SetSongRow(
                        songInSet     = songInSet,
                        isCurrentSong = songInSet.song.id == currentSong?.id,
                        isEditing     = editSongsMode,
                        isLocked      = isLocked,
                        onPlay        = { gigVm.loadSetAsQueue(set.setId, songInSet.song.id, playerVm) },
                        onQueueNext   = {
                            gigVm.insertSpontaneousNext(set.setId, songInSet.song, playerVm)
                            Toast.makeText(context, "★ ${songInSet.song.title} → nächster", Toast.LENGTH_SHORT).show()
                        },
                        onQueueEnd    = {
                            gigVm.insertSpontaneousLater(set.setId, songInSet.song, playerVm)
                            Toast.makeText(context, "★ ${songInSet.song.title} → später", Toast.LENGTH_SHORT).show()
                        },
                        onRemove          = { gigVm.deleteSongFromSet(set.setId, songInSet.song.id, playerVm) },
                        onCycleEndAction  = {
                            gigVm.cycleEndAction(set.setId, songInSet.song.id, songInSet.endAction)
                            if (songInSet.song.id == currentSong?.id)
                                playerVm.activeEndAction.value = (songInSet.endAction + 1) % 3
                        }
                    )
                }
            }
        }

        if (songs.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SetSongRowSortable(
    songInSet: SongInSet,
    displayPosition: Int,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    // rememberUpdatedState: das pointerInput unten ist mit songInSet.song.id geschlüsselt
    // (bleibt über die ganze Drag-Geste stabil) — die Callbacks selbst müssen aber bei
    // jeder Neuordnung aktuell bleiben, sonst genau der Stale-Capture-Bug aus SetSongRow.
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag       by rememberUpdatedState(onDrag)
    val latestDragEnd    by rememberUpdatedState(onDragEnd)

    val bpmTxt   = if (songInSet.song.bpmExact > 0f)
        "%.1f BPM".format(songInSet.song.bpmExact) else "${songInSet.song.bpm} BPM"
    val pre      = if (songInSet.song.artist.isNotEmpty()) "${songInSet.song.artist}  ·  " else ""
    val subtitle = "$pre$bpmTxt  |  ${songInSet.song.duration}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(if (isDragging) GigVolt.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "%02d".format(displayPosition + 1),
                color = GigVolt, fontSize = 24.sp, fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(songInSet.song.title, color = GigWhite, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GigGray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = "Song verschieben",
            tint = if (isDragging) GigVolt else GigGray,
            modifier = Modifier
                .size(44.dp)
                .padding(8.dp)
                .pointerInput(songInSet.song.id) {
                    detectVerticalDragGestures(
                        onDragStart  = { latestDragStart() },
                        onDragEnd    = { latestDragEnd() },
                        onDragCancel = { latestDragEnd() }
                    ) { change, dragAmount ->
                        change.consume()
                        latestDrag(dragAmount)
                    }
                }
        )
    }
}

@Composable
private fun SetSongRow(
    songInSet: SongInSet,
    isCurrentSong: Boolean,
    isEditing: Boolean,
    isLocked: Boolean,
    onPlay: () -> Unit,
    onQueueNext: () -> Unit,
    onQueueEnd: () -> Unit,
    onRemove: () -> Unit,
    onCycleEndAction: () -> Unit = {}
) {
    var dragX by remember { mutableStateOf(0f) }
    var showAlreadyPlayedDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // rememberUpdatedState: hält die Callbacks/Flags aktuell, auch wenn der
    // pointerInput-Block (Key = isEditing/isLocked) nicht neu startet. Ohne das würden
    // onQueueNext/onQueueEnd auf den Song der ERSTEN Komposition eingefroren bleiben.
    val latestNext      by rememberUpdatedState(onQueueNext)
    val latestEnd       by rememberUpdatedState(onQueueEnd)
    val latestPlay      by rememberUpdatedState(onPlay)
    val latestCompleted by rememberUpdatedState(songInSet.completedInSet)

    val alpha    = if (songInSet.completedInSet && !isCurrentSong) 0.30f else 1f
    val endActionLabel = when (songInSet.endAction) { 1 -> "⏹" ; 2 -> "▶▶" ; else -> "⏸" }
    val voltColor = if (songInSet.spontaneousInSet) Color(0xFFFFD700) else GigVolt
    val bpmTxt   = if (songInSet.song.bpmExact > 0f)
        "%.1f BPM".format(songInSet.song.bpmExact) else "${songInSet.song.bpm} BPM"
    val pre      = if (songInSet.song.artist.isNotEmpty()) "${songInSet.song.artist}  ·  " else ""
    val subtitle = "$pre$bpmTxt  |  ${songInSet.song.duration}"

    fun guarded(action: () -> Unit) {
        if (latestCompleted) { pendingAction = action; showAlreadyPlayedDialog = true }
        else action()
    }

    if (showAlreadyPlayedDialog) {
        AlertDialog(
            onDismissRequest = { showAlreadyPlayedDialog = false },
            containerColor   = GigBgCard,
            title  = { Text("Heute bereits gespielt.", color = GigWhite, fontWeight = FontWeight.Bold) },
            text   = { Text("Trotzdem verwenden?", color = GigGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showAlreadyPlayedDialog = false; pendingAction?.invoke() }) {
                    Text("Ja", color = GigVolt, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadyPlayedDialog = false }) {
                    Text("Abbrechen", color = GigGray)
                }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            containerColor   = GigBgCard,
            title  = { Text("Aus Set entfernen?", color = GigWhite, fontWeight = FontWeight.Bold) },
            text   = { Text("\"${songInSet.song.title}\" aus diesem Set entfernen? " +
                "Der Song bleibt im Archiv erhalten.", color = GigGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showRemoveDialog = false; onRemove() }) {
                    Text("Entfernen", color = GigRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Abbrechen", color = GigGray)
                }
            }
        )
    }

    val interactive = !isEditing && !isLocked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(if (isCurrentSong) GigVolt.copy(alpha = 0.18f) else Color.Transparent)
            .alpha(alpha)
            .pointerInput(isEditing, isLocked) {
                if (interactive) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragX > 80f  -> guarded(latestNext)
                                dragX < -80f -> guarded(latestEnd)
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    ) { _, delta -> dragX += delta }
                }
            }
            .clickable(enabled = interactive, onClick = { guarded(latestPlay) })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (interactive) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null,
                tint = GigGray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }
        Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "%02d".format(songInSet.positionInSet + 1),
                color = voltColor, fontSize = 24.sp, fontWeight = FontWeight.Black
            )
            if (songInSet.spontaneousInSet) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Wunsch",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(8.dp).align(Alignment.TopEnd)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(songInSet.song.title, color = GigWhite, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GigGray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isEditing) {
            TextButton(onClick = onCycleEndAction, modifier = Modifier.defaultMinSize(minWidth = 40.dp)) {
                Text(endActionLabel, fontSize = 14.sp, color = GigGray)
            }
            IconButton(onClick = { showRemoveDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Aus Set entfernen",
                    tint = GigRed, modifier = Modifier.size(18.dp))
            }
        } else if (interactive) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = GigGray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }
    }
}

// ── Dialog ────────────────────────────────────────────────────────────────────

@Composable
private fun CreateNameDialog(
    title: String, placeholder: String,
    initialValue: String = "",
    confirmLabel: String = "Erstellen",
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = GigBgCard,
        title  = { Text(title, color = GigWhite, fontWeight = FontWeight.Bold) },
        text   = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(placeholder, color = GigGray, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GigVolt, unfocusedBorderColor = GigGray,
                    focusedTextColor = GigWhite, unfocusedTextColor = GigWhite,
                    cursorColor = GigVolt
                )
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) {
                Text(confirmLabel, color = GigVolt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = GigGray) }
        }
    )
}
