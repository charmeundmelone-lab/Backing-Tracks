package de.minitraxx.app.util

/**
 * Parser für Songtexte mit Akkorden. Versteht zwei Formate und erkennt sie
 * automatisch:
 *  - ChordPro: [Akkord]-Marker im Text, Direktiven in {}
 *  - Ultimate-Guitar-Stil: eigene Akkordzeile über der Textzeile,
 *    Abschnitts-Marker wie [Verse 1] allein auf einer Zeile
 */
object ChordPro {

    enum class Kind { LYRIC, COMMENT, SECTION, EMPTY }

    data class Line(val kind: Kind, val chords: String?, val text: String)

    /** Ein Akkord-mit-Silbe-Baustein für den umbruchfähigen Renderer. */
    data class Piece(val chord: String?, val text: String)

    fun isChord(token: String): Boolean = CHORD.matches(token.removeSurrounding("[", "]"))

    fun isFiller(token: String): Boolean = token in FILLER

    /**
     * Zerlegt eine LYRIC-Zeile in „Wörter", die je für sich umbrechen dürfen,
     * ohne dass ein Akkord von seiner Silbe getrennt wird. Jedes Wort ist eine
     * Folge von [Piece]s (Akkord über Teil-Silbe). Akkorde, die über einer Lücke
     * stehen (z. B. Instrumentalzeilen), werden zu eigenständigen Akkord-Wörtern.
     */
    fun toWords(line: Line): List<List<Piece>> {
        val text = line.text
        val chords = line.chords

        val markers = ArrayList<Pair<Int, String>>()
        if (chords != null) {
            var i = 0
            while (i < chords.length) {
                if (chords[i] != ' ') {
                    val start = i
                    while (i < chords.length && chords[i] != ' ') i++
                    markers.add(start to chords.substring(start, i))
                } else i++
            }
        }

        data class W(val start: Int, val end: Int)
        val textWords = ArrayList<W>()
        run {
            var i = 0
            while (i < text.length) {
                if (text[i] != ' ') {
                    val start = i
                    while (i < text.length && text[i] != ' ') i++
                    textWords.add(W(start, i))
                } else i++
            }
        }

        val used = BooleanArray(markers.size)
        val result = ArrayList<Pair<Int, MutableList<Piece>>>()
        for (w in textWords) {
            val inWord = ArrayList<Pair<Int, String>>()
            for ((mi, m) in markers.withIndex()) {
                if (!used[mi] && m.first >= w.start && m.first < w.end) {
                    inWord.add(m); used[mi] = true
                }
            }
            val pieces = ArrayList<Piece>()
            if (inWord.isEmpty()) {
                pieces.add(Piece(null, text.substring(w.start, w.end)))
            } else {
                inWord.sortBy { it.first }
                if (inWord.first().first > w.start) {
                    pieces.add(Piece(null, text.substring(w.start, inWord.first().first)))
                }
                for (k in inWord.indices) {
                    val from = inWord[k].first
                    val to = if (k + 1 < inWord.size) inWord[k + 1].first else w.end
                    pieces.add(Piece(inWord[k].second, text.substring(from, to)))
                }
            }
            result.add(w.start to pieces)
        }
        // Gap-Akkorde: Akkord über Leerraum oder vor eingerücktem Wort dem
        // nächstgelegenen Wort (nach absolutem Abstand) zuordnen — deutlich
        // robuster als "nächstes Wort nach Position", weil es auch mit
        // Einrückungen und unterschiedlichen Spaltenversätzen zwischen Akkord-
        // und Textzeile umgeht.
        val standalone = ArrayList<Pair<Int, String>>()
        for ((mi, m) in markers.withIndex()) {
            if (used[mi]) continue
            val nearestWord = result.minByOrNull { kotlin.math.abs(it.first - m.first) }
            val firstPiece = nearestWord?.second?.firstOrNull()
            if (nearestWord != null && firstPiece != null && firstPiece.chord == null) {
                nearestWord.second[0] = Piece(m.second, firstPiece.text)
                used[mi] = true
            } else {
                standalone.add(m)
            }
        }
        val out = ArrayList<Pair<Int, List<Piece>>>()
        for (wp in result) out.add(wp.first to wp.second)
        for (m in standalone) out.add(m.first to listOf(Piece(m.second, "")))
        out.sortBy { it.first }
        return out.map { it.second }
    }

    /** Erkennt einen einzelnen Akkordnamen wie Em7, Dsus4, Cadd9, D/F#. */
    private val CHORD = Regex(
        "^\\(?[A-G][#b]?(?:maj|min|mi|m|M|sus|dim|aug|add|[0-9]|[#b()+°-])*" +
            "(?:/[A-G][#b]?)?\\)?\\*?$"
    )
    private val FILLER = setOf("|", "-", "–", "x2", "x3", "x4", "(x2)", "(x3)", "(x4)", "N.C.", "NC")

    fun parse(source: String): List<Line> {
        val out = if (looksLikeChordPro(source)) parseChordPro(source) else parsePlain(source)
        mergeChordOnlyLines(out)
        while (out.isNotEmpty() && out.first().kind == Kind.EMPTY) out.removeAt(0)
        while (out.isNotEmpty() && out.last().kind == Kind.EMPTY) out.removeAt(out.size - 1)
        return out
    }

