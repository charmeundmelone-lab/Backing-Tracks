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

    suspend fun import(context: Context, rootUri: Uri, dao: SongDao, onProgress: (String) -> Unit) {
        Log.d(TAG, "import() rootUri=$rootUri")
        try {
            context.contentResolver.takePersistableUriPermission(rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) { Log.w(TAG, "takePersistableUriPermission: ${e.message}") }

        val root = DocumentFile.fromTreeUri(context, rootUri) ?: run { Log.e(TAG, "root null"); return }
        val all = try { root.listFiles() } catch (e: Exception) { Log.e(TAG, "listFiles failed", e); return }

        val folders = all.filter { it.isDirectory }.sortedBy { it.name }
        val wavs    = all.filter { it.name?.endsWith(".wav", ignoreCase = true) == true }.sortedBy { it.name }

        Log.d(TAG, "${folders.size} Unterordner (Modus A), ${wavs.size} WAV-Dateien (Modus B)")

        // Modus A: Unterordner = Multi-Stem-Songs
        for (folder in folders) {
            val rawName = folder.name ?: continue
            onProgress(rawName)
            val (artist, title) = parseArtistTitle(rawName)
            val safPath = "${rootUri}||${rawName}"
            val children = try { folder.listFiles() } catch (e: Exception) { emptyList() }
            val allWavs = children.filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
            val clickFile = allWavs.find { it.name?.contains("click", ignoreCase = true) == true }
            val bpmFromName = bpmFromName(rawName)
            val bpmExact = if (clickFile != null) BpmAnalyzer.analyze(context, clickFile.uri) else 0f
            val bpm = bpmFromName ?: if (bpmExact > 0f) bpmExact.toInt() else 120
            val duration = allWavs.firstOrNull()?.let { getDuration(context, it.uri) } ?: "00:00"
            Log.d(TAG, "ModeA '$rawName': bpmExact=$bpmExact bpm=$bpm")
            upsert(dao, safPath, title, artist, bpm, bpmExact, duration)
        }

        // Modus B: Einzel-WAV = Stereo-Song (L=Musik, R=Klick)
        for (wav in wavs) {
            val rawName = wav.name?.removeSuffix(".wav")?.removeSuffix(".WAV") ?: continue
            onProgress(rawName)
            val (artist, title) = parseArtistTitle(rawName)
            val safPath = wav.uri.toString()
            val bpmExact = BpmAnalyzer.analyze(context, wav.uri, rightChannelOnly = true)
            val bpm = bpmFromName(rawName) ?: if (bpmExact > 0f) bpmExact.toInt() else 120
            val duration = getDuration(context, wav.uri)
            Log.d(TAG, "ModeB '$rawName': bpmExact=$bpmExact bpm=$bpm")
            upsert(dao, safPath, title, artist, bpm, bpmExact, duration)
        }
    }

    private suspend fun upsert(dao: SongDao, path: String, title: String, artist: String,
                               bpm: Int, bpmExact: Float, duration: String) {
        try {
            val existing = dao.findByPath(path)
            if (existing != null) {
                dao.update(existing.copy(title = title, artist = artist, bpm = bpm,
                    bpmExact = bpmExact, duration = duration))
            } else {
                dao.insert(Song(title = title, artist = artist, bpm = bpm, bpmExact = bpmExact,
                    timeSignature = "4/4", playlistId = 1, audioFilePath = path, duration = duration))
            }
        } catch (e: Exception) { Log.e(TAG, "DB upsert failed for '$title'", e) }
    }

    private fun parseArtistTitle(name: String): Pair<String, String> {
        val clean = name.replace(Regex("""[\s\-]*\d+\s*bpm""", RegexOption.IGNORE_CASE), "").trim()
        val idx = clean.indexOf(" - ")
        return if (idx > 0) Pair(clean.substring(0, idx).trim(), clean.substring(idx + 3).trim())
               else Pair("", clean)
    }

    private fun bpmFromName(name: String): Int? =
        Regex("""(\d+)\s*bpm""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull()

    private fun getDuration(context: Context, uri: Uri): String {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val s = ms / 1000
            "%02d:%02d".format(s / 60, s % 60)
        } catch (e: Exception) { "00:00" } finally { mmr.release() }
    }
}
