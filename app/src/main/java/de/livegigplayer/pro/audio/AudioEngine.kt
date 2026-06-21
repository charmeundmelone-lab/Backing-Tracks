package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import de.livegigplayer.pro.data.TrackMode
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

private const val TAG = "AudioEngine"

class AudioEngine(private val context: Context) {

    private data class TrackPair(val name: String, val uri: String, val playerA: ExoPlayer, val playerB: ExoPlayer)
    private data class Track(val name: String, val player: ExoPlayer, val uri: String)

    private val tracks     = mutableListOf<TrackPair>()
    private val nextTracks = mutableListOf<Track>()
    private var nextSongId = -1L

    var isPlaying          = false; private set
    var loopActive         = false; private set
    private var loopStartMs        = 0L
    private var loopEndMs          = 0L
    private var originalDurationMs = 0L
    private var isAActive          = true

    // ── Position / Duration ───────────────────────────────────────────────────

    val positionMs: Long get() =
        (if (isAActive) tracks.firstOrNull()?.playerA else tracks.firstOrNull()?.playerB)
            ?.currentPosition ?: 0L

    val durationMs: Long get() =
        if (loopActive) originalDurationMs
        else (if (isAActive) tracks.firstOrNull()?.playerA else tracks.firstOrNull()?.playerB)
            ?.duration?.takeIf { it > 0 } ?: 0L

    // ── Active / Idle shortcuts ───────────────────────────────────────────────

    private val active: List<ExoPlayer> get() =
        if (isAActive) tracks.map { it.playerA } else tracks.map { it.playerB }

    private val idle: List<ExoPlayer> get() =
        if (isAActive) tracks.map { it.playerB } else tracks.map { it.playerA }

    // ── Load / Release ────────────────────────────────────────────────────────

    fun load(mode: TrackMode) {
        releaseCurrent()
        buildTrackPairs(mode, tracks)
        tracks.forEach { tp -> tp.playerA.prepare(); tp.playerB.prepare() }
        isAActive = true
        Log.d(TAG, "load() abgeschlossen: ${tracks.size} TrackPairs")
    }

    fun activatePreloaded(songId: Long): Boolean {
        if (songId != nextSongId || nextTracks.isEmpty()) return false
        releaseCurrent()
        nextTracks.forEach { t ->
            val pB = makeExoPlayer().also {
                it.setMediaItem(MediaItem.fromUri(Uri.parse(t.uri)))
                it.prepare()
            }
            tracks.add(TrackPair(t.name, t.uri, t.player, pB))
        }
        nextTracks.clear()
        nextSongId = -1L
        isPlaying = false; isAActive = true
        Log.d(TAG, "activatePreloaded() songId=$songId – Swap erfolgreich")
        return true
    }

    fun preload(songId: Long, mode: TrackMode) {
        if (songId == nextSongId) return
        releaseNext()
        buildTracks(mode, nextTracks)
        nextTracks.forEach { it.player.prepare() }
        nextSongId = songId
        Log.d(TAG, "preload() songId=$songId – ${nextTracks.size} Tracks gepuffert")
    }

    // ── Playback Controls ─────────────────────────────────────────────────────

    fun play()  { active.forEach { it.play() };                        isPlaying = true  }
    fun pause() { tracks.forEach { it.playerA.pause(); it.playerB.pause() }; isPlaying = false }
    fun stop()  { pause(); active.forEach { it.seekTo(0L) } }

    fun seekTo(posMs: Long) {
        active.forEach { it.seekTo(posMs) }
        if (loopActive) idle.forEach { it.seekTo(loopStartMs) }
    }

    fun setVolumeDb(trackName: String, db: Float) {
        val linear = 10f.pow(db.coerceIn(-3f, 3f) / 20f)
        tracks.find { it.name == trackName }?.let {
            it.playerA.volume = linear; it.playerB.volume = linear
        }
    }

    // ── A/B Loop — Ping-Pong Crossfade ───────────────────────────────────────

    fun preloadLoopStart(startMs: Long) {
        loopStartMs = startMs
        idle.forEach { it.seekTo(startMs) }
        Log.d(TAG, "preloadLoopStart() idle → ${startMs}ms")
    }

    fun activateLoop(bpmExact: Float, bars: Int = 8) {
        val bpm    = bpmExact.takeIf { it > 0f } ?: 120f
        val beatMs = 60_000.0 / bpm
        val pos    = positionMs.toDouble()
        val start  = (floor(pos / beatMs).toLong() * beatMs).roundToLong()
        activateLoopDirect(start, start + (beatMs * 4.0 * bars).roundToLong())
    }

