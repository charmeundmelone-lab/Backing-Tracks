package de.livegigplayer.pro.audio

import de.livegigplayer.pro.data.GigDao
import de.livegigplayer.pro.data.SetDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Show-Automatik-Diagnose (PLAN-show-automatik.md, Teil 3 Punkt 2): findet
 * Auto-Advance-Übergänge (endAction=AUTOPLAY), die weder eine Nachlauf-Notiz am
 * vorherigen noch eine Vorlauf-Notiz oder eine manuelle Fallback-Pause am nächsten
 * Song haben. Genau diese Übergänge springen beim Gig kommentarlos/instant in den
 * nächsten Song — dieses Tool macht solche Lücken sichtbar, BEVOR der Gig läuft.
 */
object AutomatikCheck {

    data class Gap(val gigName: String, val setName: String, val fromSong: String, val toSong: String)

    private const val AUTOPLAY = 2

    suspend fun report(gigDao: GigDao, setDao: SetDao): String = withContext(Dispatchers.IO) {
        val gaps = mutableListOf<Gap>()
        gigDao.getAllGigs().first().forEach { gig ->
            setDao.getSetsForGigOnce(gig.gigId).forEach { set ->
                val songs = setDao.getSongsInSetOnce(set.setId)
                for (i in 0 until songs.size - 1) {
                    val current = songs[i]
                    if (current.endAction != AUTOPLAY) continue
                    val next = songs[i + 1]
                    val hasOutro = current.song.outroNoteFilePath.isNotBlank()
                    val hasIntro = next.song.introNoteFilePath.isNotBlank()
                    val hasPause = next.song.manualPauseSeconds > 0
                    if (!hasOutro && !hasIntro && !hasPause) {
                        gaps += Gap(gig.name, set.name, current.song.title, next.song.title)
                    }
                }
            }
        }
        buildReport(gaps)
    }

    private fun buildReport(gaps: List<Gap>): String = buildString {
        appendLine("AUTOMATIK-CHECK — ${gaps.size} Übergänge ohne Ansage/Pause")
        appendLine()
        if (gaps.isEmpty()) {
            appendLine("Keine Lücken gefunden — jeder Auto-Advance-Übergang hat eine " +
                "Nachlauf-/Vorlauf-Notiz oder eine manuelle Pause.")
        } else {
            gaps.groupBy { it.gigName to it.setName }.forEach { (gigSet, entries) ->
                val (gigName, setName) = gigSet
                appendLine("$gigName › $setName (${entries.size}):")
                entries.forEach { g ->
                    appendLine("  • „${g.fromSong}“ → „${g.toSong}“ — kein Nachlauf, kein Vorlauf, keine Pause")
                }
                appendLine()
            }
        }
    }
}
