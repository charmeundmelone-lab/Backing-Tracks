package de.livegigplayer.pro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.livegigplayer.pro.LiveGigPlayerApp
import de.livegigplayer.pro.data.GigEntity
import de.livegigplayer.pro.data.SetEntity
import de.livegigplayer.pro.data.SetSongCrossRef
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

    fun createGig(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gigDao.insert(GigEntity(name = name))
        }
    }

    fun createSetForGig(gigId: Long, name: String, position: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            setDao.insertSet(SetEntity(gigOwnerId = gigId, name = name, position = position))
        }
    }

    fun addSongsToSet(setId: Long, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            songIds.forEachIndexed { index, songId ->
                setDao.insertCrossRef(
                    SetSongCrossRef(
                        setId         = setId,
                        songId        = songId,
                        positionInSet = index
                    )
                )
            }
        }
    }
}
