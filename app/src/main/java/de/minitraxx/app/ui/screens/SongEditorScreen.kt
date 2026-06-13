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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.minitraxx.app.data.EndAction
import de.minitraxx.app.data.Slots
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.data.StemEntity
import de.minitraxx.app.util.PdfChordImporter
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

    var showLyricsEditor by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    // Egal über welchen Knopf: PDFs werden immer durch den Parser geschickt,
    // Textdateien als Text gelesen — nie wieder Rohbytes im Songtext.
    fun runImport(uri: Uri) {
        importing = true
        scope.launch {
            try {
                val cp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    loadLyrics(context, uri)
                }
                songWithStems?.song?.let { repo.songDao.update(it.copy(chordPro = cp)) }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: genericError)
            } finally {
                importing = false
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) runImport(uri) }
    val lyricsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) runImport(uri) }

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

            // Notizen mit kurzer Verzögerung automatisch speichern.
            var notes by remember(data.song.id) { mutableStateOf(data.song.notes) }
            LaunchedEffect(notes) {
                if (notes != data.song.notes) {
                    kotlinx.coroutines.delay(600)
                    repo.songDao.update(data.song.copy(notes = notes))
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.song_notes)) },
                supportingText = { Text(stringResource(R.string.song_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.section_lyrics),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (data.song.chordPro.isBlank()) {
                            stringResource(R.string.lyrics_empty)
                        } else {
                            stringResource(
                                R.string.lyrics_status,
                                data.song.chordPro.lines().size,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                            Text(stringResource(R.string.lyrics_importing))
                        } else {
                            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                                Text(stringResource(R.string.lyrics_import_pdf))
                            }
                        }
                        OutlinedButton(onClick = { showLyricsEditor = true }) {
                            Text(stringResource(R.string.lyrics_edit))
                        }
                        TextButton(onClick = { lyricsPicker.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.lyrics_load_file))
                        }
                        if (data.song.chordPro.isNotBlank()) {
                            IconButton(onClick = {
                                scope.launch {
                                    repo.songDao.update(data.song.copy(chordPro = ""))
                                }
                            }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.lyrics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                stringResource(R.string.end_action_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    EndActionOption(
                        label = stringResource(R.string.end_action_load_next),
                        selected = data.song.endAction == EndAction.LOAD_NEXT,
                    ) { scope.launch { repo.songDao.update(data.song.copy(endAction = EndAction.LOAD_NEXT)) } }
                    EndActionOption(
                        label = stringResource(R.string.end_action_autoplay),
                        selected = data.song.endAction == EndAction.AUTOPLAY_NEXT,
                    ) { scope.launch { repo.songDao.update(data.song.copy(endAction = EndAction.AUTOPLAY_NEXT)) } }
                    EndActionOption(
                        label = stringResource(R.string.end_action_stop),
                        selected = data.song.endAction == EndAction.STOP,
                    ) { scope.launch { repo.songDao.update(data.song.copy(endAction = EndAction.STOP)) } }
                }
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

        if (showLyricsEditor) {
            LyricsEditorDialog(
                initial = data.song.chordPro,
                onDismiss = { showLyricsEditor = false },
                onSave = { text ->
                    showLyricsEditor = false
                    scope.launch { repo.songDao.update(data.song.copy(chordPro = text)) }
                },
            )
        }
    }
}

@Composable
private fun LyricsEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.section_lyrics)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().height(360.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EndActionOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableItem(onSelect)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
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

/**
 * Lädt Songtext aus einer Datei. PDFs (per MIME, Endung oder %PDF-Kennung im
 * Inhalt) gehen durch den koordinaten-genauen Parser; alles andere wird als
 * Text gelesen. So landen nie wieder PDF-Rohbytes im Songtext.
 */
private suspend fun loadLyrics(context: android.content.Context, uri: Uri): String {
    val name = queryDisplayName(context, uri)?.lowercase(Locale.ROOT) ?: ""
    val mime = context.contentResolver.getType(uri) ?: ""
    if (mime == "application/pdf" || name.endsWith(".pdf")) {
        return PdfChordImporter.import(context, uri)
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw PdfChordImporter.PdfImportException("Datei kann nicht geöffnet werden")
    if (bytes.size >= 5 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
    ) {
        return PdfChordImporter.import(context, uri)
    }
    if (bytes.size > 500_000) throw PdfChordImporter.PdfImportException("Datei zu groß")
    return String(bytes, Charsets.UTF_8)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
