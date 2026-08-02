package de.livegigplayer.pro.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Liest aus jeder WAV-Datei der Bibliothek den Dateikopf und meldet Samplerate,
 * Bittiefe und Kanalzahl.
 *
 * Hintergrund: Für den geplanten USB-Multitrack-Weg werden die Samples roh mit dem
 * Takt des Pults (48 kHz) ausgegeben — 44,1-kHz-Material müsste umgerechnet werden.
 * Der User weiß nicht mehr, welche Songs er früher in welchem Format exportiert hat.
 * Statt zu raten wird gemessen: Die Samplerate steht im WAV-Header, das Auslesen
 * kostet pro Datei ein paar Dutzend Bytes.
 *
 * Der Bericht listet zusätzlich alle Stem-Dateinamen auf, die nicht dem erwarteten
 * Schema entsprechen (`drums/bass/keys/vocals/click/cue.wav`) — die Zuordnung
 * Datei → Rolle ist ein offener Punkt im Multitrack-Plan.
 */
object WavFormatCheck {

    private val KNOWN_STEMS = setOf(
        "drums.wav", "bass.wav", "keys.wav", "vocals.wav", "click.wav", "cue.wav"
    )

    /** Format einer einzelnen Datei; [error] gesetzt, wenn der Kopf unlesbar war. */
    data class FileFormat(
        val name: String,
        val sampleRate: Int = 0,
        val bits: Int = 0,
        val channels: Int = 0,
        val error: String? = null
    ) {
        val label: String get() = error ?: "$sampleRate Hz / ${bitsLabel} / ${channels}ch"
        val bitsLabel: String get() = if (bits > 0) "$bits bit" else "Bittiefe n/a"
        val isTarget: Boolean get() = error == null && sampleRate == 48000
    }

    /**
     * Prüft alle Songs und gibt einen fertigen, kopierbaren Textbericht zurück.
     * Läuft auf dem IO-Dispatcher; [onProgress] meldet (erledigt, gesamt).
     */
    suspend fun report(
        context: Context,
        songs: List<Song>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val perSong = LinkedHashMap<Song, List<FileFormat>>()
        songs.forEachIndexed { index, song ->
            val files = wavFilesOf(context, song)
            perSong[song] = if (files.isEmpty()) {
                // Keine Datei auffindbar — den rohen Pfad mitschreiben, sonst steht man
                // wie beim ersten Anlauf vor "0 Dateien" ohne jeden Anhaltspunkt.
                listOf(FileFormat(name = "", error = "keine Datei gefunden — Pfad: ${song.audioFilePath.take(90)}"))
            } else {
                files.map { (name, uri) ->
                    readFormat(context, uri)?.copy(name = name)
                        ?: FileFormat(name, error = "Format nicht lesbar")
                }
            }
            onProgress(index + 1, songs.size)
        }
        buildReport(perSong)
    }

