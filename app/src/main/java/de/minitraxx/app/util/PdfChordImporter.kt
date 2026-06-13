package de.minitraxx.app.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Wandelt ein PDF mit Akkorden über dem Text (Ultimate-Guitar-Stil) in
 * sauberes ChordPro um — und zwar koordinaten-genau.
 *
 * Der Kern: Jeder Buchstabe im PDF hat eine echte X-Position. Ein Akkord wird
 * deshalb NICHT über gezählte Leerzeichen platziert (die beim Text-Export aus
 * PDFs zusammenbrechen), sondern direkt vor das Zeichen der Textzeile gesetzt,
 * dessen X-Position unter dem Akkord liegt. Verrutschen ist damit per
 * Konstruktion ausgeschlossen.
 *
 * Funktioniert nur mit PDFs, die einen echten Textlayer haben (aus HTML/Print
 * erzeugt, wie bei Ultimate Guitar) — reine Scans/Bilder liefern keinen Text.
 */
object PdfChordImporter {

    class PdfImportException(message: String) : Exception(message)

    private data class Glyph(val c: Char, val x: Float, val y: Float, val w: Float, val h: Float)
    private data class Token(val text: String, val startX: Float, val endX: Float)

    /** Abschnitts-Schlüsselwörter (engl./dt.), die als {c: …}-Hinweis erscheinen. */
    private val SECTION_KEYS = Regex(
        "^(intro|verse|chorus|refrain|bridge|pre[- ]?chorus|outro|solo|interlude|" +
            "instrumental|hook|coda|tag|ending|strophe|vers|teil)\\b.*",
        RegexOption.IGNORE_CASE,
    )

