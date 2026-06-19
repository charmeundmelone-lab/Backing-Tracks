package de.livegigplayer.pro.audio

import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode
import java.io.File

object SongScanner {
    fun scan(song: Song): TrackMode {
        val path = song.audioFilePath.trim()
        if (path.isEmpty()) return TrackMode.Legacy("")

        val file = File(path)
        if (file.isFile) return TrackMode.Legacy(file.absolutePath)

        if (file.isDirectory) {
            val wavs = file.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            if (wavs != null && wavs.size == 1) return TrackMode.Legacy(wavs[0].absolutePath)

            fun track(name: String): String? = File(file, name).takeIf { it.exists() }?.absolutePath
            return TrackMode.Multitrack(
                drums  = track("drums.wav"),
                bass   = track("bass.wav"),
                keys   = track("keys.wav"),
                vocals = track("vocals.wav"),
                click  = track("click.wav"),
                cue    = track("cue.wav")
            )
        }
        return TrackMode.Legacy(path)
    }
}
