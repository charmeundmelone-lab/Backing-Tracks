package de.livegigplayer.pro.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.livegigplayer.pro.LiveGigPlayerApp
import de.livegigplayer.pro.audio.AudioEngine
import de.livegigplayer.pro.audio.FolderImporter
import de.livegigplayer.pro.audio.SongScanner
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val dao    = (app as LiveGigPlayerApp).database.songDao()
    private val engine = AudioEngine(app)

    val songs: StateFlow<List<Song>> = dao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSongs: StateFlow<List<Song>> = songs
        .combine(_searchQuery) { list, q ->
            if (q.isBlank()) list
            else list.filter { s ->
                s.title.contains(q, ignoreCase = true) ||
                s.bpm.toString().contains(q) ||
                s.keySignature.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _trackMode = MutableStateFlow<TrackMode?>(null)
    val trackMode: StateFlow<TrackMode?> = _trackMode.asStateFlow()

    private val _showMixer = MutableStateFlow(false)
    val showMixer: StateFlow<Boolean> = _showMixer.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _positionMs.value = engine.positionMs
                _durationMs.value = engine.durationMs
                delay(200L)
            }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun selectSong(song: Song, context: Context) {
        val mode = SongScanner.scan(song, context)
        if (!engine.activatePreloaded(song.id)) {
            engine.load(mode)
        }
        engine.setVolumeDb("drums",  song.volDrums)
        engine.setVolumeDb("bass",   song.volBass)
        engine.setVolumeDb("keys",   song.volKeys)
        engine.setVolumeDb("vocals", song.volVocals)
        engine.setVolumeDb("click",  song.volClick)
        engine.setVolumeDb("cue",    song.volCue)
        _currentSong.value = song
        _trackMode.value   = mode
        _isPlaying.value   = false
        preloadNext(context)
    }

    private fun preloadNext(context: Context) {
        val list = songs.value
        val idx  = list.indexOfFirst { it.id == _currentSong.value?.id }
        if (idx in 0 until list.size - 1) {
            val next = list[idx + 1]
            viewModelScope.launch {
                val nextMode = withContext(Dispatchers.IO) { SongScanner.scan(next, context) }
                engine.preload(next.id, nextMode)
            }
        }
    }

    fun importFolder(context: Context, uri: Uri) {
        Log.d("ImportFolder", "importFolder called, uri=$uri")
        _isScanning.value    = true
        _scanProgress.value  = ""
        _importStatus.value  = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                FolderImporter.import(context, uri, dao) { name ->
                    _scanProgress.value = name
                    count++
                }
                _importStatus.value = if (count == 0)
                    "Keine Unterordner gefunden – bitte den Elternordner wählen."
                else "$count Songs importiert."
            } catch (e: Exception) {
                Log.e("ImportFolder", "Import FEHLGESCHLAGEN", e)
                _importStatus.value = "Fehler: ${e.message}"
            } finally {
                _isScanning.value   = false
                _scanProgress.value = ""
            }
        }
    }

    fun togglePlayPause() {
        if (engine.isPlaying) { engine.pause(); _isPlaying.value = false }
        else                  { engine.play();  _isPlaying.value = true  }
    }

    fun stopPlayback() { engine.stop(); _isPlaying.value = false }

    fun skipPrevious() {
        val list = songs.value
        val idx  = list.indexOfFirst { it.id == _currentSong.value?.id }
        if (idx > 0) selectSong(list[idx - 1], getApplication()) else engine.seekTo(0L)
    }

    fun skipNext() {
        val list = songs.value
        val idx  = list.indexOfFirst { it.id == _currentSong.value?.id }
        if (idx in 0 until list.size - 1) selectSong(list[idx + 1], getApplication())
    }

    fun toggleMixer() { _showMixer.value = !_showMixer.value }
    fun closeMixer()  { _showMixer.value = false }

    fun updateMixerVolume(trackName: String, volumeDb: Float) {
        val song = _currentSong.value ?: return
        engine.setVolumeDb(trackName, volumeDb)
        val updated = when (trackName) {
            "drums"  -> song.copy(volDrums  = volumeDb)
            "bass"   -> song.copy(volBass   = volumeDb)
            "keys"   -> song.copy(volKeys   = volumeDb)
            "vocals" -> song.copy(volVocals = volumeDb)
            "click"  -> song.copy(volClick  = volumeDb)
            "cue"    -> song.copy(volCue    = volumeDb)
            else -> return
        }
        _currentSong.value = updated
        viewModelScope.launch { dao.update(updated) }
    }

    fun resetAllMixer() {
        val song = _currentSong.value
        listOf("drums","bass","keys","vocals","click","cue").forEach { engine.setVolumeDb(it, 0f) }
        viewModelScope.launch { dao.resetAllMixerSettings() }
        if (song != null) _currentSong.value = song.copy(
            volDrums=0f, volBass=0f, volKeys=0f, volVocals=0f, volClick=0f, volCue=0f
        )
    }

    override fun onCleared() { super.onCleared(); engine.release() }
}
