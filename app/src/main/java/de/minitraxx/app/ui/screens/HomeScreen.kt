package de.minitraxx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.minitraxx.app.R
import de.minitraxx.app.audio.PedalAction
import de.minitraxx.app.audio.PedalManager
import de.minitraxx.app.data.SetlistEntity
import de.minitraxx.app.data.SettingsStore
import de.minitraxx.app.data.SongEntity
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.util.formatFrames
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSetlist: (Long) -> Unit,
    onOpenSong: (Long) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val repo = remember { SongRepository.get(context) }
    val scope = rememberCoroutineScope()

    var showNewSetlist by remember { mutableStateOf(false) }
    var showNewSong by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    label = { Text(stringResource(R.string.tab_setlists)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.LibraryMusic, null) },
                    label = { Text(stringResource(R.string.tab_library)) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showNewSetlist = true }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.new_setlist))
                }
            } else if (tab == 1) {
                FloatingActionButton(onClick = { showNewSong = true }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.new_song))
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> SetlistsTab(repo, padding, onOpenSetlist)
            1 -> LibraryTab(repo, padding, onOpenSong)
            2 -> SettingsTab(padding)
        }
    }

    if (showNewSetlist) {
        NameDialog(
            title = stringResource(R.string.new_setlist),
            label = stringResource(R.string.name),
            onDismiss = { showNewSetlist = false },
            onConfirm = { name ->
                showNewSetlist = false
                scope.launch { repo.setlistDao.insert(SetlistEntity(name = name)) }
            },
        )
    }
    if (showNewSong) {
        NewSongDialog(
            onDismiss = { showNewSong = false },
            onConfirm = { title, artist ->
                showNewSong = false
                scope.launch {
                    val id = repo.createSong(title, artist)
                    onOpenSong(id)
                }
            },
        )
    }
}

@Composable
private fun SetlistsTab(
    repo: SongRepository,
    padding: PaddingValues,
    onOpenSetlist: (Long) -> Unit,
) {
    val setlists by repo.setlistDao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<SetlistEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        items(setlists, key = { it.id }) { setlist ->
            ListItem(
                headlineContent = { Text(setlist.name) },
                trailingContent = {
                    IconButton(onClick = { deleteTarget = setlist }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                    }
                },
                modifier = Modifier.clickableItem { onOpenSetlist(setlist.id) },
            )
        }
        if (setlists.isEmpty()) {
            item { EmptyHint(stringResource(R.string.empty_setlists)) }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            text = stringResource(R.string.confirm_delete_setlist, target.name),
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch { repo.setlistDao.delete(target.id) }
            },
        )
    }
}

@Composable
private fun LibraryTab(
    repo: SongRepository,
    padding: PaddingValues,
    onOpenSong: (Long) -> Unit,
) {
    val songs by repo.songDao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<SongEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        items(songs, key = { it.id }) { song ->
            ListItem(
                headlineContent = { Text(song.title) },
                supportingContent = {
                    val info = listOf(song.artist, formatFrames(song.durationFrames))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    Text(info)
                },
                trailingContent = {
                    IconButton(onClick = { deleteTarget = song }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                    }
                },
                modifier = Modifier.clickableItem { onOpenSong(song.id) },
            )
        }
        if (songs.isEmpty()) {
            item { EmptyHint(stringResource(R.string.empty_library)) }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            text = stringResource(R.string.confirm_delete_song, target.title),
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch { repo.deleteSong(target.id) }
            },
        )
    }
}

@Composable
private fun SettingsTab(padding: PaddingValues) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.settings.collectAsState()
    val pedal = remember { PedalManager.get(context) }
    val learnTarget by pedal.learnTarget.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_swap), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.setting_swap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.swapSides,
                onCheckedChange = { v -> store.update { it.copy(swapSides = v) } },
            )
        }
        Column {
            Text(
                stringResource(R.string.setting_main_gain, percent(settings.mainGain)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.mainGain,
                onValueChange = { v -> store.update { it.copy(mainGain = v) } },
                valueRange = 0f..1.5f,
            )
        }
        Column {
            Text(
                stringResource(R.string.setting_cue_gain, percent(settings.cueGain)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.cueGain,
                onValueChange = { v -> store.update { it.copy(cueGain = v) } },
                valueRange = 0f..1.5f,
            )
        }
        Text(
            stringResource(R.string.routing_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.pedal_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.pedal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PedalLearnRow(
            label = stringResource(R.string.pedal_play_pause),
            keyName = PedalManager.keyName(settings.pedalPlayKey),
            learning = learnTarget == PedalAction.PLAY_PAUSE,
            onLearn = {
                if (learnTarget == PedalAction.PLAY_PAUSE) pedal.cancelLearning()
                else pedal.startLearning(PedalAction.PLAY_PAUSE)
            },
        )
        PedalLearnRow(
            label = stringResource(R.string.pedal_next),
            keyName = PedalManager.keyName(settings.pedalNextKey),
            learning = learnTarget == PedalAction.NEXT,
            onLearn = {
                if (learnTarget == PedalAction.NEXT) pedal.cancelLearning()
                else pedal.startLearning(PedalAction.NEXT)
            },
        )
    }
}

@Composable
private fun PedalLearnRow(
    label: String,
    keyName: String,
    learning: Boolean,
    onLearn: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (learning) stringResource(R.string.pedal_waiting)
                else stringResource(R.string.pedal_current_key, keyName),
                style = MaterialTheme.typography.bodySmall,
                color = if (learning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onLearn) {
            Text(
                stringResource(
                    if (learning) R.string.cancel else R.string.pedal_learn
                )
            )
        }
    }
}

private fun percent(v: Float): Int = (v * 100).toInt()

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(32.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun NewSongDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_song)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.song_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.song_artist)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), artist.trim()) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun ConfirmDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