    fun activateLoopDirect(startMs: Long, endMs: Long) {
        loopStartMs = startMs
        loopEndMs   = endMs
        originalDurationMs = active.firstOrNull()?.duration?.takeIf { it > 0 } ?: 0L
        loopActive = true
        active.forEach { it.seekTo(startMs); if (isPlaying) it.play() }
        idle.forEach { it.seekTo(startMs) }
        Log.d(TAG, "Loop aktiviert: start=${startMs}ms end=${endMs}ms")
    }

    fun shouldCrossfade(): Boolean = loopActive && isPlaying && positionMs >= loopEndMs

    // Aktualisiert Loop-Punkte OHNE den aktiven Player zu unterbrechen.
    // Nur der Idle-Player wird heimlich auf den neuen Start-Punkt gesetzt.
    // Edge-Case (pos >= newEnd): shouldCrossfade() feuert beim nächsten 5ms-Tick automatisch.
    fun updateLoopPoints(startMs: Long, endMs: Long) {
        loopStartMs = startMs
        loopEndMs   = endMs
        idle.forEach { it.seekTo(startMs) }
        Log.d(TAG, "updateLoopPoints: start=${startMs}ms end=${endMs}ms (pos=${positionMs}ms)")
    }

    suspend fun performCrossfade() {
        if (!loopActive) return
        idle.forEach { it.play() }
        delay(10L)
        if (!loopActive) { idle.forEach { it.pause() }; return }
        active.forEach { it.pause(); it.seekTo(loopStartMs) }
        isAActive = !isAActive
        Log.d(TAG, "Crossfade → isAActive=$isAActive")
    }

    fun deactivateLoop() {
        loopActive = false
        idle.forEach { it.pause(); it.seekTo(0L) }
        Log.d(TAG, "Loop deaktiviert bei ${positionMs}ms")
    }

    fun tickLoop(): Boolean = false

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun release() { releaseCurrent(); releaseNext() }

    private fun releaseCurrent() {
        tracks.forEach { it.playerA.release(); it.playerB.release() }
        tracks.clear(); isPlaying = false; loopActive = false; isAActive = true
    }

    private fun releaseNext() {
        nextTracks.forEach { it.player.release() }
        nextTracks.clear(); nextSongId = -1L
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private fun buildTrackPairs(mode: TrackMode, target: MutableList<TrackPair>) {
        fun add(name: String, path: String) { target.add(makeTrackPair(name, path)) }
        when (mode) {
            is TrackMode.Legacy     -> if (mode.filePath.isNotEmpty()) add("main", mode.filePath)
            is TrackMode.Multitrack -> {
                mode.drums?.let  { add("drums",  it) }
                mode.bass?.let   { add("bass",   it) }
                mode.keys?.let   { add("keys",   it) }
                mode.vocals?.let { add("vocals", it) }
                mode.click?.let  { add("click",  it) }
                mode.cue?.let    { add("cue",    it) }
            }
        }
    }

    private fun buildTracks(mode: TrackMode, target: MutableList<Track>) {
        fun add(name: String, path: String) { target.add(makeTrack(name, path)) }
        when (mode) {
            is TrackMode.Legacy     -> if (mode.filePath.isNotEmpty()) add("main", mode.filePath)
            is TrackMode.Multitrack -> {
                mode.drums?.let  { add("drums",  it) }
                mode.bass?.let   { add("bass",   it) }
                mode.keys?.let   { add("keys",   it) }
                mode.vocals?.let { add("vocals", it) }
                mode.click?.let  { add("click",  it) }
                mode.cue?.let    { add("cue",    it) }
            }
        }
    }

    private fun makeTrackPair(name: String, path: String): TrackPair {
        val uri = Uri.parse(path)
        val pA  = makeExoPlayer().also { it.setMediaItem(MediaItem.fromUri(uri)) }
        val pB  = makeExoPlayer().also { it.setMediaItem(MediaItem.fromUri(uri)) }
        return TrackPair(name, path, pA, pB)
    }

    private fun makeTrack(name: String, path: String): Track {
        val p = makeExoPlayer()
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.setMediaItem(MediaItem.fromUri(Uri.parse(path)))
        return Track(name, p, path)
    }

    private fun makeExoPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), false
            ).build().also {
                it.repeatMode = Player.REPEAT_MODE_ONE
                it.setSeekParameters(SeekParameters.EXACT)
            }
}
