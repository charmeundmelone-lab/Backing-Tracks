package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import de.livegigplayer.pro.data.TrackMode
import kotlin.math.pow

class AudioEngine(private val context: Context) {

    private data class Track(val name: String, val player: ExoPlayer)

    private val tracks = mutableListOf<Track>()
    var isPlaying = false; private set

    fun load(mode: TrackMode) {
        release()
        when (mode) {
            is TrackMode.Legacy -> if (mode.filePath.isNotEmpty()) addTrack("main", mode.filePath)
            is TrackMode.Multitrack -> {
                mode.drums?.let  { addTrack("drums",  it) }
                mode.bass?.let   { addTrack("bass",   it) }
                mode.keys?.let   { addTrack("keys",   it) }
                mode.vocals?.let { addTrack("vocals", it) }
                mode.click?.let  { addTrack("click",  it) }
                mode.cue?.let    { addTrack("cue",    it) }
            }
        }
        tracks.forEach { it.player.prepare() }
    }

    fun play()  { tracks.forEach { it.player.play()  }; isPlaying = true  }
    fun pause() { tracks.forEach { it.player.pause() }; isPlaying = false }

    fun seekTo(posMs: Long) { tracks.forEach { it.player.seekTo(posMs) } }

    fun setVolumeDb(trackName: String, db: Float) {
        val linear = 10f.pow(db.coerceIn(-3f, 3f) / 20f)
        tracks.find { it.name == trackName }?.player?.volume = linear
    }

    val positionMs: Long get() = tracks.firstOrNull()?.player?.currentPosition ?: 0L
    val durationMs: Long get() = tracks.firstOrNull()?.player?.duration?.takeIf { it > 0 } ?: 0L

    fun release() {
        tracks.forEach { it.player.release() }
        tracks.clear()
        isPlaying = false
    }

    private fun addTrack(name: String, path: String) {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), false
            ).build()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(path)))
        tracks.add(Track(name, player))
    }
}