    fun import(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw PdfImportException("PDF kann nicht geöffnet werden")
        val text = try {
            stream.use { input ->
                PDDocument.load(input).use { doc ->
                    if (doc.isEncrypted) {
                        throw PdfImportException("PDF ist passwortgeschützt und kann nicht gelesen werden")
                    }
                    val out = StringBuilder()
                    for (page in 1..doc.numberOfPages) {
                        val glyphs = collectGlyphs(doc, page)
                        if (glyphs.isNotEmpty()) appendPage(out, glyphs)
                    }
                    out.toString()
                }
            }
        } catch (e: PdfImportException) {
            throw e
        } catch (e: Exception) {
            throw PdfImportException("PDF konnte nicht gelesen werden")
        }
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw PdfImportException(
                "Im PDF wurde kein auslesbarer Text gefunden — vermutlich ein Scan oder Bild.",
            )
        }
        return trimmed
    }

    private fun collectGlyphs(doc: PDDocument, page: Int): List<Glyph> {
        val glyphs = ArrayList<Glyph>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(string: String, textPositions: List<TextPosition>) {
                for (tp in textPositions) {
                    val u = tp.unicode ?: continue
                    if (u.isEmpty()) continue
                    val ch = u[0]
                    if (ch == '\n' || ch == '\r' || ch == '\t') continue
                    glyphs.add(Glyph(ch, tp.xDirAdj, tp.yDirAdj, tp.widthDirAdj, tp.heightDir))
                }
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(doc)
        return glyphs
    }

    private fun appendPage(out: StringBuilder, glyphs: List<Glyph>) {
        val lines = groupLines(glyphs)
        if (lines.isEmpty()) return
        val medianH = median(glyphs.map { it.h }).coerceAtLeast(4f)

        var prevY: Float? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val y = line.first().y
            if (prevY != null && (y - prevY) > medianH * 1.8f) ensureBlank(out)
            prevY = y

            val tokens = tokenize(line)
            when {
                isSectionHeader(tokens) -> appendLine(out, "{section: ${sectionText(tokens)}}")
                isTabLine(tokens) -> { /* Guitar tab lines: skip silently */ }
                isChordTokens(tokens) -> {
                    val next = lines.getOrNull(i + 1)
                    val nextTokens = next?.let { tokenize(it) }
                    if (next != null && nextTokens != null &&
                        !isChordTokens(nextTokens) && !isSectionHeader(nextTokens) &&
                        !isTabLine(nextTokens)
                    ) {
                        appendLine(out, mergeChordLyric(tokens, next))
                        prevY = next.first().y
                        i += 2
                        continue
                    } else {
                        appendLine(out, chordOnly(tokens))
                    }
                }
                else -> appendLine(out, reconstructText(line))
            }
            i++
        }
        ensureBlank(out)
    }

    /** Gruppiert Glyphen mit ähnlicher Y-Position zu Zeilen (oben → unten). */
    private fun groupLines(glyphs: List<Glyph>): List<List<Glyph>> {
        if (glyphs.isEmpty()) return emptyList()
        val sorted = glyphs.sortedBy { it.y }
        val lines = ArrayList<MutableList<Glyph>>()
        var current = ArrayList<Glyph>()
        var currentY = Float.NaN
        for (g in sorted) {
            val tol = (if (g.h > 0f) g.h else 8f) * 0.5f
            if (current.isEmpty() || kotlin.math.abs(g.y - currentY) <= tol) {
                current.add(g)
                currentY = if (currentY.isNaN()) g.y
                else (currentY * (current.size - 1) + g.y) / current.size
            } else {
                lines.add(current)
                current = arrayListOf(g)
                currentY = g.y
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.map { it.sortedBy { gl -> gl.x } }
    }

    /** Zerlegt eine Zeile anhand der X-Lücken in Tokens (mit Start-X). */
    private fun tokenize(line: List<Glyph>): List<Token> {
        if (line.isEmpty()) return emptyList()
        val medianW = median(line.map { it.w }).coerceAtLeast(1f)
        val gapThreshold = medianW * 0.6f
        val tokens = ArrayList<Token>()
        val sb = StringBuilder()
        var startX = line.first().x
        var prevEnd = line.first().x
        for ((idx, g) in line.withIndex()) {
            if (idx == 0) {
                sb.append(g.c); startX = g.x; prevEnd = g.x + g.w; continue
            }
            if (g.x - prevEnd > gapThreshold) {
                tokens.add(Token(sb.toString(), startX, prevEnd))
                sb.setLength(0)
                sb.append(g.c)
                startX = g.x
            } else {
                sb.append(g.c)
            }
            prevEnd = g.x + g.w
        }
        if (sb.isNotEmpty()) tokens.add(Token(sb.toString(), startX, prevEnd))
        return tokens
    }

    private fun isChordTokens(tokens: List<Token>): Boolean {
        if (tokens.isEmpty()) return false
        var chords = 0
        for (t in tokens) {
            val clean = t.text.trim().removeSuffix(",")
            when {
                ChordPro.isChord(clean) -> chords++
                ChordPro.isFiller(clean) -> {}
                else -> return false
            }
        }
        return chords > 0
    }

    private fun isSectionHeader(tokens: List<Token>): Boolean {
        if (tokens.isEmpty()) return false
        val joined = tokens.joinToString(" ") { it.text }.trim()
        if (joined.startsWith("[") && joined.endsWith("]") && !isChordTokens(tokens)) return true
        val inner = joined.removeSurrounding("[", "]").removeSurrounding("(", ")").trim()
        return inner.length <= 24 && SECTION_KEYS.matches(inner)
    }

    /** Gitarren-Tab-Zeilen wie "e|---5-2---|" oder "B|-3-2-3---|" überspringen. */
    private fun isTabLine(tokens: List<Token>): Boolean {
        val joined = tokens.joinToString("") { it.text }
        return joined.matches(Regex("[eEBGDAd]\\|[-0-9|x/\\\\hpb~. ]+.*"))
    }

    private fun sectionText(tokens: List<Token>): String =
        tokens.joinToString(" ") { it.text }.trim()
            .removeSurrounding("[", "]").removeSurrounding("(", ")").trim()

    /**
     * Setzt jeden Akkord an die nächste Wortgrenze rechts von chord.startX.
     *
     * Kernproblem der alten Logik: "chords[ci].startX <= g.x" platziert Akkorde
     * zeichenweise und landet dadurch mitten in Wörtern (z.B. "t[G]he").
     * Neue Logik: Akkorde snappen immer an Wortanfänge — mit einem halben
     * Zeichen Lookahead nach links, damit ein UG-Akkord der 2–3 px rechts vom
     * Wortanfang steht trotzdem korrekt zugeordnet wird.
     */
    private fun mergeChordLyric(chords: List<Token>, lyric: List<Glyph>): String {
        val medianW = median(lyric.map { it.w }).coerceAtLeast(1f)
        val spaceGap = medianW * 0.6f
        val halfChar = medianW * 0.5f

        // Indizes der Wortanfänge (erster Buchstabe nach einer Lücke)
        val wordStarts = mutableListOf(0)
        for (i in 1 until lyric.size) {
            if (lyric[i].x - (lyric[i - 1].x + lyric[i - 1].w) > spaceGap) wordStarts.add(i)
        }

        // Jeden Akkord zum ersten Wortanfang snappen, dessen X >= chord.startX - halfChar.
        // halfChar-Lookahead: UG-Akkorde stehen oft minimal rechts vom Silbenanfang.
        val chordAt = mutableMapOf<Int, MutableList<String>>()
        for (chord in chords) {
            val snapX = chord.startX - halfChar
            val targetIdx = wordStarts.firstOrNull { lyric[it].x >= snapX } ?: lyric.size
            chordAt.getOrPut(targetIdx) { mutableListOf() }.add(chord.text.removeSuffix(","))
        }

        val sb = StringBuilder()
        var prevEnd: Float? = null
        for ((idx, g) in lyric.withIndex()) {
            if (prevEnd != null) {
                val gap = g.x - prevEnd
                if (gap > spaceGap) repeat((gap / medianW).toInt().coerceIn(1, 8)) { sb.append(' ') }
            }
            chordAt[idx]?.forEach { c -> sb.append('[').append(c).append(']') }
            sb.append(g.c)
            prevEnd = g.x + g.w
        }
        // Akkorde die hinter dem Zeilenende liegen (Akkord ohne Lyric darunter)
        chordAt[lyric.size]?.forEach { c -> sb.append('[').append(c).append(']') }
        return sb.toString()
    }

    private fun chordOnly(tokens: List<Token>): String = buildString {
        for ((idx, t) in tokens.withIndex()) {
            if (idx > 0) append("   ")
            val c = t.text.removeSuffix(",")
            if (ChordPro.isChord(c)) append('[').append(c).append(']') else append(c)
        }
    }

    private fun reconstructText(line: List<Glyph>): String {
        val medianW = median(line.map { it.w }).coerceAtLeast(1f)
        val spaceGap = medianW * 0.6f
        val sb = StringBuilder()
        var prevEnd: Float? = null
        for (g in line) {
            if (prevEnd != null) {
                val gap = g.x - prevEnd
                if (gap > spaceGap) repeat((gap / medianW).toInt().coerceIn(1, 8)) { sb.append(' ') }
            }
            sb.append(g.c)
            prevEnd = g.x + g.w
        }
        return sb.toString()
    }

    private fun appendLine(out: StringBuilder, s: String) {
        out.append(s.trimEnd()).append('\n')
    }

    private fun ensureBlank(out: StringBuilder) {
        if (out.isEmpty()) return
        if (out.length >= 2 && out[out.length - 1] == '\n' && out[out.length - 2] == '\n') return
        out.append('\n')
    }

    private fun median(xs: List<Float>): Float {
        if (xs.isEmpty()) return 0f
        val s = xs.sorted()
        return s[s.size / 2]
    }
}
