package de.minitraxx.app.data

import android.content.Context
import android.net.Uri
import de.minitraxx.app.audio.AudioImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Slot-Konstanten: 0..3 Instrumente, 4 Click/Cue. */
object Slots {
    const val INSTRUMENT_COUNT = 4
    const val CLICK = 4
    const val TOTAL = 5
}

class SongRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    val songDao get() = db.songDao()
    val setlistDao get() = db.setlistDao()

    fun songDir(songId: Long): File =
        File(File(context.filesDir, "songs"), songId.toString())

    fun stemFile(songId: Long, fileName: String): File = File(songDir(songId), fileName)

    suspend fun createSong(title: String, artist: String): Long =
        songDao.insert(SongEntity(title = title, artist = artist))

    suspend fun deleteSong(songId: Long) {
        songDao.delete(songId)
        withContext(Dispatchers.IO) { songDir(songId).deleteRecursively() }
    }

    /**
     * Importiert eine Quelldatei in den angegebenen Slot des Songs
     * (dekodiert ins kanonische Format). Ersetzt einen vorhandenen Stem im Slot.
     */
    suspend fun importStem(songId: Long, slot: Int, source: Uri, displayName: String) {
        val fileName = "stem$slot.wav"
        val dest = stemFile(songId, fileName)
        val result = withContext(Dispatchers.IO) {
            AudioImporter.import(context, source, dest)
        }
        val existing = songDao.getWithStems(songId)?.stems?.firstOrNull { it.slot == slot }
        if (existing != null) {
            songDao.updateStem(
                existing.copy(displayName = displayName, fileName = fileName, frames = result.frames)
            )
        } else {
            songDao.insertStem(
                StemEntity(
                    songId = songId,
                    slot = slot,
                    displayName = displayName,
                    fileName = fileName,
                    gainDb = 0f,
                    frames = result.frames,
                )
            )
        }
        refreshDuration(songId)
    }

    suspend fun removeStem(stem: StemEntity) {
        songDao.deleteStem(stem)
        withContext(Dispatchers.IO) { stemFile(stem.songId, stem.fileName).delete() }
        refreshDuration(stem.songId)
    }

    suspend fun setStemGain(stem: StemEntity, gainDb: Float) {
        songDao.updateStem(stem.copy(gainDb = gainDb))
    }

    private suspend fun refreshDuration(songId: Long) {
        songDao.updateDuration(songId, songDao.maxStemFrames(songId) ?: 0L)
    }

    companion object {
        @Volatile
        private var instance: SongRepository? = null

        fun get(context: Context): SongRepository =
            instance ?: synchronized(this) {
                instance ?: SongRepository(context.applicationContext).also { instance = it }
            }
    }
}
