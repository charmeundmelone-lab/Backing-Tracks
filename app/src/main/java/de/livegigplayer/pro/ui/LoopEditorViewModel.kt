package de.livegigplayer.pro.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.livegigplayer.pro.LiveGigPlayerApp
import de.livegigplayer.pro.audio.AuditionPlayer
import de.livegigplayer.pro.audio.SongScanner
import de.livegigplayer.pro.audio.WaveformAnalyzer
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

class LoopEditorViewModel(
    app: Application,
    private val song: Song
) : AndroidViewModel(app) {

    private val dao           = (app as LiveGigPlayerApp).database.songDao()
    private val auditionPlayer = AuditionPlayer(app)

    data class WaveformUiState(
        val isLoading: Boolean  = true,
        val samples: FloatArray? = null,   // downsampled max-envelope, normalized 0..1
        val onsets: LongArray?   = null,
        val durationMs: Long     = 0L,
        val auditionUri: String  = "",
        val errorMsg: String?    = null    // != null → Analyse fehlgeschlagen, Grund anzeigen
    )

    private val _waveformState = MutableStateFlow(WaveformUiState())
    val waveformState: StateFlow<WaveformUiState> = _waveformState.asStateFlow()

    private val _startMs     = MutableStateFlow(song.loopStartMs)
    val startMs: StateFlow<Long> = _startMs.asStateFlow()

    private val _endMs       = MutableStateFlow(song.loopEndMs)
    val endMs: StateFlow<Long> = _endMs.asStateFlow()

    private val _firstTapMs  = MutableStateFlow(-1L)
    val firstTapMs: StateFlow<Long> = _firstTapMs.asStateFlow()

    private val _isAuditioning = MutableStateFlow(false)
    val isAuditioning: StateFlow<Boolean> = _isAuditioning.asStateFlow()

    init { loadWaveform() }

    private fun loadWaveform() {
        viewModelScope.launch {
            _waveformState.value = WaveformUiState(isLoading = true)

            // ALLES in try/catch: eine ungefangene Exception würde sonst die
            // Coroutine killen und isLoading=true für immer stehen lassen → Dauerspinner.
            try {
                // Determine URI — IO thread
                val mode = withContext(Dispatchers.IO) { SongScanner.scan(song, getApplication()) }
                val uri  = when (mode) {
                    is TrackMode.Multitrack ->
                        mode.click ?: mode.drums ?: mode.bass ?: mode.keys ?: mode.vocals ?: mode.cue ?: ""
                    is TrackMode.Legacy -> mode.filePath
                }
                Log.d(TAG, "loadWaveform song=${song.id} uri='$uri'")

                if (uri.isEmpty()) {
                    _waveformState.value = WaveformUiState(
                        isLoading = false,
                        errorMsg  = "Keine Audiodatei gefunden (SAF-Pfad nicht auflösbar)."
                    )
                    return@launch
                }

                // Cache-first waveform load — IO thread
                val cached = withContext(Dispatchers.IO) {
                    WaveformAnalyzer.loadCache(getApplication(), song.id)
                }
                val raw = cached ?: run {
                    val analyzed = withContext(Dispatchers.IO) {
                        WaveformAnalyzer.analyze(getApplication(), uri, maxSamples = RAW_SAMPLES)
                    }
                    if (analyzed != null) withContext(Dispatchers.IO) {
                        WaveformAnalyzer.saveCache(getApplication(), song.id, analyzed)
                    }
                    analyzed
                }

                if (raw == null) {
                    _waveformState.value = WaveformUiState(
                        isLoading   = false,
                        durationMs  = 0L,
                        auditionUri = uri,
                        errorMsg    = "Wellenform-Analyse fehlgeschlagen (kein gültiges WAV?)."
                    )
                    return@launch
                }

                // Downsampling — CPU-bound, Default thread (Canvas bekommt max. TARGET_SAMPLES)
                val downsampled = withContext(Dispatchers.Default) {
                    downsample(raw.samples, TARGET_SAMPLES)
                }
                Log.d(TAG, "loadWaveform done: raw=${raw.samples.size} → ds=${downsampled.size} dur=${raw.durationMs}")

                val dur = raw.durationMs
                _waveformState.value = WaveformUiState(
                    isLoading   = false,
                    samples     = downsampled,
                    onsets      = raw.onsets,
                    durationMs  = dur,
                    auditionUri = uri
                )

                // Set default 8-bar loop if nothing stored
                if (_endMs.value <= _startMs.value + MIN_LOOP_MS && dur > 0L) {
                    val bpm = song.bpmExact.takeIf { it > 0f }
                        ?: song.bpm.toFloat().coerceAtLeast(1f)
                    val eightBarsMs = (8.0 * 4.0 * 60_000.0 / bpm).roundToLong()
                    setLoopBoth(0L, eightBarsMs.coerceAtMost(dur), dur)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadWaveform crashed for song ${song.id}", e)
                _waveformState.value = WaveformUiState(
                    isLoading = false,
                    errorMsg  = "Fehler beim Laden: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    // Max-envelope downsampling: one output point per chunk = peak RMS of that chunk
    private fun downsample(src: FloatArray, targetN: Int): FloatArray {
        if (src.size <= targetN) return src
        val out   = FloatArray(targetN)
        val chunk = src.size.toFloat() / targetN
        for (i in 0 until targetN) {
            val from = (i * chunk).toInt()
            val to   = ((i + 1) * chunk).toInt().coerceAtMost(src.size)
            var max  = 0f
            for (j in from until to) if (src[j] > max) max = src[j]
            out[i] = max
        }
        return out
    }

    // ── Loop controls ─────────────────────────────────────────────────────────

    fun setLoopStart(ms: Long) {
        _startMs.value = ms.coerceIn(0L, (_endMs.value - MIN_LOOP_MS).coerceAtLeast(0L))
    }

    fun setLoopEnd(ms: Long, maxMs: Long) {
        _endMs.value = ms.coerceIn(_startMs.value + MIN_LOOP_MS, maxMs)
    }

    fun setLoopBoth(start: Long, end: Long, maxMs: Long) {
        if (end <= start + MIN_LOOP_MS) return
        _startMs.value = start.coerceAtLeast(0L)
        _endMs.value   = end.coerceAtMost(maxMs)
    }

    fun nudgeStart(deltaMs: Long) {
        val n = (_startMs.value + deltaMs).coerceAtLeast(0L)
        if (n < _endMs.value - MIN_LOOP_MS) _startMs.value = n
    }

    fun nudgeEnd(deltaMs: Long, maxMs: Long) {
        val n = (_endMs.value + deltaMs).coerceAtMost(maxMs)
        if (n > _startMs.value + MIN_LOOP_MS) _endMs.value = n
    }

    fun tapCanvas(tappedMs: Long) {
        val first = _firstTapMs.value
        when {
            first < 0L -> {
                _firstTapMs.value = tappedMs
                _startMs.value    = tappedMs
                _endMs.value      = tappedMs
            }
            tappedMs > first + MIN_LOOP_MS -> {
                _endMs.value      = tappedMs
                _firstTapMs.value = -1L
            }
            else -> {
                _firstTapMs.value = tappedMs
                _startMs.value    = tappedMs
                _endMs.value      = tappedMs
            }
        }
    }

    // ── Audition controls ─────────────────────────────────────────────────────

    fun toggleAudition() {
        val uri = _waveformState.value.auditionUri
        if (uri.isEmpty()) return
        if (_isAuditioning.value) {
            auditionPlayer.stop()
            _isAuditioning.value = false
        } else {
            auditionPlayer.startLoop(uri, _startMs.value, _endMs.value)
            _isAuditioning.value = true
        }
    }

    fun refreshAudition() {
        if (!_isAuditioning.value) return
        val uri = _waveformState.value.auditionUri
        if (uri.isNotEmpty()) auditionPlayer.startLoop(uri, _startMs.value, _endMs.value)
    }

    fun stopAudition() { auditionPlayer.stop(); _isAuditioning.value = false }

    // ── Persistence ───────────────────────────────────────────────────────────

    fun save() {
        viewModelScope.launch { dao.updateLoopPoints(song.id, _startMs.value, _endMs.value) }
    }

    override fun onCleared() { super.onCleared(); auditionPlayer.release() }

    companion object {
        private const val TAG            = "LoopEditorVM"
        const val MIN_LOOP_MS            = 100L
        private const val RAW_SAMPLES    = 1000  // max points from analyzer (IO thread)
        private const val TARGET_SAMPLES = 800   // max points given to Canvas (always downsampled)

        fun factory(song: Song): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return LoopEditorViewModel(app, song) as T
            }
        }
    }
}