    private fun buildReport(perSong: Map<Song, List<FileFormat>>): String = buildString {
        val allFiles = perSong.values.flatten()
        val counts = allFiles.filter { it.error == null }
            .groupingBy { "${it.sampleRate} Hz / ${it.bitsLabel}" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        appendLine("SONG-FORMATE — ${perSong.size} Songs, ${allFiles.size} WAV-Dateien")
        appendLine()
        appendLine("Verteilung:")
        if (counts.isEmpty()) appendLine("  keine lesbaren Dateien gefunden")
        counts.forEach { (format, n) -> appendLine("  $format: $n Dateien") }

        // Songs, in denen mindestens eine Datei nicht 48 kHz ist — das sind die,
        // die für den Multitrack-Weg umgerechnet oder neu exportiert werden müssten.
        val offSongs = perSong.filterValues { files ->
            files.any { it.error == null && it.sampleRate != 48000 }
        }
        appendLine()
        appendLine("NICHT 48 kHz (${offSongs.size} Songs):")
        if (offSongs.isEmpty()) {
            appendLine("  keine — alles bereits 48 kHz")
        } else {
            offSongs.entries.take(40).forEach { (song, files) ->
                appendLine("  • ${song.title}")
                files.filter { it.error == null && it.sampleRate != 48000 }
                    .forEach { appendLine("      ${it.name}: ${it.label}") }
            }
            if (offSongs.size > 40) appendLine("  … und ${offSongs.size - 40} weitere")
        }

        // Gemischte Formate INNERHALB eines Songs sind der gefährlichste Fall: die
        // Stems würden beim gemeinsamen Ausspielen auseinanderlaufen.
        val mixed = perSong.filterValues { files ->
            files.filter { it.error == null }.map { it.sampleRate }.distinct().size > 1
        }
        appendLine()
        appendLine("UNEINHEITLICH INNERHALB EINES SONGS (${mixed.size}):")
        if (mixed.isEmpty()) appendLine("  keine")
        mixed.keys.take(20).forEach { appendLine("  • ${it.title}") }

        // Dateinamen außerhalb des erwarteten Schemas — nötig für die spätere
        // Zuordnung Datei → Rolle → Pultkanal.
        val unknown = perSong.values.flatten()
            .map { it.name }
            .filter { it.lowercase() !in KNOWN_STEMS }
            .distinct()
        appendLine()
        appendLine("DATEINAMEN AUSSERHALB DES SCHEMAS (${unknown.size}):")
        if (unknown.isEmpty()) appendLine("  keine")
        unknown.take(40).forEach { appendLine("  • $it") }
        if (unknown.size > 40) appendLine("  … und ${unknown.size - 40} weitere")

        val problems = perSong.filterValues { files -> files.any { it.error != null } }
        if (problems.isNotEmpty()) {
            appendLine()
            appendLine("PROBLEME (${problems.size} Songs):")
            problems.entries.take(15).forEach { (song, files) ->
                files.filter { it.error != null }.forEach {
                    appendLine("  • ${song.title}: ${it.error}")
                }
            }
            if (problems.size > 15) appendLine("  … und ${problems.size - 15} weitere")
        }
    }

    /**
     * Alle WAV-Dateien eines Songs als (Dateiname, Uri) — bewusst ALLE, nicht nur die
     * sechs bekannten Rollen, damit auch abweichend benannte Stems im Bericht auftauchen.
     */
    private fun wavFilesOf(context: Context, song: Song): List<Pair<String, Uri>> {
        val path = song.audioFilePath.trim()
        if (path.isEmpty()) return emptyList()

        // Modus B (Einzeldatei je Song, Musik L / Click R): FolderImporter legt hier die
        // nackte Dokument-URI ab, KEIN "tree||ordner". Fehlte in der ersten Fassung —
        // dadurch fand die Prüfung bei einer reinen Modus-B-Bibliothek null Dateien.
        if ("||" !in path && path.startsWith("content://")) {
            val uri  = Uri.parse(path)
            val name = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }
                .getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "?"
            return listOf(name to uri)
        }

        if ("||" in path) {
            val delimIdx   = path.indexOf("||")
            val rootUri    = Uri.parse(path.substring(0, delimIdx))
            val folderName = path.substring(delimIdx + 2)
            val folder = runCatching {
                DocumentFile.fromTreeUri(context, rootUri)?.findFile(folderName)
            }.getOrNull() ?: return emptyList()
            return runCatching {
                folder.listFiles()
                    .filter { it.name?.endsWith(".wav", ignoreCase = true) == true }
                    .map { (it.name ?: "?") to it.uri }
            }.getOrDefault(emptyList())
        }

        val file = File(path)
        return when {
            file.isFile      -> listOf(file.name to Uri.fromFile(file))
            file.isDirectory -> file.listFiles { f -> f.extension.equals("wav", true) }
                ?.map { it.name to Uri.fromFile(it) } ?: emptyList()
            else             -> emptyList()
        }
    }

    /**
     * Format einer Datei: erst der WAV-Header (exakt, inkl. Bittiefe), sonst
     * MediaExtractor als Auffangnetz für alles andere (mp3, m4a, flac …).
     */
    private fun readFormat(context: Context, uri: Uri): FileFormat? =
        readWavHeader(context, uri) ?: readViaExtractor(context, uri)

    /** Samplerate/Kanäle über den Media-Stack — Bittiefe ist dort meist nicht bekannt. */
    private fun readViaExtractor(context: Context, uri: Uri): FileFormat? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            if (extractor.trackCount == 0) return@runCatching null
            val format = extractor.getTrackFormat(0)
            FileFormat(
                name       = "",
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                bits       = 0,
                channels   = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            )
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrNull()

    /** Liest den `fmt `-Chunk eines WAV-Headers. Null, wenn es keine gültige WAV ist. */
    private fun readWavHeader(context: Context, uri: Uri): FileFormat? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val riff = ByteArray(12)
            if (input.readFully(riff) < 12) return@use null
            if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return@use null

            val header = ByteArray(8)
            // Begrenzt, damit eine kaputte Datei keine Endlosschleife auslöst.
            repeat(64) {
                if (input.readFully(header) < 8) return@use null
                val id   = String(header, 0, 4)
                val size = le32(header, 4)
                if (id == "fmt ") {
                    val fmt = ByteArray(size.coerceIn(16, 64))
                    if (input.readFully(fmt) < 16) return@use null
                    return@use FileFormat(
                        name       = "",
                        channels   = le16(fmt, 2),
                        sampleRate = le32(fmt, 4),
                        bits       = le16(fmt, 14)
                    )
                }
                // Chunks sind auf gerade Länge gepolstert.
                input.skipFully(size.toLong() + (size % 2))
            }
            null
        }
    }.getOrNull()

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)

    /** InputStream.read() darf kurz lesen — hier wird bis zum Ziel nachgelesen. */
    private fun InputStream.readFully(into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = read(into, total, into.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    /** skip() darf ebenfalls kurz springen — hier wird bis zum Ziel weitergesprungen. */
    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val n = skip(left)
            if (n <= 0) {
                // Manche Streams springen nicht: dann lesend überbrücken.
                if (read() < 0) return
                left -= 1
            } else {
                left -= n
            }
        }
    }
}
