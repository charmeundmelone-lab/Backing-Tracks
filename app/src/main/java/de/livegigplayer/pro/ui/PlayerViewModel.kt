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
    private val _showLyrics    = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()
    // Verhindert, dass der Lyrics-Screen bei jedem Play/Pause-Toggle erneut
    // automatisch aufgeht — nur beim ERSTEN Play eines frisch angewählten Songs.
    private var lyricsAutoShownForSongId: Long? = null

    // Diagnose-Log für den Lyrics-Teleprompter (Sprint 5.43): bewusst NICHT an
    // song.id/openSession gebunden wie zuvor in LyricsOverlay.kt selbst, sondern
    // hier im ViewModel über den gesamten App-Gebrauch hinweg — sonst geht der Log
    // eines Songs verloren, sobald das Auto-Advance/CUE-Arming (siehe skipNext())
    // lautlos auf den nächsten Song umschaltet, bevor der User den Share-Button
    // drücken konnte. Kein StateFlow nötig: wird nur beim Share-Tap gelesen, nicht
    // live gerendert.
    val lyricsDebugLog: MutableList<String> = mutableListOf()
    fun logLyricsDebug(msg: String) {
        Log.d("LyricsOverlay", msg)
        lyricsDebugLog.add(msg)
        if (lyricsDebugLog.size > 500) lyricsDebugLog.removeAt(0)
    }
    fun logLyricsWarn(msg: String) {
        Log.w("LyricsOverlay", msg)
        lyricsDebugLog.add("WARN: $msg")
        if (lyricsDebugLog.size > 500) lyricsDebugLog.removeAt(0)
    }
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

    // Einmaliger Hinweistext fürs LOOP-Verhalten (UI zeigt ihn als Toast und leert ihn danach)
    private val _loopHint = MutableStateFlow<String?>(null)
    val loopHint: StateFlow<String?> = _loopHint.asStateFlow()
    fun clearLoopHint() { _loopHint.value = null }

    fun openLyrics()  { _showLyrics.value = true }
    fun closeLyrics() { _showLyrics.value = false }

    fun updateLyrics(song: Song, lyrics: String) {
        val u = song.copy(lyrics = lyrics)
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    // Kalibrierungspunkte aus dem Teleprompter (ein Tap pro Songabschnitt) —
    // steuert die abschnittsweise Scroll-Geschwindigkeit. Siehe LyricsOverlay.
    fun updateLyricsSyncPoints(song: Song, points: String) {
        val u = song.copy(lyricsSyncPoints = points)
        viewModelScope.launch { dao.updateLyricsSyncPoints(song.id, points) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    fun updateLyricsLeadMs(song: Song, leadMs: Long) {
        val u = song.copy(lyricsLeadMs = leadMs)
        viewModelScope.launch { dao.updateLyricsLeadMs(song.id, leadMs) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    private val _queue           = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    // Tracks welches Set gerade aktiv ist (null = Library-Modus)
    private val _currentPlaylistId = MutableStateFlow<Long?>(null)
    val currentPlaylistId: StateFlow<Long?> = _currentPlaylistId.asStateFlow()

    // Gig-Set-Modus: Loop-Punkte sind schreibgeschützt, kein A/B-Setzen
    private val _isGigSetMode = MutableStateFlow(false)
    val isGigSetMode: StateFlow<Boolean> = _isGigSetMode.asStateFlow()

    val nextSong: StateFlow<Song?> = combine(_queue, songs, _currentSong, _currentPlaylistId) { q, list, current, playlistId ->
        when {
            q.isNotEmpty() -> q.first()
            playlistId != null -> {
                val setList = list.filter { it.playlistId == playlistId }
                val idx = setList.indexOfFirst { it.id == current?.id }
                if (idx in 0 until setList.size - 1) setList[idx + 1] else null
            }
            else -> {
                val idx = list.indexOfFirst { it.id == current?.id }
                if (idx in 0 until list.size - 1) list[idx + 1] else null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Live-Loop-States (Tab B) ───────────────────────────────────────────────
    // isArmed: Song hat gespeicherte Loop-Punkte in DB — rein aus currentSong abgeleitet
    val isArmed: StateFlow<Boolean> = _currentSong
        .map { it != null && it.loopStartMs > 0L && it.loopEndMs > it.loopStartMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isLoopActiveLive = MutableStateFlow(false)
    val isLoopActiveLive: StateFlow<Boolean> = _isLoopActiveLive.asStateFlow()

    private val _isExitPending = MutableStateFlow(false)
    val isExitPending: StateFlow<Boolean> = _isExitPending.asStateFlow()

    fun addToQueueNext(song: Song) { _queue.value = listOf(song) + _queue.value.filter { it.id != song.id } }
    fun addToQueueEnd(song: Song)  { _queue.value = _queue.value.filter { it.id != song.id } + listOf(song) }
    fun clearQueue()               { _queue.value = emptyList() }
    fun updateQueueAtomic(newQueue: List<Song>) { if (_queue.value != newQueue) _queue.value = newQueue }
    private fun dequeueFirst(): Song? {
        val first = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        return first
    }

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
                    && engine.isPlaying
                    && _loopState.value != LoopState.LOOPING) {
                    val next = nextSong.value
                    when {
                        next != null -> {
                            Log.d("PlayerViewModel", "Auto-Advance: Song-Ende → '${next.title}' (endAction=${activeEndAction.value})")
                            when (activeEndAction.value) {
                                1 -> { engine.pause(); _isPlaying.value = false }                   // STOP
                                0 -> { skipNext(); _isPlaying.value = false }                       // CUE (arm, kein Play)
                                else -> { skipNext(); engine.play(); _isPlaying.value = true }       // AUTOPLAY
                            }
                        }
                        _currentSong.value?.autoStop == true -> {
                            // Kein nächster Song + autoStop=true → Stopp
                            Log.d("PlayerViewModel", "Auto-Stop: Song-Ende, kein nächster Song.")
                            engine.pause(); engine.seekTo(0L)
                            _isPlaying.value = false
                            engine.deactivateLoop()
                            _loopState.value = LoopState.INACTIVE
                        }
                        // autoStop=false + kein nächster Song → Song loopt weiter (REPEAT_MODE_ONE)
                    }
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
                    if (_isLoopActiveLive.value && _isExitPending.value
                        && engine.positionMs >= (_loopEndMs.value ?: Long.MAX_VALUE)) {
                        // Quantized Exit: loopEndMs erreicht → linear weiterspielen, DB unangetastet
                        engine.deactivateLoop()
                        _isLoopActiveLive.value = false
                        _isExitPending.value    = false
                        _loopState.value        = LoopState.INACTIVE
                        _loopStartMs.value      = null
                        _loopEndMs.value        = null
                    } else if (!_isExitPending.value && engine.shouldCrossfade()) {
                        engine.performCrossfade()
                    }
                }
                delay(5L)
            }
        }
    }

    fun onLoopButtonPressed(currentPosMs: Long) {
        if (_isGigSetMode.value) {
            // Set-Modus: reiner Ein/Aus-Schalter, kein Schreiben in DB, kein A_SET
            val preStart = _loopStartMs.value
            val preEnd   = _loopEndMs.value
            when (_loopState.value) {
                LoopState.INACTIVE, LoopState.A_SET -> {
                    if (preStart != null && preEnd != null) {
                        _loopState.value = LoopState.LOOPING
                        engine.activateLoopDirect(preStart, preEnd)
                    } else {
                        _loopHint.value = "Kein Loop für diesen Song gespeichert"
                    }
                }
                LoopState.LOOPING -> {
                    engine.deactivateLoop()
                    _loopState.value = LoopState.INACTIVE
                    // Punkte bleiben in _loopStartMs/_loopEndMs (READY bleibt sichtbar)
                }
            }
            return
        }

        when (_loopState.value) {
            LoopState.INACTIVE -> {
                val preStart = _loopStartMs.value
                val preEnd   = _loopEndMs.value
                if (preStart != null && preEnd != null) {
                    // Vorgeladene Punkte vorhanden → direkt zu LOOPING springen
                    _loopState.value      = LoopState.LOOPING
                    engine.activateLoopDirect(preStart, preEnd)
                    _isLoopModified.value = false
                } else {
                    _loopStartMs.value    = currentPosMs
                    _loopEndMs.value      = null
                    _loopState.value      = LoopState.A_SET
                    _isLoopModified.value = true
                    engine.preloadLoopStart(currentPosMs)
                    _loopHint.value = "Punkt A gesetzt — nochmal LOOP tippen für Punkt B"
                }
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
                _loopHint.value = "Loop läuft — nochmal LOOP tippen zum Beenden"
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

    // Tab B Live-Modus: ORANGE → LIVE → EXIT_PENDING → (Quantized Exit zurück zu ORANGE)
    fun onSetLoopButtonPressed() {
        val song = _currentSong.value ?: return
        if (song.loopStartMs <= 0L || song.loopEndMs <= song.loopStartMs) return
        when {
            !_isLoopActiveLive.value -> {
                // ORANGE: Loop einschalten (DB-Punkte laden, Engine aktivieren)
                _loopStartMs.value      = song.loopStartMs
                _loopEndMs.value        = song.loopEndMs
                _loopState.value        = LoopState.LOOPING
                _isLoopActiveLive.value = true
                _isLoopModified.value   = false
                engine.activateLoopDirect(song.loopStartMs, song.loopEndMs)
            }
            !_isExitPending.value -> {
                // LIVE → Quantized Exit vormerken (kein DB-Zugriff)
                _isExitPending.value = true
            }
            // isExitPending == true: kein weiterer Eingriff, Timer übernimmt
        }
    }

    fun setLoopRange(startMs: Long, endMs: Long) {
        if (_isGigSetMode.value) return
        if (endMs - startMs < MIN_LOOP_DURATION_MS) return
        _loopStartMs.value = startMs
        _loopEndMs.value   = endMs
        if (_loopState.value == LoopState.LOOPING) {
            engine.updateLoopPoints(startMs, endMs)   // kein Seek auf active player
        }
        updateIsLoopModified()
    }

    fun nudgeLoopStart(deltaMs: Long) {
        if (_isGigSetMode.value) return
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        val new   = (start + deltaMs).coerceIn(0L, end - MIN_LOOP_DURATION_MS)
        _loopStartMs.value = new
        engine.updateLoopPoints(new, end)   // kein Seek auf active player
        updateIsLoopModified()
    }

    fun nudgeLoopEnd(deltaMs: Long) {
        if (_isGigSetMode.value) return
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        val dur   = engine.durationMs.let { if (it > 0) it else end + 30_000L }
        val new   = (end + deltaMs).coerceIn(start + MIN_LOOP_DURATION_MS, dur)
        _loopEndMs.value = new
        engine.updateLoopPoints(start, new)   // kein Seek auf active player
        updateIsLoopModified()
    }

    fun executeHardDatabaseSave() {
        if (_isGigSetMode.value) return
        val currentSong = _currentSong.value ?: return
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.forceUpdateLoopPoints(currentSong.id, start, end)
        }
        _currentSong.value    = currentSong.copy(loopStartMs = start, loopEndMs = end)
        _isLoopModified.value = false
    }

    fun saveLoopPoints() {
        if (_isGigSetMode.value) return
        val song  = _currentSong.value ?: return
        val start = _loopStartMs.value ?: return
        val end   = _loopEndMs.value   ?: return
        // Pure DB write — vollständig isoliert von AudioEngine und Queue
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSongLoopPoints(song.id, start, end)
        }
        _currentSong.value    = song.copy(loopStartMs = start, loopEndMs = end)
        _isLoopModified.value = false
    }

    fun clearLoop() {
        if (_isGigSetMode.value) return
        val song = _currentSong.value
        // Deaktiviert Loop-Engine ohne Playback zu unterbrechen
        engine.deactivateLoop()
        _loopState.value      = LoopState.INACTIVE
        _loopStartMs.value    = null
        _loopEndMs.value      = null
        _isLoopModified.value = false
        // DB async: Loop-Felder auf 0 zurücksetzen
        if (song != null) {
            viewModelScope.launch { dao.updateLoopPoints(song.id, 0L, 0L) }
            _currentSong.value = song.copy(loopStartMs = 0L, loopEndMs = 0L)
        }
    }

    private fun updateIsLoopModified() {
        val song = _currentSong.value
        _isLoopModified.value = _loopStartMs.value != song?.loopStartMs ||
                                _loopEndMs.value   != song?.loopEndMs
    }

    private fun resetLoopState() {
        engine.deactivateLoop()
        _loopState.value        = LoopState.INACTIVE
        _loopStartMs.value      = null
        _loopEndMs.value        = null
        _isLoopModified.value   = false
        _isLoopActiveLive.value = false
        _isExitPending.value    = false
    }

    fun updateAutoStop(song: Song, enabled: Boolean) {
        val u = song.copy(autoStop = enabled)
        viewModelScope.launch { dao.update(u) }
        if (_currentSong.value?.id == song.id) _currentSong.value = u
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun selectSong(song: Song, context: Context, sourcePlaylistId: Long? = null, isGigSet: Boolean = false) {
        resetLoopState()
        lyricsAutoShownForSongId = null
        _isGigSetMode.value = isGigSet
        _currentPlaylistId.value = sourcePlaylistId
        val mode = SongScanner.scan(song, context)
        if (!engine.activatePreloaded(song.id)) engine.load(mode)
        engine.setVolumeDb("drums", song.volDrums); engine.setVolumeDb("bass",   song.volBass)
        engine.setVolumeDb("keys",  song.volKeys);  engine.setVolumeDb("vocals", song.volVocals)
        engine.setVolumeDb("click", song.volClick); engine.setVolumeDb("cue",    song.volCue)
        _currentSong.value = song; _trackMode.value = mode; _isPlaying.value = false
        // Loop-Punkte vorladen wenn in DB gespeichert — aber NICHT automatisch aktivieren
        if (song.loopStartMs > 0L && song.loopEndMs > song.loopStartMs) {
            _loopStartMs.value    = song.loopStartMs
            _loopEndMs.value      = song.loopEndMs
            _isLoopModified.value = false
            // _loopState bleibt INACTIVE — User drückt LOOP um zu aktivieren
        }
        preloadNext(context)
    }

    private fun preloadNext(context: Context) {
        val list      = songs.value
        val currentId = _currentSong.value?.id ?: return
        val playlistId = _currentPlaylistId.value
        val next = when {
            _queue.value.isNotEmpty() -> _queue.value.first()
            playlistId != null -> {
                val setList = list.filter { it.playlistId == playlistId }
                val idx = setList.indexOfFirst { it.id == currentId }
                if (idx in 0 until setList.size - 1) setList[idx + 1] else null
            }
            else -> {
                val idx = list.indexOfFirst { it.id == currentId }
                if (idx in 0 until list.size - 1) list[idx + 1] else null
            }
        } ?: return
        viewModelScope.launch { val m = withContext(Dispatchers.IO) { SongScanner.scan(next, context) }; engine.preload(next.id, m) }
    }

    fun importFolder(context: Context, uri: Uri) {
        Log.d("ImportFolder", "uri=$uri")
        _isScanning.value = true; _scanProgress.value = ""; _importStatus.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                FolderImporter.import(context, uri, dao) { name -> _scanProgress.value = name; count++ }
                _importStatus.value = if (count == 0)
                    "Keine Songs gefunden. Erwartet wird entweder ein Ordner mit Unterordnern " +
                    "(je ein Song, WAV-Stems + optional eine Click-Datei drin) oder ein Ordner " +
                    "mit einzelnen WAV-Dateien direkt drin — übergeordneten Ordner wählen, falls " +
                    "die Struktur nicht passt."
                else "$count Songs importiert."
            } catch (e: Exception) { Log.e("ImportFolder", "failed", e); _importStatus.value = "Fehler: ${e.message}" }
            finally { _isScanning.value = false; _scanProgress.value = "" }
        }
    }

    fun togglePlayPause() {
        if (engine.isPlaying) {
            engine.pause(); _isPlaying.value = false
        } else {
            engine.play(); _isPlaying.value = true
            val song = _currentSong.value
            if (song != null && song.lyrics.isNotBlank() && lyricsAutoShownForSongId != song.id) {
                lyricsAutoShownForSongId = song.id
                _showLyrics.value = true
            }
        }
    }
    fun stopPlayback()    { engine.stop(); _isPlaying.value = false }
    fun seekTo(ms: Long)  { engine.seekTo(ms.coerceIn(0L, _durationMs.value)) }
    fun skipPrevious()    { val l = songs.value; val i = l.indexOfFirst { it.id == _currentSong.value?.id }; if (i > 0) selectSong(l[i-1], getApplication(), isGigSet = _isGigSetMode.value) else engine.seekTo(0L) }
    var onSongCompleted: ((songId: Long) -> Unit)? = null
    val activeEndAction = MutableStateFlow(0) // 0=CUE, 1=STOP, 2=AUTOPLAY — set by GigViewModel

    fun skipNext() {
        val completedId = _currentSong.value?.id
        val isGigSet    = _isGigSetMode.value
        val queued = dequeueFirst()
        if (queued != null) {
            completedId?.let { onSongCompleted?.invoke(it) }
            selectSong(queued, getApplication(), isGigSet = isGigSet)
            return
        }
        val next = nextSong.value ?: return
        completedId?.let { onSongCompleted?.invoke(it) }
        selectSong(next, getApplication(), _currentPlaylistId.value, isGigSet = isGigSet)
    }
    fun toggleMixer()  { _showMixer.value = !_showMixer.value }
    fun closeMixer()   { _showMixer.value = false }

    fun toggleSelect(id: Long) { _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id } }
    fun clearSelection()       { _selectedIds.value = emptySet() }
    fun applyGenre(genre: String) {
        viewModelScope.launch { _selectedIds.value.forEach { id -> songs.value.find { it.id == id }?.let { dao.update(it.copy(genre = genre)) } }; clearSelection() }
    }

    fun updateTitle(song: Song, newTitle: String) {
        if (newTitle.isBlank()) return
        val u = song.copy(title = newTitle.trim())
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
