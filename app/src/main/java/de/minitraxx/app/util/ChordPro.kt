package de.minitraxx.app.util

/**
 * Minimaler ChordPro-Parser: [Akkord]-Marker werden in eine Akkordzeile über
 * der Textzeile umgesetzt (Monospace-Ausrichtung). Direktiven in {} werden
 * ausgeblendet; Kommentare und Abschnitts-Marker erscheinen als Hinweiszeile.
 */
object ChordPro {

    enum class Kind { LYRIC, COMMENT, EMPTY }

    data class Line(val kind: Kind, val chords: String?, val text: String)

    fun parse(source: String): List<Line> {
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
        while (out.isNotEmpty() && out.first().kind == Kind.EMPTY) out.removeAt(0)
        while (out.isNotEmpty() && out.last().kind == Kind.EMPTY) out.removeAt(out.size - 1)
        return out
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
