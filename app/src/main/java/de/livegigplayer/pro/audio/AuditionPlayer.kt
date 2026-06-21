package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AuditionPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().also {
        it.repeatMode = Player.REPEAT_MODE_ALL
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun startLoop(uri: String, startMs: Long, endMs: Long) {
        if (uri.isEmpty() || endMs <= startMs) { stop(); return }
        val clipped = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        player.setMediaItems(List(64) { clipped })
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        player.release()
    }
}
