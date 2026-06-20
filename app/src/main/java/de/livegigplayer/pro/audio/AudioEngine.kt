package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.livegigplayer.pro.data.TrackMode
import kotlin.math.pow

private const val TAG = "AudioEngine"

class AudioEngine(private val context: Context) {

    private data class Track(val name: String, val player: ExoPlayer)

    private val tracks     = mutableListOf<Track>()
    private val nextTracks = mutableListOf<Track>()
    private var nextSongId = -1L

    var isPlaying = false; private set

    // ── Aktueller Song ──────────────────────────────────────────────────────

    fun load(mode: TrackMode) {
        releaseCurrent()
        buildTracks(mode, tracks)
        tracks.forEach { it.player.prepare() }
        Log.d(TAG, "load() abgeschlossen: ${tracks.size} Tracks")
    }

    fun activatePreloaded(songId: Long): Boolean {
        if (songId != nextSongId || nextTracks.isEmpty()) return false
        releaseCurrent()
        tracks.addAll(nextTracks)
        nextTracks.clear()
        nextSongId = -1L
        isPlaying = false
        Log.d(TAG, "activatePreloaded() songId=$songId – Swap erfolgreich")
        return true
    }

    fun play()  { tracks.forEach { it.player.play()  }; isPlaying = true  }
    fun pause() { tracks.forEach { it.player.pause() }; isPlaying = false }
    fun stop()  { pause(); seekTo(0L) }

    fun seekTo(posMs: Long) { tracks.forEach { it.player.seekTo(posMs) } }

    fun setVolumeDb(trackName: String, db: Float) {
        val linear = 10f.pow(db.coerceIn(-3f, 3f) / 20f)
        tracks.find { it.name == trackName }?.player?.volume = linear
    }

    val positionMs: Long get() = tracks.firstOrNull()?.player?.currentPosition ?: 0L
    val durationMs: Long get() = tracks.firstOrNull()?.player?.duration?.takeIf { it > 0 } ?: 0L

    // ── A/B-Loop (taktsynchron) ───────────────────────────────────────────────

    var loopActive  = false; private set
    private var loopStartMs = 0L
    private var loopEndMs   = 0L

    fun activateLoop(bpmExact: Float, bars: Int = 8) {
        val bpm = bpmExact.takeIf { it > 0f } ?: 120f
        val beatMs  = (60_000.0 / bpm).toLong()
        val barMs   = beatMs * 4                          // 4/4 Takt
        val pos     = positionMs
        loopStartMs = (pos / beatMs) * beatMs             // snap-to-beat (floor)
        loopEndMs   = loopStartMs + barMs * bars
        loopActive  = true
        Log.d(TAG, "Loop aktiviert: start=${loopStartMs}ms end=${loopEndMs}ms (${bars} Takte @ ${bpm}BPM)")
    }

    fun deactivateLoop() {
        loopActive = false
        Log.d(TAG, "Loop deaktiviert")
    }

    // Wird vom ViewModel-Polling-Loop alle 200ms aufgerufen.
    // Gibt true zurück wenn ein Loop-Sprung ausgeführt wurde.
    fun tickLoop(): Boolean {
        if (!loopActive || loopEndMs <= loopStartMs) return false
        if (positionMs >= loopEndMs) {
            seekTo(loopStartMs)
            Log.d(TAG, "Loop-Sprung: zurück zu ${loopStartMs}ms")
            return true
        }
        return false
    }

    // ── Nächster Song vorbereiten ────────────────────────────────────────────

    fun preload(songId: Long, mode: TrackMode) {
        if (songId == nextSongId) return  // Bereits vorgeladen
        releaseNext()
        buildTracks(mode, nextTracks)
        nextTracks.forEach { it.player.prepare() }
        nextSongId = songId
        Log.d(TAG, "preload() songId=$songId – ${nextTracks.size} Tracks gepuffert")
    }

    // ── Aufräumen ────────────────────────────────────────────────────────────

    fun release() {
        releaseCurrent()
        releaseNext()
    }

    private fun releaseCurrent() {
        tracks.forEach { it.player.release() }
        tracks.clear()
        isPlaying = false
    }

    private fun releaseNext() {
        nextTracks.forEach { it.player.release() }
        nextTracks.clear()
        nextSongId = -1L
    }

    // ── Interne Hilfsfunktionen ───────────────────────────────────────────────

    private fun buildTracks(mode: TrackMode, target: MutableList<Track>) {
        when (mode) {
            is TrackMode.Legacy ->
                if (mode.filePath.isNotEmpty()) target.add(makeTrack("main", mode.filePath))
            is TrackMode.Multitrack -> {
                mode.drums?.let  { target.add(makeTrack("drums",  it)) }
                mode.bass?.let   { target.add(makeTrack("bass",   it)) }
                mode.keys?.let   { target.add(makeTrack("keys",   it)) }
                mode.vocals?.let { target.add(makeTrack("vocals", it)) }
                mode.click?.let  { target.add(makeTrack("click",  it)) }
                mode.cue?.let    { target.add(makeTrack("cue",    it)) }
            }
        }
    }

    private fun makeTrack(name: String, path: String): Track {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), false
            ).build()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.fromUri(Uri.parse(path)))
        return Track(name, player)
    }
}
