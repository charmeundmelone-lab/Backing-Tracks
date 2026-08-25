package de.livegigplayer.pro.audio

import android.content.Context
import de.livegigplayer.pro.data.CrossRefMembership
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prüft, ob jeder Song im Archiv noch auf eine tatsächlich auffindbare Datei zeigt,
 * und ob gleichnamige Songs (mögliche Duplikate) existieren.
 *
 * Hintergrund: Der Import matcht Songs beim erneuten Einlesen exakt über
 * `audioFilePath` (SAF-Dokument-URI, siehe FolderImporter.upsert). Ändert sich diese
 * URI für eine Datei (z.B. weil sie extern neu geschrieben wurde, etwa durch das
 * 48-kHz-Konvertier-Skript), findet der Import beim nächsten Scan keinen Treffer mehr
 * und legt eine neue Song-Zeile mit neuer ID an — ohne jede Set-Zuordnung. Die alte
 * Zeile bleibt mit kaputtem Pfad in der DB stehen. Ergebnis: ein Song verschwindet aus
 * allen Sets, obwohl er im Archiv (scheinbar unverändert, oft sogar doppelt) weiter
 * auftaucht. Dieses Tool macht genau das sichtbar, bevor irgendetwas repariert wird.
 */
object SongLinkCheck {

    data class SongStatus(
        val song: Song,
        val fileFound: Boolean,
        val sets: List<CrossRefMembership>
    )

    /**
     * Prüft alle Songs und gibt einen fertigen, kopierbaren Textbericht zurück.
     * Läuft auf dem IO-Dispatcher; [onProgress] meldet (erledigt, gesamt).
     */
    suspend fun report(
        context: Context,
        songs: List<Song>,
        crossRefs: List<CrossRefMembership>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val bySong = crossRefs.groupBy { it.songId }
        val statuses = songs.mapIndexed { index, song ->
            val fileFound = WavFormatCheck.wavFilesOf(context, song).isNotEmpty()
            onProgress(index + 1, songs.size)
            SongStatus(song, fileFound, bySong[song.id].orEmpty())
        }
        buildReport(statuses)
    }

    private fun buildReport(statuses: List<SongStatus>): String = buildString {
        val broken = statuses.filter { !it.fileFound }
        val duplicateGroups = statuses
            .groupBy { it.song.title.trim().lowercase() to it.song.artist.trim().lowercase() }
            .filter { it.value.size > 1 }

        appendLine("SONG-VERKNÜPFUNGEN — ${statuses.size} Songs, " +
            "${broken.size} defekte Verknüpfungen, ${duplicateGroups.size} mögliche Duplikat-Gruppen")
        appendLine()

        appendLine("DEFEKTE VERKNÜPFUNG (Datei nicht gefunden, ${broken.size}):")
        if (broken.isEmpty()) {
            appendLine("  keine")
        } else {
            broken.take(40).forEach { st ->
                val setInfo = if (st.sets.isEmpty()) {
                    "in KEINEM Set mehr"
                } else {
                    "noch in ${st.sets.size} Set(s): " +
                        st.sets.joinToString(", ") { "${it.gigName}›${it.setName}" }
                }
                appendLine("  • ${st.song.title} (id=${st.song.id}) — $setInfo")
                appendLine("      Pfad: ${st.song.audioFilePath.take(90)}")
            }
            if (broken.size > 40) appendLine("  … und ${broken.size - 40} weitere")
        }

        appendLine()
        appendLine("MÖGLICHE DUPLIKATE (gleicher Titel + Interpret, ${duplicateGroups.size} Gruppen):")
        if (duplicateGroups.isEmpty()) {
            appendLine("  keine")
        } else {
            duplicateGroups.entries.take(30).forEach { (_, variants) ->
                appendLine("  • \"${variants.first().song.title}\"")
                variants.forEach { st ->
                    val state = if (st.fileFound) "ok" else "DEFEKT"
                    appendLine("      id=${st.song.id}: $state, ${st.sets.size} Set(s)")
                }
                val brokenOrphan = variants.any { !it.fileFound && it.sets.isEmpty() }
                val workingLinked = variants.any { it.fileFound && it.sets.isNotEmpty() }
                val classification = when {
                    brokenOrphan && workingLinked ->
                        "vermutlich altes verwaistes Duplikat (Reimport-Bug)"
                    variants.all { it.sets.isNotEmpty() } ->
                        "beide aktiv in Sets — evtl. bewusster Duplikat-Song, kein klarer Bug"
                    variants.all { it.sets.isEmpty() } ->
                        "beide ohne Set-Zuordnung"
                    else -> "uneindeutig — manuell prüfen"
                }
                appendLine("      → $classification")
            }
            if (duplicateGroups.size > 30) appendLine("  … und ${duplicateGroups.size - 30} weitere")
        }
    }
}
