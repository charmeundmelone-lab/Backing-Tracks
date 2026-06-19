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

    private data class InfoData(val bpm: Int? = null, val key: String = "", val capo: Int = 0)

    suspend fun import(
        context: Context,
        rootUri: Uri,
        dao: SongDao,
        onProgress: (String) -> Unit
    ) {
        Log.d(TAG, "import() gestartet: rootUri=$rootUri")

        try {
            context.contentResolver.takePersistableUriPermission(
                rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "takePersistableUriPermission OK")
        } catch (e: SecurityException) {
            Log.w(TAG, "takePersistableUriPermission fehlgeschlagen (evtl. bereits vorhanden): ${e.message}")
        }

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        if (rootDoc == null) {
            Log.e(TAG, "DocumentFile.fromTreeUri() lieferte null")
            return
        }

        val allFiles = try { rootDoc.listFiles() } catch (e: Exception) {
            Log.e(TAG, "listFiles() auf Root fehlgeschlagen", e); return
        }

        val folders = allFiles.filter { it.isDirectory }.sortedBy { it.name }
        Log.d(TAG, "Root hat ${allFiles.size} Einträge, davon ${folders.size} Unterordner")

        if (folders.isEmpty()) {
            Log.w(TAG, "Keine Unterordner gefunden – falscher Ordner? Pfad: $rootUri")
            return
        }

        for (folder in folders) {
            val name = folder.name
            if (name == null) { Log.w(TAG, "Ordner ohne Namen übersprungen"); continue }

            Log.d(TAG, "Verarbeite: '$name'")
            onProgress(name)

            val safPath = "${rootUri}||${name}"
            val info = parseInfoTxt(context, folder)
            val bpm = info.bpm ?: BPM_REGEX.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 120

            val wavs = try {
                folder.listFiles().filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
            } catch (e: Exception) {
                Log.e(TAG, "listFiles() in '$name' fehlgeschlagen", e); emptyList()
            }
            Log.d(TAG, "  '$name': ${wavs.size} WAVs, bpm=$bpm, key='${info.key}', capo=${info.capo}")

            val duration = if (wavs.isNotEmpty()) getDuration(context, wavs[0].uri) else "00:00"

            try {
                val existing = dao.findByPath(safPath)
                if (existing != null) {
                    dao.update(existing.copy(
                        title = name, bpm = bpm, duration = duration,
                        keySignature = info.key, capoPosition = info.capo
                    ))
                } else {
                    dao.insert(Song(
                        title = name, bpm = bpm, timeSignature = "4/4",
                        playlistId = 1, audioFilePath = safPath, duration = duration,
                        keySignature = info.key, capoPosition = info.capo
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB-Operation für '$name' fehlgeschlagen", e)
            }
        }
        Log.d(TAG, "import() abgeschlossen (${folders.size} Songs)")
    }

    private fun parseInfoTxt(context: Context, folder: DocumentFile): InfoData {
        val infoFile = try {
            folder.listFiles().find { it.name?.equals("info.txt", ignoreCase = true) == true }
        } catch (e: Exception) { null } ?: return InfoData()

        return try {
            val text = context.contentResolver.openInputStream(infoFile.uri)
                ?.bufferedReader()?.use { it.readText() } ?: return InfoData()

            var bpm: Int? = null
            var key = ""
            var capo = 0

            // Unterstützt "BPM=147\nKEY=Am\nCAPO=2" und "BPM=147, KEY=Am, CAPO=Kein"
            for (part in text.split(Regex("[,\n]")).map { it.trim() }.filter { it.isNotEmpty() }) {
                when {
                    part.startsWith("BPM=", ignoreCase = true) ->
                        bpm = part.substringAfter("=").trim().toIntOrNull()
                    part.startsWith("KEY=", ignoreCase = true) ->
                        key = part.substringAfter("=").trim()
                    part.startsWith("CAPO=", ignoreCase = true) -> {
                        val v = part.substringAfter("=").trim()
                        capo = if (v.equals("Kein", ignoreCase = true) || v == "0") 0
                               else v.toIntOrNull() ?: 0
                    }
                }
            }
            InfoData(bpm, key, capo)
        } catch (e: Exception) {
            Log.w(TAG, "info.txt parse error: ${e.message}")
            InfoData()
        }
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
