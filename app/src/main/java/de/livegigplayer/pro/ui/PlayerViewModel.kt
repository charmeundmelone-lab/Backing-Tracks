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
                s.artist.contains(q, ignoreCase = true) ||
                s.bpm.toString().contains(q) ||
                s.keySignature.contains(q, ignoreCase = true) ||
                s.genre.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSong   = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    private val _isPlaying     = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _trackMode     = MutableStateFlow<TrackMode?>(null)
    val trackMode: StateFlow<TrackMode?> = _trackMode.asStateFlow()
    private val _showMixer     = MutableStateFlow(false)
    val showMixer: StateFlow<Boolean> = _showMixer.asStateFlow()
    private val _positionMs    = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs    = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    private val _isScanning    = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _scanProgress  = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()
    private val _importStatus  = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    // Batch-Selektion
    private val _selectedIds   = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Inline-Edit
    private val _editingSongId = MutableStateFlow<Long?>(null)
    val editingSongId: StateFlow<Long?> = _editingSongId.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) { _positionMs.value = engine.positionMs; _durationMs.value = engine.durationMs; delay(200L) }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun selectSong(song: Song, context: Context) {
        val mode = SongScanner.scan(song, context)
        if (!engine.activatePreloaded(song.id)) engine.load(mode)
        engine.setVolumeDb("drums", song.volDrums); engine.setVolumeDb("bass",   song.volBass)
        engine.setVolumeDb("keys",  song.volKeys);  engine.setVolumeDb("vocals", song.volVocals)
        engine.setVolumeDb("click", song.volClick); engine.setVolumeDb("cue",    song.volCue)
        _currentSong.value = song; _trackMode.value = mode; _isPlaying.value = false
        preloadNext(context)
    }

    private fun preloadNext(context: Context) {
        val list = songs.value; val idx = list.indexOfFirst { it.id == _currentSong.value?.id }
        if (idx in 0 until list.size - 1) {
            val next = list[idx + 1]
            viewModelScope.launch { val m = withContext(Dispatchers.IO) { SongScanner.scan(next, context) }; engine.preload(next.id, m) }
        }
    }

    fun importFolder(context: Context, uri: Uri) {
        Log.d("ImportFolder", "uri=$uri")
        _isScanning.value = true; _scanProgress.value = ""; _importStatus.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                FolderImporter.import(context, uri, dao) { name -> _scanProgress.value = name; count++ }
                _importStatus.value = if (count == 0) "Keine Songs gefunden – übergeordneten Ordner wählen." else "$count Songs importiert."
            } catch (e: Exception) { Log.e("ImportFolder", "failed", e); _importStatus.value = "Fehler: ${e.message}" }
            finally { _isScanning.value = false; _scanProgress.value = "" }
        }
    }

    fun togglePlayPause() { if (engine.isPlaying) { engine.pause(); _isPlaying.value = false } else { engine.play(); _isPlaying.value = true } }
    fun stopPlayback()    { engine.stop(); _isPlaying.value = false }
    fun skipPrevious()    { val l = songs.value; val i = l.indexOfFirst { it.id == _currentSong.value?.id }; if (i > 0) selectSong(l[i-1], getApplication()) else engine.seekTo(0L) }
    fun skipNext()        { val l = songs.value; val i = l.indexOfFirst { it.id == _currentSong.value?.id }; if (i in 0 until l.size-1) selectSong(l[i+1], getApplication()) }
    fun toggleMixer()     { _showMixer.value = !_showMixer.value }
    fun closeMixer()      { _showMixer.value = false }

    // Batch-Modus
    fun toggleSelect(id: Long) { _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id } }
    fun clearSelection()       { _selectedIds.value = emptySet() }
    fun applyGenre(genre: String) {
        viewModelScope.launch { _selectedIds.value.forEach { id -> songs.value.find { it.id == id }?.let { dao.update(it.copy(genre = genre)) } }; clearSelection() }
    }

    // Inline-Edit
    fun startEditing(id: Long)   { _editingSongId.value = id }
    fun stopEditing()            { _editingSongId.value = null }
    fun updateTitle(song: Song, newTitle: String) {
        if (newTitle.isBlank()) { stopEditing(); return }
        val u = song.copy(title = newTitle.trim()); stopEditing()
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }
    fun updateArtist(song: Song, newArtist: String) {
        val u = song.copy(artist = newArtist.trim())
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    // Kapo
    fun updateCapo(song: Song, delta: Int) {
        val u = song.copy(capoPosition = (song.capoPosition + delta).coerceIn(0, 11))
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    fun updateMixerVolume(trackName: String, volumeDb: Float) {
        val song = _currentSong.value ?: return; engine.setVolumeDb(trackName, volumeDb)
        val u = when (trackName) {
            "drums"->song.copy(volDrums=volumeDb);"bass"->song.copy(volBass=volumeDb)
            "keys" ->song.copy(volKeys =volumeDb);"vocals"->song.copy(volVocals=volumeDb)
            "click"->song.copy(volClick=volumeDb);"cue"->song.copy(volCue=volumeDb)
            else -> return
        }
        _currentSong.value = u; viewModelScope.launch { dao.update(u) }
    }

    fun resetAllMixer() {
        val s = _currentSong.value
        listOf("drums","bass","keys","vocals","click","cue").forEach { engine.setVolumeDb(it, 0f) }
        viewModelScope.launch { dao.resetAllMixerSettings() }
        if (s != null) _currentSong.value = s.copy(volDrums=0f,volBass=0f,volKeys=0f,volVocals=0f,volClick=0f,volCue=0f)
    }

    override fun onCleared() { super.onCleared(); engine.release() }
}
