package de.livegigplayer.pro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.livegigplayer.pro.LiveGigPlayerApp
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SetSongCrossRef
import de.livegigplayer.pro.data.SongInSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GigViewModel(app: Application) : AndroidViewModel(app) {

    private val db     = (app as LiveGigPlayerApp).database
    private val gigDao = db.gigDao()
    private val setDao = db.setDao()

    val allGigs: StateFlow<List<GigEntity>> = gigDao.getAllGigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGigId = MutableStateFlow<Long?>(null)
    val selectedGigId: StateFlow<Long?> = _selectedGigId.asStateFlow()

    val setsForSelectedGig: StateFlow<List<SetEntity>> = _selectedGigId
        .flatMapLatest { gigId ->
            if (gigId != null) setDao.getSetsForGig(gigId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGig(id: Long?) { _selectedGigId.value = id }

    fun getSetsForGig(gigId: Long): Flow<List<SetEntity>> = setDao.getSetsForGig(gigId)

    fun getSongsInSet(setId: Long): Flow<List<SongInSet>> = setDao.getSongsInSet(setId)

    // ── Gig / Set / Song CRUD ────────────────────────────────────────────────

    fun createGig(name: String) {
        viewModelScope.launch(Dispatchers.IO) { gigDao.insert(GigEntity(name = name)) }
    }

    fun deleteGig(gig: GigEntity) {
        viewModelScope.launch(Dispatchers.IO) { gigDao.delete(gig) }
    }

    fun createSetForGig(gigId: Long, name: String, position: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            setDao.insertSet(SetEntity(gigOwnerId = gigId, name = name, position = position))
        }
    }

    fun deleteSet(set: SetEntity) {
        viewModelScope.launch(Dispatchers.IO) { setDao.deleteSet(set) }
    }

    fun addSongsToSet(setId: Long, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val startPos = (setDao.getMaxPositionInSet(setId) ?: -1) + 1
            songIds.forEachIndexed { index, songId ->
                setDao.insertCrossRef(SetSongCrossRef(setId, songId, startPos + index))
            }
            sanitizeSetPositionsInternal(setId)
        }
    }

    fun deleteSongFromSet(setId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            setDao.deleteCrossRef(setId, songId)
            sanitizeSetPositionsInternal(setId)
        }
    }

    fun markSongCompleted(setId: Long, songId: Long, completed: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) { setDao.markSongCompleted(setId, songId, completed) }
    }

    fun resetCompletedForSet(setId: Long) {
        viewModelScope.launch(Dispatchers.IO) { setDao.resetCompletedForSet(setId) }
    }

    fun sanitizeSetPositions(setId: Long) {
        viewModelScope.launch(Dispatchers.IO) { sanitizeSetPositionsInternal(setId) }
    }

    private suspend fun sanitizeSetPositionsInternal(setId: Long) {
        setDao.getSongsInSetOnce(setId).forEachIndexed { index, songInSet ->
            setDao.updateSongPosition(setId, songInSet.song.id, index)
        }
    }

    // ── Set-Wiedergabe ────────────────────────────────────────────────────────

    fun loadSetAsQueue(setId: Long, startSongId: Long, playerVm: PlayerViewModel) {
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) { setDao.getSongsInSetOnce(setId) }
            val startIdx = songs.indexOfFirst { it.song.id == startSongId }.coerceAtLeast(0)
            val toPlay = songs.subList(startIdx, songs.size).filter { !it.completedInSet }
            if (toPlay.isEmpty()) return@launch
            playerVm.clearQueue()
            playerVm.selectSong(toPlay.first().song, getApplication())
            toPlay.drop(1).forEach { playerVm.addToQueueEnd(it.song) }
            playerVm.onSongCompleted = { completedId ->
                viewModelScope.launch(Dispatchers.IO) {
                    setDao.markSongCompleted(setId, completedId, true)
                }
            }
        }
    }
}
