package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Phase 1: extrahiert den Textlayer eines PDF und entfernt reine Akkordzeilen.
 * Umbrüche bleiben 1:1 wie im PDF (keine eigene Umbruch-Regel). Kein OCR.
 */
object PdfLyricsImporter {

    // Ein Token gilt als Akkord: C, Am, G7, Dsus4, F#m, Cmaj7, C/E …
    private val CHORD_REGEX =
        Regex("^[A-G](#|b)?(m|maj|min|sus|dim|aug|add)?\\d{0,2}(/[A-G](#|b)?)?$")

    // Reine Takt-/Trenn-Tokens zählen ebenfalls als „Akkord-Token".
    private val BAR_TOKENS = setOf("|", ":", "/", "||", "|:", ":|")

    // [Verse], [Chorus] … bleiben IMMER erhalten (Songstruktur, kein Akkord).
    private val SECTION_REGEX = Regex("^\\[.*\\]$")

    /** Liest den PDF-Textlayer und gibt den gefilterten Lyrics-Text zurück. */
    fun importLyricsFromPdf(context: Context, uri: Uri): String {
        val rawText = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                PDFTextStripper().getText(doc)
            }
        } ?: return ""
        return filterChordLines(rawText)
    }

    /** Entfernt reine Akkordzeilen, behält Text-, Leer- und [Section]-Zeilen. */
    fun filterChordLines(text: String): String =
        text.lineSequence()
            .filterNot { isChordLine(it) }
            .joinToString("\n")
            .trim()

    private fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false                // Leerzeilen behalten
        if (SECTION_REGEX.matches(trimmed)) return false    // [Section] behalten
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        val chordCount = tokens.count { it in BAR_TOKENS || CHORD_REGEX.matches(it) }
        return chordCount.toDouble() / tokens.size >= 0.8   // ≥80 % → Akkordzeile
    }
}
