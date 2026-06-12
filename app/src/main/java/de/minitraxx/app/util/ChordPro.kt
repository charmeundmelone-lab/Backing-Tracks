package de.minitraxx.app.util

/**
 * Parser für Songtexte mit Akkorden. Versteht zwei Formate und erkennt sie
 * automatisch:
 *  - ChordPro: [Akkord]-Marker im Text, Direktiven in {}
 *  - Ultimate-Guitar-Stil: eigene Akkordzeile über der Textzeile,
 *    Abschnitts-Marker wie [Verse 1] allein auf einer Zeile
 */
object ChordPro {

    enum class Kind { LYRIC, COMMENT, EMPTY }

    data class Line(val kind: Kind, val chords: String?, val text: String)

    /** Erkennt einen einzelnen Akkordnamen wie Em7, Dsus4, Cadd9, D/F#. */
    private val CHORD = Regex(
        "^\\(?[A-G][#b]?(?:maj|min|mi|m|M|sus|dim|aug|add|[0-9]|[#b()+°-])*" +
            "(?:/[A-G][#b]?)?\\)?\\*?$"
    )
    private val FILLER = setOf("|", "-", "–", "x2", "x3", "x4", "(x2)", "(x3)", "(x4)", "N.C.", "NC")

    fun parse(source: String): List<Line> {
        val out = if (looksLikeChordPro(source)) parseChordPro(source) else parsePlain(source)
        while (out.isNotEmpty() && out.first().kind == Kind.EMPTY) out.removeAt(0)
        while (out.isNotEmpty() && out.last().kind == Kind.EMPTY) out.removeAt(out.size - 1)
        return out
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
                    val next = lines.getOrNull(i + 1)
                    if (next != null && next.isNotBlank() && !isChordLine(next) &&
                        !(next.trim().startsWith("[") && next.trim().endsWith("]"))
                    ) {
                        out.add(Line(Kind.LYRIC, line, next))
                        i++
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
