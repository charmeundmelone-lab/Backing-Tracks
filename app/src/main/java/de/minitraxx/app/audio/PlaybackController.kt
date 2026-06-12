package de.minitraxx.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import de.minitraxx.app.data.SettingsStore
import de.minitraxx.app.data.Slots
import de.minitraxx.app.data.SongRepository
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueSong(
    val songId: Long,
    val title: String,
    val artist: String,
    val durationFrames: Long,
)

data class PlayerState(
    val queue: List<QueueSong> = emptyList(),
    val currentIndex: Int = -1,
    val positionFrames: Long = 0,
    val durationFrames: Long = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val currentSong: QueueSong? get() = queue.getOrNull(currentIndex)
    val nextSong: QueueSong? get() = queue.getOrNull(currentIndex + 1)
    val hasSession: Boolean get() = currentIndex in queue.indices
}

/**
 * Live-Logik: hält die Setlist-Queue, lädt Songs in die Engine und setzt das
 * vereinbarte Songende-Verhalten um — Stopp + nächsten Song laden ("armed"),
 * Wiedergabe erst auf Play-Befehl.
 */
class PlaybackController private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = SongRepository.get(context)
    private val settingsStore = SettingsStore.get(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var tickJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    init {
        scope.launch {
            settingsStore.settings.collect { s ->
                NativeEngine.setBusGains(s.mainGain, s.cueGain)
                NativeEngine.setSwapSides(s.swapSides)
            }
        }
    }

    /** Startet eine Live-Session mit der Setlist; lädt den Song an [startIndex] "armed". */
    fun startSetlist(setlistId: Long, startIndex: Int = 0) {
        scope.launch {
            val items = repo.setlistDao.getItems(setlistId)
            val queue = items.map {
                QueueSong(it.song.id, it.song.title, it.song.artist, it.song.durationFrames)
            }
            if (queue.isEmpty()) {
                _state.value = PlayerState(error = "Setlist ist leer")
                return@launch
            }
            _state.value = PlayerState(queue = queue)
            loadIndex(startIndex.coerceIn(queue.indices))
        }
    }

    fun loadIndex(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        scope.launch {
            NativeEngine.pause()
            _state.value = _state.value.copy(
                currentIndex = index, isLoading = true, isPlaying = false,
                positionFrames = 0, error = null,
            )
            val song = repo.songDao.getWithStems(queue[index].songId)
            if (song == null || song.stems.isEmpty()) {
                _state.value = _state.value.copy(isLoading = false, error = "Song hat keine Stems")
                return@launch
            }
            val paths = arrayOfNulls<String>(Slots.TOTAL)
            val gains = FloatArray(Slots.TOTAL) { 1f }
            for (stem in song.stems) {
                if (stem.slot !in 0 until Slots.TOTAL) continue
                paths[stem.slot] = repo.stemFile(stem.songId, stem.fileName).absolutePath
                gains[stem.slot] = dbToLinear(stem.gainDb)
            }
            val frames = NativeEngine.loadSong(paths, gains)
            if (frames < 0) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Stems konnten nicht geladen werden",
                )
            } else {
                _state.value = _state.value.copy(isLoading = false, durationFrames = frames)
            }
        }
    }

    fun playPause() {
        val s = _state.value
        if (!s.hasSession || s.isLoading) return
        if (s.isPlaying) {
            NativeEngine.pause()
            _state.value = s.copy(isPlaying = false)
        } else {
            if (!requestFocus()) {
                _state.value = s.copy(error = "Audiofokus nicht verfügbar")
                return
            }
            PlaybackService.start(context)
            if (NativeEngine.hadStreamError()) {
                // Stream nach Geräte-Disconnect neu öffnen.
                NativeEngine.start()
            }
            NativeEngine.play()
            _state.value = s.copy(isPlaying = true, error = null)
            startTicking()
        }
    }

    fun next() {
        val s = _state.value
        if (s.currentIndex + 1 in s.queue.indices) loadIndex(s.currentIndex + 1)
    }

    fun previous() {
        val s = _state.value
        // Erst zum Songanfang, bei erneutem Druck zum vorherigen Song.
        if (s.positionFrames > NativeEngine.SAMPLE_RATE * 3L && s.hasSession) {
            NativeEngine.seek(0)
            _state.value = s.copy(positionFrames = 0)
        } else if (s.currentIndex - 1 in s.queue.indices) {
            loadIndex(s.currentIndex - 1)
        } else if (s.hasSession) {
            NativeEngine.seek(0)
            _state.value = s.copy(positionFrames = 0)
        }
    }

    fun seekTo(frame: Long) {
        if (!_state.value.hasSession) return
        NativeEngine.seek(frame)
        _state.value = _state.value.copy(positionFrames = frame)
    }

    fun stopSession() {
        NativeEngine.stop()
        NativeEngine.unloadSong()
        abandonFocus()
        tickJob?.cancel()
        PlaybackService.stop(context)
        _state.value = PlayerState()
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                delay(100)
                val s = _state.value
                if (!s.hasSession) continue
                val pos = NativeEngine.positionFrames()
                val playing = NativeEngine.isPlaying()
                if (NativeEngine.isFinished()) {
                    // Songende: Stopp + nächsten Song laden, wartet auf Play.
                    if (s.currentIndex + 1 in s.queue.indices) {
                        loadIndex(s.currentIndex + 1)
                    } else {
                        _state.value = s.copy(isPlaying = false, positionFrames = s.durationFrames)
                        NativeEngine.stop()
                        PlaybackService.stop(context)
                    }
                } else if (NativeEngine.hadStreamError() && s.isPlaying) {
                    _state.value = s.copy(
                        isPlaying = false,
                        positionFrames = pos,
                        error = "Audioausgabe getrennt — Wiedergabe pausiert",
                    )
                } else if (s.positionFrames != pos || s.isPlaying != playing) {
                    _state.value = s.copy(positionFrames = pos, isPlaying = playing)
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> {
                        NativeEngine.pause()
                        _state.value = _state.value.copy(isPlaying = false)
                    }
                }
            }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    companion object {
        @Volatile
        private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController =
            instance ?: synchronized(this) {
                instance
                    ?: PlaybackController(context.applicationContext).also { instance = it }
            }
    }
}

private fun PlaybackService.Companion.start(context: Context) {
    context.startForegroundService(Intent(context, PlaybackService::class.java))
}

private fun PlaybackService.Companion.stop(context: Context) {
    context.stopService(Intent(context, PlaybackService::class.java))
}
