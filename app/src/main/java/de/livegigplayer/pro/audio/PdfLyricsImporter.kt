package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

    // ---- „Zuletzt genutzter Ordner" merken --------------------------------

    private const val PREFS = "pdf_import"
    private const val KEY_LAST_FOLDER = "last_folder_uri"

    /** Zuletzt gemerkten Ordner laden (als Start-Hinweis für den Datei-Picker). */
    fun loadLastFolder(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FOLDER, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /**
     * Merkt sich den Ordner der gerade gewählten PDF-Datei und gibt ihn zurück.
     * Leitet die Parent-Ordner-URI ab (funktioniert für den System-Dateispeicher);
     * gelingt das nicht, wird die Datei-URI selbst gemerkt — der Picker öffnet
     * dann ebenfalls den enthaltenden Ordner.
     */
    fun rememberFolderOf(context: Context, fileUri: Uri): Uri {
        val folder = parentFolderOf(fileUri) ?: fileUri
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_FOLDER, folder.toString()).apply()
        return folder
    }

    private fun parentFolderOf(fileUri: Uri): Uri? = runCatching {
        val docId = DocumentsContract.getDocumentId(fileUri)   // z.B. "primary:Documents/BT/song.pdf"
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val scheme = docId.substring(0, colon)
        val path = docId.substring(colon + 1)
        val slash = path.lastIndexOf('/')
        if (slash < 0) return null
        val parentDocId = "$scheme:${path.substring(0, slash)}"
        DocumentsContract.buildDocumentUri(fileUri.authority, parentDocId)
    }.getOrNull()

    // -----------------------------------------------------------------------

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
