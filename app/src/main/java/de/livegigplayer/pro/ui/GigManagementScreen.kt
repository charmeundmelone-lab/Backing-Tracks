package de.livegigplayer.pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SetProgress
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.SongInSet
import kotlin.math.roundToInt

private val GigBgDeep  = Color(0xFF0A0A0A)
private val GigBgCard  = Color(0xFF1A1A1A)
private val GigBgTrack = Color(0xFF2A2A2A)
private val GigVolt    = Color(0xFFE8FF00)
private val GigWhite   = Color(0xFFFFFFFF)
private val GigGray    = Color(0xFF777777)
private val GigRed     = Color(0xFFDC2626)
private val GigCool    = Color(0xFF9FB2C4)

// "mm:ss" (song.duration) → Sekunden, für die Restzeit-Anzeige im Griff-Button.
private fun parseDurationSeconds(s: String): Int {
    val parts = s.split(":")
    val min = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val sec = parts.getOrNull(1)?.toIntOrNull() ?: return 0
    return min * 60 + sec
}

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
    var showOverview by remember { mutableStateOf(false) }

    val activeSetId by gigVm.activeSetId.collectAsState()
    val currentSet = remember(sets, activeSetId, gig.lastActiveSetId) {
        sets.firstOrNull { it.setId == activeSetId }
            ?: sets.firstOrNull { it.setId == gig.lastActiveSetId }
            ?: sets.firstOrNull()
    }

    // Beim Öffnen des Gigs (oder falls das zuvor aktive Set inzwischen gelöscht
    // wurde) das zuletzt aktive Set armen — ohne Unterbrechung, kein Auto-Play.
    LaunchedEffect(gig.gigId, sets) {
        val target = sets.firstOrNull { it.setId == gig.lastActiveSetId } ?: sets.firstOrNull()
        if (target != null && target.setId != activeSetId) {
            gigVm.switchToSet(gig.gigId, target.setId, playerVm)
        }
    }

    val currentSetSongs by remember(currentSet?.setId) {
        currentSet?.let { gigVm.getSongsInSet(it.setId) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    val currentCompleted = currentSetSongs.count { it.completedInSet }
    val currentTotal = currentSetSongs.size
    val remainingSeconds = currentSetSongs.filter { !it.completedInSet }
        .sumOf { parseDurationSeconds(it.song.duration) }
    val remainingMinutes = (remainingSeconds + 59) / 60

    Column(modifier = Modifier.fillMaxSize().background(GigBgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück",
                    tint = GigWhite, modifier = Modifier.size(26.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(gig.name, color = GigGray, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentSet != null) {
                    Text("  ›  ", color = GigGray.copy(alpha = 0.5f), fontSize = 13.sp)
                    Text(currentSet.name, color = GigWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Griff — deutlich als Button erkennbar (Rahmen + Hintergrund), zeigt das
        // aktive Set + Fortschritt, Tipp öffnet die Set-Übersicht.
        if (sets.isNotEmpty() && currentSet != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GigBgCard)
                    .border(1.dp, GigVolt.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { showOverview = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentSet.name, color = GigWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$currentCompleted/$currentTotal gespielt · ~$remainingMinutes Min", color = GigGray, fontSize = 12.sp)
                }
                Text("Wechseln", color = GigWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Filled.ExpandMore, contentDescription = "Sets-Übersicht",
                    tint = GigGray, modifier = Modifier.size(22.dp))
            }
        }

        if (sets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null,
                        tint = GigGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Noch keine Sets in diesem Gig", color = GigGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GigBgCard)
                            .border(1.dp, GigGray.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isLocked) { showDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null,
                            tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neues Set", color = if (isLocked) GigGray.copy(alpha = 0.4f) else GigWhite,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else if (currentSet != null) {
            SetCard(
                set              = currentSet,
                gigVm            = gigVm,
                playerVm         = playerVm,
                isLocked         = isLocked,
                onResetCompleted = { gigVm.resetCompletedForSet(currentSet.setId) },
                modifier         = Modifier.weight(1f)
            )
        }
    }

    if (showDialog) {
        CreateNameDialog("Neues Set", "Name des Sets …",
            onConfirm = { onCreate(it); showDialog = false },
            onDismiss = { showDialog = false })
    }

    if (showOverview) {
        SetSwitcherSheet(
            gig         = gig,
            sets        = sets,
            activeSetId = activeSetId,
            isLocked    = isLocked,
            gigVm       = gigVm,
            playerVm    = playerVm,
            onDismiss   = { showOverview = false },
            onCreateSet = { showDialog = true },
            onDeleteSet = onDeleteSet
        )
    }
}

// ── Set-Übersicht & -Verwaltung (Umschalten, Umbenennen, Löschen, Sortieren) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetSwitcherSheet(
    gig: GigEntity,
    sets: List<SetEntity>,
    activeSetId: Long?,
    isLocked: Boolean,
    gigVm: GigViewModel,
    playerVm: PlayerViewModel,
    onDismiss: () -> Unit,
    onCreateSet: () -> Unit,
    onDeleteSet: (SetEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renameTarget by remember { mutableStateOf<SetEntity?>(null) }
    var progressMap by remember { mutableStateOf<Map<Long, SetProgress>>(emptyMap()) }

    LaunchedEffect(sets) {
        progressMap = gigVm.setProgress(sets.map { it.setId })
    }

    val density        = LocalDensity.current
    val setSlotHeight  = 64.dp
    val setRowHeightPx = with(density) { setSlotHeight.toPx() }

    var sortMode       by remember { mutableStateOf(false) }
    var localOrder     by remember(sets) { mutableStateOf(sets.map { it.setId }) }
    var draggingSetId  by remember { mutableStateOf<Long?>(null) }
    var dragOffset     by remember { mutableStateOf(0f) }
    val setMap = remember(sets) { sets.associateBy { it.setId } }

    LaunchedEffect(sets, draggingSetId) {
        if (draggingSetId == null) localOrder = sets.map { it.setId }
    }

    fun exitSortMode() {
        gigVm.reorderSets(gig.gigId, localOrder)
        sortMode = false
        draggingSetId = null
        dragOffset = 0f
    }

    if (sortMode) {
        BackHandler { exitSortMode() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = GigBgCard
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Sets", color = GigWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLocked, onClick = onCreateSet)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null,
                        tint = if (isLocked) GigGray.copy(alpha = 0.4f) else GigWhite, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Neues Set", color = if (isLocked) GigGray.copy(alpha = 0.4f) else GigWhite,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (sets.size > 1) {
                    IconButton(
                        onClick = { if (sortMode) exitSortMode() else sortMode = true },
                        enabled = !isLocked || sortMode
                    ) {
                        Icon(
                            if (sortMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                            contentDescription = if (sortMode) "Fertig" else "Sortieren",
                            tint = when { sortMode -> GigVolt; isLocked -> GigGray.copy(alpha = 0.4f); else -> GigGray }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Automatisch ins nächste Set", color = GigWhite, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = gig.autoAdvanceSets,
                    onCheckedChange = { gigVm.setAutoAdvance(gig.gigId, it) },
                    enabled = !isLocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GigVolt,
                        checkedTrackColor = GigVolt.copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (sortMode) {
                Text("Sets werden sortiert — ziehe am Handle, dann Häkchen zum Speichern",
                    color = GigVolt, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(setSlotHeight * localOrder.size)) {
                    localOrder.forEachIndexed { index, setId ->
                        val set = setMap[setId] ?: return@forEachIndexed
                        key(setId) {
                            val isDragging = draggingSetId == setId
                            val targetOffset = index * setRowHeightPx
                            val animatedOffset by animateFloatAsState(
                                targetValue = targetOffset, label = "setReorderOffset"
                            )
                            val offsetPx = if (isDragging) targetOffset + dragOffset else animatedOffset

                            SetRowSortable(
                                set             = set,
                                displayPosition = index,
                                isDragging      = isDragging,
                                modifier        = Modifier
                                    .offset { IntOffset(0, offsetPx.roundToInt()) }
                                    .zIndex(if (isDragging) 1f else 0f),
                                onDragStart = { draggingSetId = setId; dragOffset = 0f },
                                onDrag = { delta ->
                                    dragOffset += delta
                                    val currentIdx = localOrder.indexOf(setId)
                                    val newIdx = ((currentIdx * setRowHeightPx + dragOffset) / setRowHeightPx)
                                        .roundToInt().coerceIn(0, localOrder.size - 1)
                                    if (newIdx != currentIdx) {
                                        val mutable = localOrder.toMutableList()
                                        mutable.removeAt(currentIdx)
                                        mutable.add(newIdx, setId)
                                        localOrder = mutable
                                        dragOffset -= (newIdx - currentIdx) * setRowHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggingSetId = null
                                    dragOffset = 0f
                                    gigVm.reorderSets(gig.gigId, localOrder)
                                }
                            )
                        }
                    }
                }
            } else {
                sets.forEach { set ->
                    key(set.setId) {
                        val progress = progressMap[set.setId]
                        SetSwitcherRow(
                            set       = set,
                            isActive  = set.setId == activeSetId,
                            completed = progress?.completed ?: 0,
                            total     = progress?.total ?: 0,
                            isLocked  = isLocked,
                            onClick   = { gigVm.switchToSet(gig.gigId, set.setId, playerVm); onDismiss() },
                            onRename  = { renameTarget = set },
                            onDelete  = { onDeleteSet(set) }
                        )
                    }
                }
            }
        }
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
private fun SetSwitcherRow(
    set: SetEntity,
    isActive: Boolean,
    completed: Int,
    total: Int,
    isLocked: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember(set.setId) { mutableStateOf(false) }
    var menuExpanded     by remember(set.setId) { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = GigBgCard,
            title = { Text("Set löschen?", color = GigWhite, fontWeight = FontWeight.Bold) },
            text  = { Text("\"${set.name}\" mit allen $total zugeordneten Songs wirklich löschen? " +
                "Die Songs bleiben im Archiv erhalten, ihre Reihenfolge und Markierungen in diesem Set gehen verloren.",
                color = GigGray, fontSize = 13.sp) },
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
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isActive) GigVolt.copy(alpha = 0.12f) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(set.position + 1), color = if (isActive) GigVolt else GigGray,
            fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(set.name, color = GigWhite, fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$completed/$total gespielt", color = GigGray, fontSize = 12.sp)
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
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Löschen", color = GigRed) },
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                )
            }
        }
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
    onResetCompleted: () -> Unit,
    modifier: Modifier = Modifier
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
    var showAddSongs     by remember(set.setId) { mutableStateOf(false) }

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

    Column(modifier = modifier
        .fillMaxWidth()
        .background(GigBgCard, shape = MaterialTheme.shapes.small)
    ) {
        // Set-Header — Name/Nummer/Songzahl stehen schon im Griff oben, hier nur
        // noch die song-bezogenen Aktionen (Sortieren/Bearbeiten/⋮).
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

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
                        text = { Text("Songs hinzufügen", color = GigWhite) },
                        onClick = { menuExpanded = false; showAddSongs = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Completed zurücksetzen", color = GigWhite) },
                        onClick = { menuExpanded = false; onResetCompleted() }
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

        // Song-Liste scrollbar: SetCard bekommt vom Eltern-Column weight(1f) (begrenzte
        // Höhe) → diese innere Column füllt den Rest und scrollt, wenn mehr Songs da sind
        // als aufs Display passen (Bug: bei 8 Songs waren die unteren nicht erreichbar).
        // Header + Status-Zeile oben bleiben fix.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
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

    if (showAddSongs) {
        val allSongs by playerVm.songs.collectAsState()
        val songIdsInGig by gigVm.getSongIdsInGig(set.gigOwnerId).collectAsState(emptyList())
        AddSongsToSetDialog(
            allSongs     = allSongs,
            alreadyInSet = songs.map { it.song.id }.toSet(),
            plannedInGig = songIdsInGig.toSet(),
            onConfirm    = { ids -> gigVm.addSongsToSet(set.setId, ids, playerVm); showAddSongs = false },
            onDismiss    = { showAddSongs = false }
        )
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

    // Gleiche Meta-Zeile wie in der normalen Set-Ansicht (Tonart · Kapo · Dauer) —
    // die Zeile soll beim Sortieren nicht plötzlich andere Angaben zeigen.
    val key  = songInSet.song.keySignature.trim()
    val capo = songInSet.song.capoPosition
    val metaParts = buildList {
        if (key.isNotEmpty()) add(key)
        if (capo > 0)         add("Kapo $capo")
        add(songInSet.song.duration)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            // Sortier-Modus ist an der Zeile selbst erkennbar: dezent abgesetzte
            // "Kacheln" (gleiche Palette wie die Karten, nur eine Stufe heller) mit
            // schmaler Lücke dazwischen — sonst wirken die Zeilen wie eine Fläche.
            .padding(vertical = 3.dp)
            .background(
                if (isDragging) GigVolt.copy(alpha = 0.22f) else GigBgTrack.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "%d".format(displayPosition + 1),
                color = GigGray, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(songInSet.song.title, color = GigWhite, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    metaParts.forEachIndexed { index, part ->
                        if (index > 0) append("  ·  ")
                        if (index == 0 && key.isNotEmpty()) {
                            withStyle(SpanStyle(color = GigCool, fontWeight = FontWeight.Medium)) { append(part) }
                        } else {
                            withStyle(SpanStyle(color = GigGray)) { append(part) }
                        }
                    }
                },
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
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
    val key  = songInSet.song.keySignature.trim()
    val capo = songInSet.song.capoPosition
    val metaParts = buildList {
        if (key.isNotEmpty()) add(key)
        if (capo > 0)         add("Kapo $capo")
        add(songInSet.song.duration)
    }

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
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
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
        if (isCurrentSong) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(GigVolt))
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (interactive) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null,
                tint = GigGray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "%d".format(songInSet.positionInSet + 1),
                color = GigGray, fontSize = 14.sp, fontWeight = FontWeight.Medium
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
            Text(songInSet.song.title, color = if (isCurrentSong) GigVolt else GigWhite, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    metaParts.forEachIndexed { index, part ->
                        if (index > 0) append("  ·  ")
                        if (index == 0 && key.isNotEmpty()) {
                            withStyle(SpanStyle(color = GigCool, fontWeight = FontWeight.Medium)) { append(part) }
                        } else {
                            withStyle(SpanStyle(color = GigGray)) { append(part) }
                        }
                    }
                },
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
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
private fun AddSongsToSetDialog(
    allSongs: List<Song>,
    alreadyInSet: Set<Long>,
    plannedInGig: Set<Long> = emptySet(),
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingConfirm by remember { mutableStateOf(false) }

    val available = remember(allSongs, alreadyInSet) {
        allSongs.filter { it.id !in alreadyInSet }
    }
    val filtered = remember(available, query) {
        if (query.isBlank()) available
        else available.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = GigBgCard,
        title = { Text("Songs zum Set hinzufügen", color = GigWhite, fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Suchen …", color = GigGray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GigVolt, unfocusedBorderColor = GigGray,
                        focusedTextColor = GigWhite, unfocusedTextColor = GigWhite,
                        cursorColor = GigVolt
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    available.isEmpty() -> Text(
                        "Alle Archiv-Songs sind bereits in diesem Set.",
                        color = GigGray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp)
                    )
                    filtered.isEmpty() -> Text(
                        "Keine Treffer.",
                        color = GigGray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp)
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(filtered, key = { it.id }) { song ->
                            val plannedElsewhere = song.id in plannedInGig
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = if (plannedElsewhere) 0.4f else 1f
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    .clickable {
                                        selectedIds = if (song.id in selectedIds)
                                            selectedIds - song.id else selectedIds + song.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = song.id in selectedIds,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + song.id else selectedIds - song.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = GigVolt, uncheckedColor = GigGray)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, color = GigWhite, fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = GigGray, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selectedIds.isNotEmpty(), onClick = {
                if (selectedIds.any { it in plannedInGig }) pendingConfirm = true
                else onConfirm(selectedIds.toList())
            }) {
                Text("Hinzufügen (${selectedIds.size})", color = GigVolt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = GigGray) }
        }
    )

    if (pendingConfirm) {
        AlertDialog(
            onDismissRequest = { pendingConfirm = false },
            containerColor   = GigBgCard,
            title = { Text("Schon im Gig verplant", color = GigWhite, fontWeight = FontWeight.Bold) },
            text  = { Text("Mindestens ein gewählter Song ist in diesem Gig bereits einem anderen Set zugeordnet. Trotzdem alle hinzufügen?",
                color = GigGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { pendingConfirm = false; onConfirm(selectedIds.toList()) }) {
                    Text("Trotzdem hinzufügen", color = GigVolt, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = false }) { Text("Abbrechen", color = GigGray) }
            }
        )
    }
}

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