    /**
     * Fusioniert Akkord-Only-Zeilen (Liedtext leer, Akkorde gesetzt) mit der
     * unmittelbar folgenden Text-Only-Zeile (keine Akkorde). Entsteht z. B.
     * wenn der Importer [C]   [G] und "And all the lights..." als getrennte
     * Zeilen speichert — nach dem Merge sitzen die Akkorde korrekt über dem Text.
     */
    private fun mergeChordOnlyLines(lines: MutableList<Line>) {
        var i = 0
        while (i < lines.size - 1) {
            val cur = lines[i]
            val next = lines[i + 1]
            val curIsChordOnly = cur.kind == Kind.LYRIC && !cur.chords.isNullOrBlank() && cur.text.isBlank()
            val nextIsLyricOnly = next.kind == Kind.LYRIC && next.chords == null && next.text.isNotBlank()
            if (curIsChordOnly && nextIsLyricOnly) {
                lines[i] = Line(Kind.LYRIC, cur.chords, next.text)
                lines.removeAt(i + 1)
                // don't advance i — check merged line again in case of chain
            } else {
                i++
            }
        }
    }

    private fun looksLikeChordPro(source: String): Boolean {
        for (raw in source.lines()) {
            val t = raw.trim()
            if (t.startsWith("{") && t.endsWith("}")) return true
            // Inline-Akkord mitten im Text (nicht nur ein [Verse]-Header)?
            var i = t.indexOf('[')
            while (i >= 0) {
                val end = t.indexOf(']', i + 1)
                if (end > i + 1 && CHORD.matches(t.substring(i + 1, end)) &&
                    (i > 0 || end < t.length - 1)
                ) {
                    return true
                }
                i = if (end > i) t.indexOf('[', end) else -1
            }
        }
        return false
    }

    private fun parseChordPro(source: String): MutableList<Line> {
        val out = mutableListOf<Line>()
        for (raw in source.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                out.add(Line(Kind.EMPTY, null, ""))
                continue
            }
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val body = trimmed.substring(1, trimmed.length - 1)
                val sep = body.indexOf(':')
                val key = (if (sep >= 0) body.substring(0, sep) else body).trim().lowercase()
                val value = if (sep >= 0) body.substring(sep + 1).trim() else ""
                when (key) {
                    "comment", "c", "comment_italic", "ci", "highlight" ->
                        out.add(Line(Kind.COMMENT, null, value))
                    "start_of_chorus", "soc" -> out.add(Line(Kind.COMMENT, null, "— Refrain —"))
                    "start_of_bridge", "sob" -> out.add(Line(Kind.COMMENT, null, "— Bridge —"))
                    "section" -> out.add(Line(Kind.SECTION, null, value))
                    // title/artist/tempo/key etc. bewusst ausgeblendet
                }
                continue
            }
            out.add(parseLyricLine(line))
        }
        return out
    }

    /**
     * Ultimate-Guitar-Stil: Akkordzeilen stehen bereits über den Textzeilen
     * (Monospace-Ausrichtung bleibt erhalten), [Section]-Header werden zu
     * Hinweiszeilen.
     */
    private fun parsePlain(source: String): MutableList<Line> {
        val out = mutableListOf<Line>()
        val lines = source.lines().map { it.trimEnd() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                line.isBlank() -> out.add(Line(Kind.EMPTY, null, ""))
                trimmed.startsWith("[") && trimmed.endsWith("]") &&
                    !isChordLine(trimmed.substring(1, trimmed.length - 1)) ->
                    out.add(Line(Kind.COMMENT, null, trimmed.substring(1, trimmed.length - 1)))
                isChordLine(line) -> {
                    // Nächste NICHT-leere Zeile suchen (Import kann eine Leerzeile
                    // zwischen Akkord- und Textzeile eingefügt haben).
                    var j = i + 1
                    while (j < lines.size && lines[j].isBlank()) j++
                    val next = lines.getOrNull(j)
                    if (next != null && next.isNotBlank() && !isChordLine(next) &&
                        !(next.trim().startsWith("[") && next.trim().endsWith("]"))
                    ) {
                        out.add(Line(Kind.LYRIC, line, next))
                        i = j
                    } else {
                        out.add(Line(Kind.LYRIC, line, ""))
                    }
                }
                else -> out.add(Line(Kind.LYRIC, null, line))
            }
            i++
        }
        return out
    }

    private fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        var chordCount = 0
        for (token in tokens) {
            val clean = token.removeSuffix(",")
            when {
                CHORD.matches(clean.removeSurrounding("[", "]")) -> chordCount++
                clean in FILLER -> Unit
                else -> return false
            }
        }
        return chordCount > 0
    }

    private fun parseLyricLine(line: String): Line {
        val text = StringBuilder()
        val chords = StringBuilder()
        var hasChord = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '[') {
                val end = line.indexOf(']', i + 1)
                if (end > i + 1) {
                    val chord = line.substring(i + 1, end)
                    while (chords.length < text.length) chords.append(' ')
                    if (chords.isNotEmpty() && chords.last() != ' ') chords.append(' ')
                    chords.append(chord)
                    hasChord = true
                    i = end + 1
                    continue
                }
            }
            text.append(ch)
            i++
        }
        return Line(Kind.LYRIC, if (hasChord) chords.toString() else null, text.toString())
    }
}
