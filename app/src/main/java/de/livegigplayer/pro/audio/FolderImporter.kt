package de.livegigplayer.pro.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.SongDao

object FolderImporter {

    private val BPM_REGEX = Regex("""(\d+)\s*bpm""", RegexOption.IGNORE_CASE)

    /**
     * Scannt alle Unterordner von rootUri.
     * Jeder Unterordner = ein Song (Ordnername = Titel).
     * audioFilePath-Format: "{rootUri}||{ordnerName}"
     */
    suspend fun import(
        context: Context,
        rootUri: Uri,
        dao: SongDao,
        onProgress: (String) -> Unit
    ) {
        // Persistente Berechtigung sichern
        context.contentResolver.takePersistableUriPermission(
            rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return

        rootDoc.listFiles()
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .forEach { folder ->
                val name = folder.name ?: return@forEach
                onProgress(name)

                val safPath = "${rootUri}||${name}"
                val bpm = BPM_REGEX.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 120

                // Dauer vom ersten WAV ermitteln
                val wavs = folder.listFiles()
                    .filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
                val duration = if (wavs.isNotEmpty()) getDuration(context, wavs[0].uri) else "00:00"

                // Bestehenden Song aktualisieren oder neu anlegen (Mixer-Einstellungen bleiben erhalten)
                val existing = dao.findByPath(safPath)
                if (existing != null) {
                    dao.update(existing.copy(title = name, bpm = bpm, duration = duration))
                } else {
                    dao.insert(Song(
                        title         = name,
                        bpm           = bpm,
                        timeSignature = "4/4",
                        playlistId    = 1,
                        audioFilePath = safPath,
                        duration      = duration
                    ))
                }
            }
    }

    private fun getDuration(context: Context, uri: Uri): String {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val s = ms / 1000
            "%02d:%02d".format(s / 60, s % 60)
        } catch (_: Exception) {
            "00:00"
        } finally {
            mmr.release()
        }
    }
}
