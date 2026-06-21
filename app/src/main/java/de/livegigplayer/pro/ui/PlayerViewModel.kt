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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoopState { INACTIVE, A_SET, LOOPING }

private const val MIN_LOOP_DURATION_MS = 500L

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

    private val _selectedIds   = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _editingSongId = MutableStateFlow<Long?>(null)
    val editingSongId: StateFlow<Long?> = _editingSongId.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    val nextSong: StateFlow<Song?> = _queue
        .combine(songs) { q, list ->
            if (q.isNotEmpty()) q.first()
            else {
                val idx = list.indexOfFirst { it.id == _currentSong.value?.id }
                if (idx in 0 until list.size - 1) list[idx + 1] else null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addToQueueNext(song: Song) { _queue.value = listOf(song) + _queue.value.filter { it.id != song.id } }
    fun addToQueueEnd(song: Song)  { _queue.value = _queue.value.filter { it.id != song.id } + listOf(song) }
    fun clearQueue()               { _queue.value = emptyList() }
    private fun dequeueFirst(): Song? {
        val first = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        return first
    }

    private val playlistDao = (app as LiveGigPlayerApp).database.playlistDao()
    val playlists = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── A/B Loop State Machine ─────────────────────────────────────────────────
    private val _loopState      = MutableStateFlow(LoopState.INACTIVE)
    val loopState: StateFlow<LoopState> = _loopState.asStateFlow()

    private val _loopStartMs    = MutableStateFlow<Long?>(null)
    val loopStartMs: StateFlow<Long?> = _loopStartMs.asStateFlow()

    private val _loopEndMs      = MutableStateFlow<Long?>(null)
    val loopEndMs: StateFlow<Long?> = _loopEndMs.asStateFlow()

    private val _isLoopModified = MutableStateFlow(false)
    val isLoopModified: StateFlow<Boolean> = _isLoopModified.asStateFlow()

    val loopActive: StateFlow<Boolean> = _loopState
        .map { it == LoopState.LOOPING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var _prevPositionMs = 0L

    init {
        // 200ms position/auto-stop poll
        viewModelScope.launch {
            while (true) {
                val pos = engine.positionMs
                val dur = engine.durationMs
                if (_prevPositionMs > 1000 && pos < _prevPositionMs - 1000
                    && _currentSong.value?.autoStop == true && engine.isPlaying
                    && _loopState.value != LoopState.LOOPING) {
                    Log.d("PlayerViewModel", "Auto-Stop: Song-Ende erkannt, stoppe.")
                    engine.pause(); engine.seekTo(0L)
                    _isPlaying.value = false
                    engine.deactivateLoop()
                    _loopState.value = LoopState.INACTIVE
                }
                _prevPositionMs = pos
                _positionMs.value = pos
                _durationMs.value = dur
                delay(200L)
            }
        }
        // 5ms crossfade monitor — only active during LOOPING
        viewModelScope.launch {
            while (true) {
                if (_loopState.value == LoopState.LOOPING && engine.isPlaying) {
                    if (engine.shouldCrossfade()) engine.performCrossfade()
                }
                delay(5L)
            }
        }
    }

    fun onLoopButtonPressed(currentPosMs: Long) {
        when (_loopState.value) {
            LoopState.INACTIVE -> {
                _loopStartMs.value = currentPosMs
                _loopEndMs.value   = null
                _loopState.value   = LoopState.A_SET
                _isLoopModified.value = true
                engine.preloadLoopStart(currentPosMs)
            }
            LoopState.A_SET -> {
                val start = _loopStartMs.value ?: return
                val rawEnd = if (currentPosMs - start < MIN_LOOP_DURATION_MS)
                    start + MIN_LOOP_DURATION_MS else currentPosMs
                val dur    = engine.durationMs.let { if (it > 0) it else Long.MAX_VALUE }
                val end    = rawEnd.coerceAtMost(dur)
                _loopEndMs.value = end
                _loopState.value = LoopState.LOOPING
                engine.activateLoopDirect(start, end)
                updateIsLoopModified()
            }
            LoopState.LOOPING -> {
                engine.deactivateLoop()
                _loopStartMs.value    = null
                _loopEndMs.value      = null
                _loopState.value      = LoopState.INACTIVE
                _isLoopModified.value = false
            }
        }
    }

    fun setLoopRange(startMs: Long, endMs: Long) {
        if (endMs - startMs < MIN_LOOP_DURATION_MS) return
        _loopStartMs.value = startMs
        _loopEndMs.value   = endMs
        if (_loopState.value == LoopState.LOOPING) {
            engine.activateLoopDirect(startMs, endMs)
        }
        updateIsLoopModified()
    }

    fun nudgeLoopStart(deltaMs: Long) {
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        val new   = (start + deltaMs).coerceIn(0L, end - MIN_LOOP_DURATION_MS)
        _loopStartMs.value = new
        engine.activateLoopDirect(new, end)
        updateIsLoopModified()
    }

    fun nudgeLoopEnd(deltaMs: Long) {
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        val dur   = engine.durationMs.let { if (it > 0) it else end + 30_000L }
        val new   = (end + deltaMs).coerceIn(start + MIN_LOOP_DURATION_MS, dur)
        _loopEndMs.value = new
        engine.activateLoopDirect(start, new)
        updateIsLoopModified()
    }

    fun saveLoopPoints() {
        val song  = _currentSong.value ?: return
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        viewModelScope.launch { dao.updateLoopPoints(song.id, start, end) }
        _currentSong.value    = song.copy(loopStartMs = start, loopEndMs = end)
        _isLoopModified.value = false
    }

    private fun updateIsLoopModified() {
        val song = _currentSong.value
        _isLoopModified.value = _loopStartMs.value != song?.loopStartMs ||
                                _loopEndMs.value   != song?.loopEndMs
    }

    private fun resetLoopState() {
        engine.deactivateLoop()
        _loopState.value      = LoopState.INACTIVE
        _loopStartMs.value    = null
        _loopEndMs.value      = null
        _isLoopModified.value = false
    }

    fun updateAutoStop(song: Song, enabled: Boolean) {
        val u = song.copy(autoStop = enabled)
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun selectSong(song: Song, context: Context) {
        resetLoopState()
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
    fun skipNext() {
        val queued = dequeueFirst()
        if (queued != null) { selectSong(queued, getApplication()); return }
        val l = songs.value; val i = l.indexOfFirst { it.id == _currentSong.value?.id }
        if (i in 0 until l.size - 1) selectSong(l[i + 1], getApplication())
    }
    fun toggleMixer()  { _showMixer.value = !_showMixer.value }
    fun closeMixer()   { _showMixer.value = false }

    fun toggleSelect(id: Long) { _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id } }
    fun clearSelection()       { _selectedIds.value = emptySet() }
    fun applyGenre(genre: String) {
        viewModelScope.launch { _selectedIds.value.forEach { id -> songs.value.find { it.id == id }?.let { dao.update(it.copy(genre = genre)) } }; clearSelection() }
    }

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

    fun deleteSong(song: Song) {
        viewModelScope.launch { dao.delete(song) }
        if (_currentSong.value?.id == song.id) {
            engine.stop(); _currentSong.value = null; _isPlaying.value = false
        }
    }

    fun deleteAllSongs() {
        viewModelScope.launch { dao.deleteAll() }
        engine.stop(); _currentSong.value = null; _isPlaying.value = false
    }

    fun resetAllMixer() {
        val s = _currentSong.value
        listOf("drums","bass","keys","vocals","click","cue").forEach { engine.setVolumeDb(it, 0f) }
        viewModelScope.launch { dao.resetAllMixerSettings() }
        if (s != null) _currentSong.value = s.copy(volDrums=0f,volBass=0f,volKeys=0f,volVocals=0f,volClick=0f,volCue=0f)
    }

    override fun onCleared() { super.onCleared(); engine.release() }
}
