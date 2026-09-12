package de.livegigplayer.pro.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Show-Automatik (PLAN-show-automatik.md, Teil 1): Vorlauf-/Nachlauf-Sprachnotizen.
 * Aufnahme läuft App-intern (kein SAF-Picker) im internen Speicher der App — die
 * Datei ist für den User im normalen Dateimanager nicht sichtbar und geht bei einer
 * Deinstallation mit verloren, wie ein Spielstand. Format: AAC/.m4a via MediaRecorder
 * (Android-Standard für Sprachaufnahmen, kein NDK nötig).
 */
object VoiceNoteRecorder {
    private const val TAG = "VoiceNoteRecorder"

    enum class Slot(val suffix: String) { INTRO("intro"), OUTRO("outro") }

    fun fileFor(context: Context, songId: Long, slot: Slot): File =
        File(context.filesDir, "${songId}_${slot.suffix}.m4a")

    /** Startet die Aufnahme in [outputFile]. Gibt null zurück, falls Start fehlschlägt. */
    fun start(context: Context, outputFile: File): MediaRecorder? {
        val recorder = try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder-Instanziierung fehlgeschlagen", e)
            return null
        }
        return try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Ohne diese drei Zeilen fällt MediaRecorder je Gerät auf sehr niedrige
                // Default-Werte zurück ("klingt wie Telefon") — explizit Musik-/Sprach-
                // qualität statt Telefonie-Preset erzwingen.
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aufnahme-Start fehlgeschlagen für ${outputFile.name}", e)
            runCatching { recorder.release() }
            null
        }
    }

    /** Stoppt die Aufnahme, gibt die gemessene Dauer der Datei zurück (0 bei Fehler). */
    fun stop(recorder: MediaRecorder, outputFile: File): Long {
        return try {
            recorder.stop()
            recorder.release()
            durationOf(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Aufnahme-Stop fehlgeschlagen für ${outputFile.name}", e)
            runCatching { recorder.release() }
            0L
        }
    }

    fun durationOf(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Dauer-Messung fehlgeschlagen für ${file.name}", e)
            0L
        } finally {
            retriever.release()
        }
    }

    fun delete(file: File) {
        if (file.exists()) file.delete()
    }
}
