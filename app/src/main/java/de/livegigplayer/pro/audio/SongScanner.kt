package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode
import java.io.File

object SongScanner {

    /**
     * SAF-Pfadformat: "{treeUri}||{ordnerName}"
     * Klassisch:      absoluter Dateisystem-Pfad
     */
    fun scan(song: Song, context: Context? = null): TrackMode {
        val path = song.audioFilePath.trim()
        if (path.isEmpty()) return TrackMode.Legacy("")

        if ("||" in path && context != null) return scanSaf(context, path)

        val file = File(path)
        if (file.isFile) return TrackMode.Legacy(file.absolutePath)
        if (file.isDirectory) {
            val wavs = file.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            if (!wavs.isNullOrEmpty() && wavs.size == 1) return TrackMode.Legacy(wavs[0].absolutePath)
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

    private fun scanSaf(context: Context, path: String): TrackMode {
        val delimIdx = path.indexOf("||")
        val rootUri  = Uri.parse(path.substring(0, delimIdx))
        val folderName = path.substring(delimIdx + 2)
        val folderDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?.findFile(folderName) ?: return TrackMode.Legacy("")
        val wavs = folderDoc.listFiles()
            .filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
        if (wavs.isEmpty()) return TrackMode.Legacy("")
        if (wavs.size == 1) return TrackMode.Legacy(wavs[0].uri.toString())
        fun find(n: String) = wavs.find { it.name?.equals(n, ignoreCase = true) == true }?.uri?.toString()
        return TrackMode.Multitrack(
            drums  = find("drums.wav"),
            bass   = find("bass.wav"),
            keys   = find("keys.wav"),
            vocals = find("vocals.wav"),
            click  = find("click.wav"),
            cue    = find("cue.wav")
        )
    }
}
