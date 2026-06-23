package de.livegigplayer.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SongInSet

private val GigBgDeep  = Color(0xFF0A0A0A)
private val GigBgCard  = Color(0xFF1A1A1A)
private val GigBgTrack = Color(0xFF2A2A2A)
private val GigVolt    = Color(0xFFE8FF00)
private val GigWhite   = Color(0xFFFFFFFF)
private val GigGray    = Color(0xFF777777)
private val GigRed     = Color(0xFFDC2626)

@Composable
fun GigManagementScreen(gigVm: GigViewModel, playerVm: PlayerViewModel) {
    val allGigs       by gigVm.allGigs.collectAsState()
    val selectedGigId by gigVm.selectedGigId.collectAsState()
    val setsForGig    by gigVm.setsForSelectedGig.collectAsState()

    val selectedGig = allGigs.find { it.gigId == selectedGigId }
    var isEditing by remember { mutableStateOf(false) }

    if (selectedGig == null) {
        GigListView(
            gigs         = allGigs,
            isEditing    = isEditing,
            onToggleEdit = { isEditing = !isEditing },
            onSelect     = { gigVm.selectGig(it.gigId) },
            onCreate     = { gigVm.createGig(it) },
            onDelete     = { gigVm.deleteGig(it) }
        )
    } else {
        GigDetailView(
            gigVm        = gigVm,
            playerVm     = playerVm,
            gig          = selectedGig,
            sets         = setsForGig,
            isEditing    = isEditing,
            onToggleEdit = { isEditing = !isEditing },
            onBack       = { gigVm.selectGig(null) },
            onCreate     = { gigVm.createSetForGig(selectedGig.gigId, it, setsForGig.size) },
            onDeleteSet  = { gigVm.deleteSet(it) }
        )
    }
}

// ── Gig-Liste ────────────────────────────────────────────────────────────────

@Composable
private fun GigListView(
    gigs: List<GigEntity>,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
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
            if (!isEditing) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Neuer Gig",
                        tint = GigVolt, modifier = Modifier.size(26.dp))
                }
            }
            IconButton(onClick = onToggleEdit) {
                Icon(
                    if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (isEditing) "Fertig" else "Bearbeiten",
                    tint = if (isEditing) GigVolt else GigGray,
                    modifier = Modifier.size(22.dp)
                )
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
                        gig       = gig,
                        isEditing = isEditing,
                        onClick   = { if (!isEditing) onSelect(gig) },
                        onDelete  = { onDelete(gig) }
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
private fun GigRow(gig: GigEntity, isEditing: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth().height(64.dp)
            .background(GigBgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null,
            tint = if (isEditing) GigGray else GigVolt, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(gig.name, color = GigWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Gig löschen",
                    tint = GigRed, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = GigGray, modifier = Modifier.size(24.dp))
        }
    }
}

// ── Gig-Detail (Sets + Songs) ─────────────────────────────────────────────────

@Composable
private fun GigDetailView(
    gigVm: GigViewModel,
    playerVm: PlayerViewModel,
    gig: GigEntity,
    sets: List<SetEntity>,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onDeleteSet: (SetEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

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
            if (!isEditing) {
                IconButton(onClick = { gigVm.resetCompletedForSet(sets.firstOrNull()?.setId ?: -1) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Completed zurücksetzen",
                        tint = GigGray, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Neues Set",
                        tint = GigVolt, modifier = Modifier.size(26.dp))
                }
            }
            IconButton(onClick = onToggleEdit) {
                Icon(
                    if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (isEditing) "Fertig" else "Bearbeiten",
                    tint = if (isEditing) GigVolt else GigGray,
                    modifier = Modifier.size(22.dp)
                )
            }
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(sets, key = { it.setId }) { set ->
                    SetCard(
                        set          = set,
                        gigVm        = gigVm,
                        playerVm     = playerVm,
                        isEditing    = isEditing,
                        onDeleteSet  = { onDeleteSet(set) }
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
}

// ── Set-Karte mit Songs ───────────────────────────────────────────────────────

@Composable
private fun SetCard(
    set: SetEntity,
    gigVm: GigViewModel,
    playerVm: PlayerViewModel,
    isEditing: Boolean,
    onDeleteSet: () -> Unit
) {
    val songs by gigVm.getSongsInSet(set.setId).collectAsState(emptyList())

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
            if (isEditing) {
                IconButton(onClick = onDeleteSet) {
                    Icon(Icons.Filled.Delete, contentDescription = "Set löschen",
                        tint = GigRed, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Song-Zeilen
        songs.forEach { songInSet ->
            SetSongRow(
                songInSet    = songInSet,
                isEditing    = isEditing,
                onPlay       = { gigVm.loadSetAsQueue(set.setId, songInSet.song.id, playerVm) },
                onQueueNext  = { playerVm.addToQueueNext(songInSet.song) },
                onQueueEnd   = { playerVm.addToQueueEnd(songInSet.song) },
                onRemove     = { gigVm.deleteSongFromSet(set.setId, songInSet.song.id) }
            )
        }

        if (songs.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SetSongRow(
    songInSet: SongInSet,
    isEditing: Boolean,
    onPlay: () -> Unit,
    onQueueNext: () -> Unit,
    onQueueEnd: () -> Unit,
    onRemove: () -> Unit
) {
    var dragX by remember { mutableStateOf(0f) }
    val alpha = if (songInSet.completedInSet) 0.35f else 1f
    val bpm   = if (songInSet.song.bpmExact > 0f)
        "%.1f BPM".format(songInSet.song.bpmExact) else "${songInSet.song.bpm} BPM"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(alpha)
            .pointerInput(isEditing) {
                if (!isEditing) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragX > 80f  -> onQueueNext()
                                dragX < -80f -> onQueueEnd()
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    ) { _, delta -> dragX += delta }
                }
            }
            .clickable(enabled = !isEditing, onClick = onPlay)
            .padding(start = 52.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(songInSet.positionInSet + 1), color = GigGray,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(songInSet.song.title, color = GigWhite, fontSize = 13.sp,
                fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bpm, color = GigGray, fontSize = 10.sp)
        }
        if (isEditing) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Aus Set entfernen",
                    tint = GigRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Dialog ────────────────────────────────────────────────────────────────────

@Composable
private fun CreateNameDialog(
    title: String, placeholder: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
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
                Text("Erstellen", color = GigVolt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = GigGray) }
        }
    )
}
