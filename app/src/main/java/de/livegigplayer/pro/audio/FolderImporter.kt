package de.livegigplayer.pro.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.SongDao

private const val TAG = "FolderImporter"

object FolderImporter {

    private val BPM_REGEX = Regex("""(\d+)\s*bpm""", RegexOption.IGNORE_CASE)

    suspend fun import(
        context: Context,
        rootUri: Uri,
        dao: SongDao,
        onProgress: (String) -> Unit
    ) {
        Log.d(TAG, "import() gestartet: rootUri=$rootUri")

        // Persistente Leseberechtigung sichern (kann SecurityException werfen → fangen)
        try {
            context.contentResolver.takePersistableUriPermission(
                rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "takePersistableUriPermission erfolgreich")
        } catch (e: SecurityException) {
            Log.w(TAG, "takePersistableUriPermission fehlgeschlagen (evtl. schon vorhanden): ${e.message}")
            // Weiter versuchen – Berechtigung ist evtl. bereits persistent
        }

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        if (rootDoc == null) {
            Log.e(TAG, "DocumentFile.fromTreeUri() lieferte null – URI ungültig oder keine Berechtigung")
            return
        }
        Log.d(TAG, "rootDoc OK: name=${rootDoc.name}, isDirectory=${rootDoc.isDirectory}")

        val allFiles = try {
            rootDoc.listFiles()
        } catch (e: Exception) {
            Log.e(TAG, "listFiles() auf Root fehlgeschlagen", e)
            return
        }

        Log.d(TAG, "Root enthält ${allFiles.size} Einträge")

        val folders = allFiles
            .filter { it.isDirectory }
            .sortedBy { it.name }

        Log.d(TAG, "Davon ${folders.size} Unterordner (= Songs)")

        if (folders.isEmpty()) {
            Log.w(TAG, "Keine Unterordner gefunden! Falscher Ordner gewählt? Pfad: $rootUri")
            return
        }

        for (folder in folders) {
            val name = folder.name
            if (name == null) {
                Log.w(TAG, "Ordner ohne Namen übersprungen")
                continue
            }

            Log.d(TAG, "Verarbeite Song-Ordner: '$name'")
            onProgress(name)

            val safPath = "${rootUri}||${name}"
            val bpm = BPM_REGEX.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 120

            val wavs = try {
                folder.listFiles()
                    .filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
            } catch (e: Exception) {
                Log.e(TAG, "listFiles() im Ordner '$name' fehlgeschlagen", e)
                emptyList()
            }

            Log.d(TAG, "  '$name': ${wavs.size} WAV-Dateien, bpm=$bpm")
            wavs.forEach { Log.d(TAG, "    WAV: ${it.name}") }

            val duration = if (wavs.isNotEmpty()) getDuration(context, wavs[0].uri) else "00:00"

            val existing = try { dao.findByPath(safPath) } catch (e: Exception) {
                Log.e(TAG, "DB-Abfrage findByPath fehlgeschlagen", e); null
            }

            try {
                if (existing != null) {
                    Log.d(TAG, "  Update existing song id=${existing.id}")
                    dao.update(existing.copy(title = name, bpm = bpm, duration = duration))
                } else {
                    Log.d(TAG, "  Insert neuer Song '$name'")
                    dao.insert(
                        Song(
                            title         = name,
                            bpm           = bpm,
                            timeSignature = "4/4",
                            playlistId    = 1,
                            audioFilePath = safPath,
                            duration      = duration
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB-Insert/Update für '$name' fehlgeschlagen", e)
            }
        }

        Log.d(TAG, "import() vollständig abgeschlossen (${folders.size} Songs verarbeitet)")
    }

    private fun getDuration(context: Context, uri: Uri): String {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val s = ms / 1000
            "%02d:%02d".format(s / 60, s % 60)
        } catch (e: Exception) {
            Log.w(TAG, "getDuration fehlgeschlagen für $uri: ${e.message}")
            "00:00"
        } finally {
            mmr.release()
        }
    }
}
