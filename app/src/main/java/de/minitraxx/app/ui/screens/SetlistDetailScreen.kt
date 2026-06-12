package de.minitraxx.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        dragging = true
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
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
            items(items, key = { it.item.id }) { entry ->
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
                                IconButton(onClick = {
                                    scope.launch { repo.setlistDao.deleteItem(entry.item.id) }
                                }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                                }
                            },
                            modifier = Modifier.clickableItem { onOpenSong(entry.song.id) },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item { EmptyHint(stringResource(R.string.empty_setlist)) }
            }
        }
    }

    if (showAddSheet) {
        val allSongs by repo.songDao.observeAll().collectAsState(initial = emptyList())
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            LazyColumn {
                items(allSongs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title) },
                        supportingContent = {
                            if (song.artist.isNotBlank()) Text(song.artist)
                        },
                        modifier = Modifier.clickableItem {
                            scope.launch {
                                val pos = repo.setlistDao.nextPosition(setlistId)
                                repo.setlistDao.insertItem(
                                    SetlistItemEntity(
                                        setlistId = setlistId,
                                        songId = song.id,
                                        position = pos,
                                    )
                                )
                            }
                        },
                    )
                }
                if (allSongs.isEmpty()) {
                    item { EmptyHint(stringResource(R.string.empty_library)) }
                }
            }
        }
    }
}
