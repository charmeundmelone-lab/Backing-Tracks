package de.minitraxx.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.minitraxx.app.R
import de.minitraxx.app.data.Slots
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.data.StemEntity
import de.minitraxx.app.util.formatFrames
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditorScreen(songId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SongRepository.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val songWithStems by repo.songDao.observeWithStems(songId).collectAsState(initial = null)

    var importingSlot by remember { mutableIntStateOf(-1) }
    var pendingSlot by remember { mutableIntStateOf(-1) }
    val genericError = stringResource(R.string.import_failed)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val slot = pendingSlot
        pendingSlot = -1
        if (uri == null || slot < 0) return@rememberLauncherForActivityResult
        importingSlot = slot
        scope.launch {
            try {
                val name = queryDisplayName(context, uri) ?: "Stem ${slot + 1}"
                repo.importStem(songId, slot, uri, name)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: genericError)
            } finally {
                importingSlot = -1
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(songWithStems?.song?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val data = songWithStems ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.song_duration, formatFrames(data.song.durationFrames)),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                stringResource(R.string.section_instruments),
                style = MaterialTheme.typography.titleMedium,
            )
            for (slot in 0 until Slots.INSTRUMENT_COUNT) {
                StemSlotCard(
                    slotLabel = stringResource(R.string.slot_instrument, slot + 1),
                    stem = data.stems.firstOrNull { it.slot == slot },
                    importing = importingSlot == slot,
                    onPick = {
                        pendingSlot = slot
                        picker.launch(arrayOf("audio/*"))
                    },
                    onRemove = { stem -> scope.launch { repo.removeStem(stem) } },
                    onGainChange = { stem, db -> scope.launch { repo.setStemGain(stem, db) } },
                )
            }

            Text(
                stringResource(R.string.section_click),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.section_click_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StemSlotCard(
                slotLabel = stringResource(R.string.slot_click),
                stem = data.stems.firstOrNull { it.slot == Slots.CLICK },
                importing = importingSlot == Slots.CLICK,
                onPick = {
                    pendingSlot = Slots.CLICK
                    picker.launch(arrayOf("audio/*"))
                },
                onRemove = { stem -> scope.launch { repo.removeStem(stem) } },
                onGainChange = { stem, db -> scope.launch { repo.setStemGain(stem, db) } },
            )
        }
    }
}

@Composable
private fun StemSlotCard(
    slotLabel: String,
    stem: StemEntity?,
    importing: Boolean,
    onPick: () -> Unit,
    onRemove: (StemEntity) -> Unit,
    onGainChange: (StemEntity, Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.GraphicEq,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(slotLabel, style = MaterialTheme.typography.labelMedium)
                    Text(
                        stem?.displayName ?: stringResource(R.string.slot_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (importing) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                } else {
                    OutlinedButton(onClick = onPick) {
                        Text(
                            stringResource(
                                if (stem == null) R.string.pick_file else R.string.replace_file
                            )
                        )
                    }
                    if (stem != null) {
                        IconButton(onClick = { onRemove(stem) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            }
            if (stem != null) {
                var gain by remember(stem.id, stem.gainDb) { mutableFloatStateOf(stem.gainDb) }
                Text(
                    stringResource(
                        R.string.stem_gain,
                        String.format(Locale.ROOT, "%+.1f", gain),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = gain,
                    onValueChange = { gain = it },
                    onValueChangeFinished = { onGainChange(stem, gain) },
                    valueRange = -24f..6f,
                )
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
