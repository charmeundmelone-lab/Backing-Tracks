package de.minitraxx.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import de.minitraxx.app.data.SetlistItemEntity
import de.minitraxx.app.data.SetlistItemWithSong
import de.minitraxx.app.data.SongRepository
import de.minitraxx.app.util.formatFrames
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    setlistId: Long,
    onBack: () -> Unit,
    onStartLive: (startIndex: Int) -> Unit,
    onOpenSong: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SongRepository.get(context) }
    val scope = rememberCoroutineScope()

    val setlist by repo.setlistDao.observeById(setlistId).collectAsState(initial = null)
    val dbItems by repo.setlistDao.observeItems(setlistId).collectAsState(initial = emptyList())

    // Lokale Kopie für flüssiges Drag-and-Drop; DB-Reihenfolge wird beim Loslassen geschrieben.
    var items by remember { mutableStateOf(listOf<SetlistItemWithSong>()) }
    var dragging by remember { mutableStateOf(false) }
    if (!dragging && items != dbItems) items = dbItems

    var showAddSheet by remember { mutableStateOf(false) }
    var showNewSong by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        dragging = true
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    fun addSongToSetlist(songId: Long, then: (() -> Unit)? = null) {
        scope.launch {
            val pos = repo.setlistDao.nextPosition(setlistId)
            repo.setlistDao.insertItem(
                SetlistItemEntity(setlistId = setlistId, songId = songId, position = pos)
            )
            then?.invoke()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(setlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            stringResource(R.string.add_songs),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onStartLive(0) },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(stringResource(R.string.start_live)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            itemsIndexed(items, key = { _, it -> it.item.id }) { index, entry ->
                ReorderableItem(reorderState, key = entry.item.id) { isDragging ->
                    Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
                        ListItem(
                            headlineContent = { Text(entry.song.title) },
                            supportingContent = {
                                Text(formatFrames(entry.song.durationFrames))
                            },
                            leadingContent = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStopped = {
                                            dragging = false
                                            scope.launch {
                                                repo.setlistDao.reorder(items.map { it.item.id })
                                            }
                                        }
                                    ),
                                ) {
                                    Icon(
                                        Icons.Filled.DragHandle,
                                        stringResource(R.string.reorder),
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onOpenSong(entry.song.id) }) {
                                        Icon(Icons.Filled.Edit, stringResource(R.string.edit_song))
                                    }
                                    IconButton(onClick = {
                                        scope.launch { repo.setlistDao.deleteItem(entry.item.id) }
                                    }) {
                                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                                    }
                                }
                            },
                            // Tipp auf den Song = Player ab diesem Song starten.
                            modifier = Modifier.clickableItem { onStartLive(index) },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.empty_setlist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { showAddSheet = true },
                            Modifier.padding(top = 16.dp),
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Text(
                                stringResource(R.string.add_songs),
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        val allSongs by repo.songDao.observeAll().collectAsState(initial = emptyList())
        val songIdsInSetlist = items.map { it.song.id }.toSet()
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            LazyColumn {
                // Direkt aus der Setlist heraus einen neuen Song anlegen.
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.create_new_song),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Add,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickableItem {
                            showAddSheet = false
                            showNewSong = true
                        },
                    )
                    HorizontalDivider()
                }
                items(allSongs, key = { it.id }) { song ->
                    val alreadyIn = song.id in songIdsInSetlist
                    ListItem(
                        headlineContent = { Text(song.title) },
                        supportingContent = {
                            val parts = buildList {
                                if (song.artist.isNotBlank()) add(song.artist)
                                if (alreadyIn) add(stringResource(R.string.already_in_setlist))
                            }
                            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                        },
                        trailingContent = {
                            Icon(
                                Icons.Filled.Add,
                                stringResource(R.string.add_songs),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickableItem { addSongToSetlist(song.id) },
                    )
                }
                if (allSongs.isEmpty()) {
                    item { EmptyHint(stringResource(R.string.empty_library)) }
                }
            }
        }
    }

    if (showNewSong) {
        NewSongDialog(
            onDismiss = { showNewSong = false },
            onConfirm = { title, artist ->
                showNewSong = false
                scope.launch {
                    // Song anlegen, sofort in die Setlist legen und den Editor
                    // öffnen, damit direkt Stems geladen werden können.
                    val id = repo.createSong(title, artist)
                    addSongToSetlist(id) { onOpenSong(id) }
                }
            },
        )
    }
}
