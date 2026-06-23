package de.livegigplayer.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SongInSet

private val GigBgDeep  = Color(0xFF0A0A0A)
private val GigBgCard  = Color(0xFF1A1A1A)
private val GigVolt    = Color(0xFFE8FF00)
private val GigWhite   = Color(0xFFFFFFFF)
private val GigGray    = Color(0xFF777777)

@Composable
fun GigManagementScreen(vm: GigViewModel) {
    val allGigs       by vm.allGigs.collectAsState()
    val selectedGigId by vm.selectedGigId.collectAsState()
    val setsForGig    by vm.setsForSelectedGig.collectAsState()

    val selectedGig = allGigs.find { it.gigId == selectedGigId }

    if (selectedGig == null) {
        GigListView(
            gigs     = allGigs,
            onSelect = { vm.selectGig(it.gigId) },
            onCreate = { name -> vm.createGig(name) }
        )
    } else {
        GigDetailView(
            vm       = vm,
            gig      = selectedGig,
            sets     = setsForGig,
            onBack   = { vm.selectGig(null) },
            onCreate = { name -> vm.createSetForGig(selectedGig.gigId, name, setsForGig.size) }
        )
    }
}

// ── Gig-Liste ────────────────────────────────────────────────────────────────

@Composable
private fun GigListView(
    gigs: List<GigEntity>,
    onSelect: (GigEntity) -> Unit,
    onCreate: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(GigBgDeep)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Gigs", color = GigWhite,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    Icons.Filled.Add, contentDescription = "Neuer Gig",
                    tint = GigVolt, modifier = Modifier.size(28.dp)
                )
            }
        }

        if (gigs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QueueMusic, contentDescription = null,
                        tint = GigGray, modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Noch keine Gigs", color = GigGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tippe + um einen Gig anzulegen", color = GigGray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(gigs, key = { it.gigId }) { gig ->
                    GigRow(gig = gig, onClick = { onSelect(gig) })
                }
            }
        }
    }

    if (showDialog) {
        CreateNameDialog(
            title       = "Neuer Gig",
            placeholder = "Name des Gigs …",
            onConfirm   = { name -> onCreate(name); showDialog = false },
            onDismiss   = { showDialog = false }
        )
    }
}

@Composable
private fun GigRow(gig: GigEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(GigBgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.MusicNote, contentDescription = null,
            tint = GigVolt, modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            gig.name,
            color = GigWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight, contentDescription = null,
            tint = GigGray, modifier = Modifier.size(24.dp)
        )
    }
}

// ── Gig-Detail (Sets) ─────────────────────────────────────────────────────────

@Composable
private fun GigDetailView(
    vm: GigViewModel,
    gig: GigEntity,
    sets: List<SetEntity>,
    onBack: () -> Unit,
    onCreate: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(GigBgDeep)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack, contentDescription = "Zurück",
                    tint = GigWhite, modifier = Modifier.size(26.dp)
                )
            }
            Text(
                gig.name,
                color = GigWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    Icons.Filled.Add, contentDescription = "Neues Set",
                    tint = GigVolt, modifier = Modifier.size(28.dp)
                )
            }
        }

        if (sets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QueueMusic, contentDescription = null,
                        tint = GigGray, modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Noch keine Sets in diesem Gig", color = GigGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tippe + um ein Set anzulegen", color = GigGray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(sets, key = { it.setId }) { set ->
                    SetCard(set = set, vm = vm)
                }
            }
        }
    }

    if (showDialog) {
        CreateNameDialog(
            title       = "Neues Set",
            placeholder = "Name des Sets …",
            onConfirm   = { name -> onCreate(name); showDialog = false },
            onDismiss   = { showDialog = false }
        )
    }
}

@Composable
private fun SetCard(set: SetEntity, vm: GigViewModel) {
    val songs by vm.getSongsInSet(set.setId).collectAsState(emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GigBgCard, shape = MaterialTheme.shapes.small)
    ) {
        // Set-Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d".format(set.position + 1),
                color = GigVolt, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.width(36.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                set.name,
                color = GigWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${songs.size} Songs",
                color = GigGray, fontSize = 11.sp
            )
        }

        // Song-Liste
        if (songs.isNotEmpty()) {
            songs.forEach { songInSet ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = 52.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%02d".format(songInSet.positionInSet + 1),
                        color = GigGray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            songInSet.song.title,
                            color = GigWhite, fontSize = 13.sp, fontWeight = FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val bpm = if (songInSet.song.bpmExact > 0f)
                            "%.1f BPM".format(songInSet.song.bpmExact)
                        else "${songInSet.song.bpm} BPM"
                        Text(bpm, color = GigGray, fontSize = 10.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// ── Dialog ────────────────────────────────────────────────────────────────────

@Composable
private fun CreateNameDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = GigBgCard,
        title  = { Text(title, color = GigWhite, fontWeight = FontWeight.Bold) },
        text   = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text(placeholder, color = GigGray, fontSize = 13.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GigVolt,
                    unfocusedBorderColor = GigGray,
                    focusedTextColor     = GigWhite,
                    unfocusedTextColor   = GigWhite,
                    cursorColor          = GigVolt,
                    focusedLabelColor    = GigVolt,
                    unfocusedLabelColor  = GigGray
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) }
            ) {
                Text("Erstellen", color = GigVolt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = GigGray)
            }
        }
    )
}
