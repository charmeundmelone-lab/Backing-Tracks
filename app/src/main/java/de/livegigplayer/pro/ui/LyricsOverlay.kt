package de.livegigplayer.pro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val LyricsBg    = Color(0xFF0A0A0A)
private val LyricsVolt  = Color(0xFFE8FF00)
private val LyricsGray  = Color(0xFF777777)
private val LyricsWhite = Color(0xFFFFFFFF)
private val LyricsRed   = Color(0xFFDC2626)

// Struktur-Label wie "[Chorus]" oder "[Verse 1]" — keine Akkorde, nur Songaufbau.
// Wird als eigene, farblich abgesetzte Überschrift gerendert statt als Lyric-Zeile.
private val sectionTagRegex = Regex("^\\[(.+)]$")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Kalibrierungspunkte-Serialisierung: "lineIndex:positionMs" kommagetrennt, nach
// positionMs sortiert. Bewusst kein JSON — gleiche Diy-Delimiter-Idiomatik wie
// audioFilePath ("{treeUri}||{folderName}", siehe Gotcha 4).
private fun serializeSyncPoints(points: List<Pair<Int, Long>>): String =
    points.sortedBy { it.second }.joinToString(",") { "${it.first}:${it.second}" }

private fun parseSyncPoints(raw: String): List<Pair<Int, Long>> =
    if (raw.isBlank()) emptyList()
    else raw.split(",").mapNotNull { entry ->
        val parts = entry.split(":")
        val idx = parts.getOrNull(0)?.toIntOrNull()
        val ms  = parts.getOrNull(1)?.toLongOrNull()
        if (idx != null && ms != null) idx to ms else null
    }.sortedBy { it.second }

// "mm:ss" (song.duration, z.B. "5:22") → ms. Zusätzliche Absicherung gegen eine
// zu kurze live gemeldete durationMs (siehe FolderImporter "Bug-Fix 2"-Kommentar
// zum Click-Track) — wird in LyricsContent per max() mit der Live-Dauer kombiniert.
private fun parseDurationString(s: String): Long {
    val parts = s.split(":")
    val min = parts.getOrNull(0)?.toLongOrNull() ?: return 0L
    val sec = parts.getOrNull(1)?.toLongOrNull() ?: return 0L
    return (min * 60 + sec) * 1000
}

/**
 * Vollbild-Teleprompter: reine Lyrics (keine Akkorde), Hochkant, Auto-Scroll
 * gekoppelt an die echte Wiedergabeposition. Zwei Sync-Mechanismen:
 *
 * 1. **Kalibrierung** (Record-Button im Header): einmal pro Song durchtippen —
 *    ein Tap pro ABSCHNITTS-Anfang, sobald man ihn ansingt. Intro/Solo NICHT
 *    tippen — sie werden automatisch abgefangen (siehe Lese-Uhr unten). Jeder
 *    Tap speichert (Zeilen-Index, Wiedergabeposition). Danach läuft der Scroll
 *    bei jedem künftigen Play automatisch — kein Live-Tippen mehr nötig.
 * 2. **Live-Tap-to-Sync** (Tap irgendwo im Textbereich, außerhalb Kalibrierung):
 *    einmalige, NICHT gespeicherte Korrektur für die laufende Wiedergabe.
 *
 * **LESE-UHR-MODELL (Idee 1, Sprint 5.48):** Der Scroll läuft nach einer
 * "Lese-Uhr" statt linear über die Pixel. NUR Gesangszeilen verbrauchen Zeit
 * (Gewicht ∝ Zeichenzahl → lange Zeilen bleiben länger oben); Leerzeilen,
 * Struktur-Labels und Instrumental-Teile ([Intro]/[Solo]) haben Gewicht 0.
 * Pro Segment (zwischen zwei Kalibrier-Ankern) zwei Phasen: **Phase 1 (Lesen)**
 * — die Gesangszeilen laufen im gelernten natürlichen Sing-Tempo `naturalPace`
 * (ms pro Gewicht, robust aus den dichtesten Segmenten geschätzt) oben durch;
 * **Phase 2 (Warten)** — ist der Text durchgesungen, aber die Musik läuft noch
 * (Instrumental-Ausklang, Solo, Intro), gleitet der Scroll nur noch sanft zum
 * nächsten Anker. So wird die Instrumental-Zeit AUSGESESSEN, statt sie über die
 * Gesangszeilen zu schmieren — genau das war die Ursache des "Mitte-Driftens"
 * (Sprint 5.47). Der Abschnitts-Anfang steht weiterhin am OBEREN Bildschirmrand
 * (Oben-Anker, Sprint 5.46). Der frühere 30%-Lesepunkt (Sprint 5.39) rückte
 * über die aktuelle Zeile permanent ~15 Zeilen bereits gesungenen Text — die
 * strukturelle Ursache des "hinkt hinterher"-Gefühls, seit 5.46 behoben.
 *
 * ARCHITEKTUR (Sprint 5.36 — bewusst ohne ScrollState/Animation): Frühere Versuche
 * nutzten Compose's `ScrollState` (`verticalScroll` + `scrollTo`/`animateScrollTo`).
 * Das Problem dabei: `handleTap()` rief `animateScrollTo()` auf, während die
 * Frame-Loop GLEICHZEITIG jeden Frame `scrollTo()` aufrief — beide konkurrieren
 * um dieselbe interne Compose-Sperre (MutatorMutex) und unterbrechen sich
 * gegenseitig, was zu sichtbarem Ruckeln führen kann, unabhängig von der
 * eigentlichen Zielberechnung. Zusätzlich hing die Rate-Berechnung von
 * `ScrollState.maxValue` ab, dessen genaues Timing/Verhalten mit `enabled=false`
 * nicht vollständig nachvollziehbar war. Beides eliminiert: eigenes `Layout`
 * (siehe unten) misst den Text-Block EXPLIZIT mit `maxHeight = Constraints.Infinity`
 * und platziert ihn direkt selbst per `placeRelative(0, -scrollOffsetPx…)` — eine
 * einzige Zustandsvariable (`scrollOffsetPx`), EIN Schreiber (die Frame-Loop;
 * `handleTap()` verschiebt nur den Anker, die Loop holt den neuen Wert im
 * nächsten Frame selbst ab), keine konkurrierende Animation.
 *
 * **Sprint 5.38 — zweiter Bug in derselben Architektur:** Die erste Version
 * (5.36/5.37) hatte Viewport und Content in einer normalen `Box(fillMaxSize) {
 * Column(fillMaxWidth) }` verschachtelt. Eine `Box` reicht ihre eigene
 * (durch den Viewport begrenzte) `maxHeight`-Constraint automatisch an ihre
 * Kinder weiter — die Content-Column konnte dadurch NIE höher gemessen werden
 * als der sichtbare Ausschnitt selbst. `contentHeightPx` blieb praktisch immer
 * gleich `viewportHeightPx`, `maxScrollPx` damit strukturell ~0 → die Frame-Loop
 * wartete für immer auf sinnvollen Scroll-Bedarf, der nie kam (kompletter
 * Stillstand, unabhängig von Taps). Das eigene `Layout` misst die Content-
 * Column jetzt mit `constraints.copy(maxHeight = Constraints.Infinity)` —
 * KEIN Verlass mehr auf automatische Constraint-Weitergabe.
 *
 * Viewport- und Content-Höhe werden direkt in der `Layout`-Messphase erfasst
 * (`constraints.maxHeight` bzw. `placeable.height`) statt über eine separate
 * `onGloballyPositioned`-Messung — `maxScrollPx` wird direkt daraus berechnet.
 * Die Frame-Loop wartet zusätzlich explizit, bis ALLE Zeilen vermessen sind
 * (`allLinesMeasured`), bevor überhaupt gerechnet wird — eliminiert die ganze
 * Klasse von Bugs durch teilweise vermessene Layouts beim (Wieder-)Öffnen.
 *
 * Scroll bewegt sich in beiden Fällen ausschließlich vorwärts/abwärts (siehe
 * scrollOffsetPx-Klemmung in LyricsContent) — niemals zurück.
 */
@Composable
fun LyricsOverlay(
    visible: Boolean,
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSetLyricsSyncPoints: (Long, String) -> Unit = { _, _ -> },
    debugLog: List<String> = emptyList(),
    onLogDebug: (String) -> Unit = {},
    onLogWarn: (String) -> Unit = {}
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(visible) {
        val original = activity?.requestedOrientation
        if (visible) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (visible && original != null) activity?.requestedOrientation = original
        }
    }

    // Zählt jedes Öffnen hoch — als zusätzlicher remember()-Key in LyricsContent
    // erzwingt das einen kompletten Reset des Scroll-Zustands bei jedem Öffnen,
    // unabhängig davon, ob AnimatedVisibility die alte Komposition zwischen zwei
    // schnellen Schließen/Öffnen-Zyklen noch am Leben hält.
    var openSession by remember { mutableStateOf(0) }
    LaunchedEffect(visible) { if (visible) openSession++ }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        if (song != null) {
            LyricsContent(
                song                  = song,
                openSession           = openSession,
                positionMs            = positionMs,
                durationMs            = durationMs,
                isPlaying             = isPlaying,
                onClose               = onClose,
                onSetLyricsSyncPoints = onSetLyricsSyncPoints,
                debugLog              = debugLog,
                onLogDebug            = onLogDebug,
                onLogWarn             = onLogWarn
            )
        }
    }
}

@Composable
private fun LyricsContent(
    song: Song,
    openSession: Int,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSetLyricsSyncPoints: (Long, String) -> Unit,
    debugLog: List<String>,
    onLogDebug: (String) -> Unit,
    onLogWarn: (String) -> Unit
) {
    val context = LocalContext.current
    val lines   = remember(song.id, song.lyrics) { song.lyrics.lines() }

    // Lese-Uhr-Modell (Idee 1): nur GESANGSzeilen verbrauchen Lese-Zeit; Leerzeilen
    // und Struktur-Labels ([Verse], [Solo], …) zählen als reine Wartezeit (Gewicht 0).
    // lineIsLyric[i]  = echte Textzeile (kein Label, nicht leer)
    // lineWeight[i]   = Zeit-Gewicht dieser Zeile (∝ Zeichenzahl bei Gesang, sonst 0) —
    //                   längere Zeilen bleiben proportional länger oben stehen.
    val lineIsLyric = remember(lines) {
        lines.map { it.isNotBlank() && sectionTagRegex.find(it.trim()) == null }
    }
    val lineWeight = remember(lines, lineIsLyric) {
        FloatArray(lines.size) { i ->
            if (lineIsLyric[i]) lines[i].trim().length.coerceAtLeast(1).toFloat() else 0f
        }
    }
    // Strukturelle Instrumental-Erkennung (Idee-1-Verfeinerung, Sprint 5.51): KEIN
    // Keyword-Abgleich ("Solo"/"Intro"/…) — ein Label gilt als instrumental, wenn bis
    // zum nächsten Label (oder Songende) keine einzige Gesangszeile folgt. Das nutzt
    // nur die ohnehin geltende Text-Konvention aus (reine Instrumental-Teile als
    // eigenes Label OHNE Textzeilen) und funktioniert unabhängig vom Wortlaut/der
    // Sprache des Labels. isInstrumentalLabel[i] ist nur an Label-Zeilen true.
    val isInstrumentalLabel = remember(lines, lineWeight) {
        val labelIdx = lines.indices.filter { sectionTagRegex.find(lines[it].trim()) != null }
        val flags = BooleanArray(lines.size)
        for ((pos, idx) in labelIdx.withIndex()) {
            val nextBoundary = labelIdx.getOrNull(pos + 1) ?: lines.size
            var hasLyricAfter = false
            var j = idx + 1
            while (j < nextBoundary) { if (lineWeight[j] > 0f) { hasLyricAfter = true; break }; j++ }
            flags[idx] = !hasLyricAfter
        }
        flags
    }
    val measuredLineCount = lines.size

    // Gespeicherte Kalibrierungspunkte dieses Songs, sortiert nach Position.
    val breakpoints = remember(song.id, song.lyricsSyncPoints) { parseSyncPoints(song.lyricsSyncPoints) }

    // index -> gemessene Y-Position (px) innerhalb der Text-Column (siehe Architektur-
    // Kommentar oben). Mit openSession gekeyt: jedes Öffnen startet komplett frisch.
    val linePositions = remember(song.id, openSession) { mutableMapOf<Int, Float>() }
    var viewportHeightPx by remember(song.id, openSession) { mutableStateOf(0f) }
    var contentHeightPx  by remember(song.id, openSession) { mutableStateOf(0f) }

    // Kalibrierungs-Modus: Record-Button an → jeder Tap wird zusätzlich zum
    // Live-Sync in calibrationPoints aufgezeichnet und beim Beenden persistiert.
    var calibrating by remember(song.id, openSession) { mutableStateOf(false) }
    val calibrationPoints = remember(song.id, openSession) { mutableStateListOf<Pair<Int, Long>>() }

    fun persistCalibration() {
        if (calibrationPoints.isNotEmpty()) {
            onSetLyricsSyncPoints(song.id, serializeSyncPoints(calibrationPoints))
        }
    }

    // Sicherheitsnetz gegen Datenverlust (Sprint 5.49): Wenn der Song WÄHREND einer
    // laufenden Kalibrierung wechselt — z.B. weil er zu Ende ist und die App im
    // CUE-Modus lautlos den nächsten Song armt (siehe Gotcha 12/Sprint 5.43) —
    // werden calibrating/calibrationPoints normalerweise sofort verworfen, weil sie
    // an song.id gebunden sind (remember(song.id, openSession)), BEVOR der User
    // manuell auf Stop tippen konnte. onDispose feuert exakt in dem Moment, in dem
    // der alte song.id-Kontext (samt calibrationPoints) durch den neuen ersetzt
    // wird — hier wird noch mit den ALTEN, in dieser Closure gefangenen Werten
    // gespeichert, bevor sie weg sind. Kein Datenverlust mehr, unabhängig davon,
    // ob der User rechtzeitig Stop drückt.
    DisposableEffect(song.id) {
        onDispose {
            if (calibrating && calibrationPoints.isNotEmpty()) {
                persistCalibration()
                onLogWarn("Song während laufender Kalibrierung gewechselt (Auto-Advance/CUE) " +
                    "— ${calibrationPoints.size} Punkte automatisch gerettet und gespeichert.")
            }
        }
    }

    // Segment-Anker (Zeit, Scroll-Px), ab dem die aktuelle Scroll-Rate berechnet
    // wird. Schaltet automatisch durch die Kalibrierungspunkte weiter, sobald die
    // Wiedergabe sie erreicht (siehe LaunchedEffect unten) — ohne Kalibrierung
    // bleibt es bei (0, 0), also reine Positions-Proportion.
    var anchorPositionMs by remember(song.id, openSession) { mutableStateOf(0L) }
    var anchorScrollPx   by remember(song.id, openSession) { mutableStateOf(0f) }
    // Zeilen-Index des aktuellen Ankers — nötig fürs Lese-Uhr-Modell (Segment-Grenzen).
    var anchorLineIdx    by remember(song.id, openSession) { mutableStateOf(0) }
    // Aktuell angewandter Scroll-Offset — EINZIGE Quelle der Wahrheit fürs Rendering
    // (siehe Layout-placeRelative unten). Wird NIE verringert (nur abwärts, nie zurück).
    // Einziger Schreiber ist die Frame-Loop unten; handleTap() setzt nur den Anker,
    // die Loop übernimmt den neuen Wert automatisch im nächsten Frame — dadurch gibt
    // es nur einen einzigen Mutationspfad, keine konkurrierende Animation mehr.
    var scrollOffsetPx by remember(song.id, openSession) { mutableStateOf(0f) }

    // Zusätzliche Absicherung gegen eine zu kurze live gemeldete durationMs.
    val dbDurationMs = remember(song.id, song.duration) { parseDurationString(song.duration) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestDurationMs by rememberUpdatedState(maxOf(durationMs, dbDurationMs))
    val latestIsPlaying   by rememberUpdatedState(isPlaying)

    // positionMs kommt aus PlayerViewModel und wird dort nur alle 200ms aktualisiert
    // (Poll-Loop, delay(200L)) — Frame-Loop UND handleTap() brauchen aber eine zum
    // Zeitpunkt des Aufrufs möglichst genaue, kontinuierliche Schätzung, nicht den
    // eingefrorenen 200ms-Rohwert (sonst "Treppenstufen" statt smooth, wirkt konstant/
    // hinterher — das war der eigentliche Bug hinter "Song kommt nicht hinterher").
    // lastRawPositionMs/-AtNanos verankern den letzten ECHTEN Messwert; solange er sich
    // nicht ändert, wird per realer Systemzeit linear hochgerechnet (gekappt auf max.
    // 400ms) — der nächste echte 200ms-Wert korrigiert die Hochrechnung automatisch,
    // kein Drift. Auf Composable-Ebene gehalten (nicht lokal im LaunchedEffect), damit
    // sowohl die Frame-Loop als auch handleTap() dieselbe Schätzung verwenden.
    var lastRawPositionMs   by remember(song.id, openSession) { mutableStateOf(positionMs) }
    var lastRawPositionAtNs by remember(song.id, openSession) { mutableStateOf(System.nanoTime()) }
    var wasPlayingForEstimate by remember(song.id, openSession) { mutableStateOf(isPlaying) }
    fun estimatedPositionMs(): Long {
        val rawPos = latestPositionMs
        val nowNs  = System.nanoTime()
        val justResumed = latestIsPlaying && !wasPlayingForEstimate
        if (rawPos != lastRawPositionMs || justResumed) {
            lastRawPositionMs   = rawPos
            lastRawPositionAtNs = nowNs
        }
        wasPlayingForEstimate = latestIsPlaying
        return if (latestIsPlaying) {
            val elapsedMs = (nowNs - lastRawPositionAtNs) / 1_000_000L
            lastRawPositionMs + elapsedMs.coerceIn(0L, 400L)
        } else {
            lastRawPositionMs
        }
    }

    // Summe der Gesangs-Gewichte in [fromLine, toLine).
    fun weightBetween(fromLine: Int, toLine: Int): Float {
        var sum = 0f
        var i = fromLine.coerceAtLeast(0)
        val end = toLine.coerceAtMost(lineWeight.size)
        while (i < end) { sum += lineWeight[i]; i++ }
        return sum
    }

    // Enthält [fromLine, toLine) ein strukturell erkanntes Instrumental-Label
    // (siehe isInstrumentalLabel)? Nur für solche Segmente ist eine "Warte"-Phase
    // (Phase 2) inhaltlich gerechtfertigt — sonst wäre eine niedrige Gewicht/Zeit-
    // Relation nur ein Zeichen für langsameren Gesang, kein echtes Instrumental.
    fun segmentHasInstrumental(fromLine: Int, toLine: Int): Boolean {
        var i = fromLine.coerceAtLeast(0)
        val end = toLine.coerceAtMost(isInstrumentalLabel.size)
        while (i < end) { if (isInstrumentalLabel[i]) return true; i++ }
        return false
    }

    // Scroll-Pixel während der LESE-Phase: verbraucht `targetW` Gewicht ab `fromLine`
    // und bleibt dabei auf jeder Gesangszeile proportional zu ihrem Gewicht stehen
    // (lange Zeilen länger). Nicht-Gesangszeilen (Gewicht 0) werden übersprungen.
    // Bei vollständig verbrauchtem Gewicht steht der Pixel am Ende des letzten
    // gesungenen Blocks — von dort übernimmt die WARTE-Phase.
    fun readingPixel(fromLine: Int, toLine: Int, targetW: Float): Float {
        var acc = 0f
        var i = fromLine.coerceAtLeast(0)
        val end = toLine.coerceAtMost(lineWeight.size)
        while (i < end) {
            val w = lineWeight[i]
            if (w > 0f && acc + w >= targetW) {
                val frac = ((targetW - acc) / w).coerceIn(0f, 1f)
                val p0 = linePositions[i] ?: 0f
                val p1 = linePositions[i + 1] ?: p0
                return p0 + (p1 - p0) * frac
            }
            acc += w
            i++
        }
        return linePositions[end] ?: (linePositions[(end - 1).coerceAtLeast(0)] ?: 0f)
    }

    // Frame-für-Frame-Scroll nach dem LESE-UHR-Modell (Idee 1, Sprint 5.48):
    // Nur Gesangszeilen verbrauchen Zeit; jede läuft im natürlichen Sing-Tempo oben
    // durch. Instrumental-Teile (Intro/Solo/Ausklänge) haben kein Gewicht und werden
    // ausgesessen — statt ihre Zeit über die Gesangszeilen zu schmieren (das war die
    // Ursache des "Mitte-Driftens"). Pro Segment (zwischen zwei Kalibrier-Ankern):
    //   Phase 1 (Lesen):  Gesangszeilen im Tempo `naturalPace`.
    //   Phase 2 (Warten): Rest der Segmentzeit sanft zum nächsten Anker gleiten.
    // `naturalPace` wird aus den dichtesten Segmenten gelernt (siehe unten).
    LaunchedEffect(song.id, openSession, song.lyricsSyncPoints) {
        // Natürliches Sing-Tempo (ms pro Gewichtseinheit): aus den am dichtesten
        // gesungenen Segmenten (kaum Instrumental-Luft). Robuster niedriger Wert
        // (~20. Perzentil, nur "volle" Segmente), damit instrumental-gepolsterte
        // Segmente das Tempo nicht künstlich verlangsamen. Ein eher schnelles Tempo
        // lässt Zeilen minimal zu FRÜH oben ankommen (gut zum Vorlesen) statt zu spät.
        val naturalPace: Float = run {
            val tpws = ArrayList<Float>()
            val segWs = ArrayList<Float>()
            var maxW = 0f
            for (j in 0 until breakpoints.size - 1) {
                val fromL = breakpoints[j].first
                val toL   = breakpoints[j + 1].first
                // Segmente mit echtem Instrumental-Anteil ausschließen — ihre Zeit
                // enthält Warte-Anteile, die das gelernte Sing-Tempo sonst verzerren.
                if (segmentHasInstrumental(fromL, toL)) continue
                val w = weightBetween(fromL, toL)
                val t = (breakpoints[j + 1].second - breakpoints[j].second).toFloat()
                if (w > 0f && t > 0f) { segWs.add(w); tpws.add(t / w); if (w > maxW) maxW = w }
            }
            val qualifying = tpws.indices
                .filter { segWs[it] >= 0.4f * maxW }
                .map { tpws[it] }
                .sorted()
            when {
                qualifying.isNotEmpty() -> qualifying[qualifying.size / 5]
                tpws.isNotEmpty()       -> tpws.minOrNull() ?: 250f
                else                    -> 250f
            }
        }
        onLogDebug("Lese-Uhr start: song='${song.title}' dur=$durationMs (db=$dbDurationMs) " +
            "naturalPace=${naturalPace}ms/Gew. breakpoints=$breakpoints")

        var nextIdx = 0
        var guardBlockLogged = false
        while (isActive) {
            withFrameNanos { }

            // Nichts tun, bevor Layout vollständig vermessen ist (inkl. Leerzeilen).
            val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
            val allLinesMeasured = linePositions.size >= measuredLineCount
            if (!allLinesMeasured || contentHeightPx <= 0f || viewportHeightPx <= 0f || maxScrollPx <= 0f) {
                continue
            }

            val pos = estimatedPositionMs()
            val dur = latestDurationMs

            // Während einer NEUEN Kalibrierung treiben die ALTEN Punkte den Scroll nicht
            // (sonst kämpfen altes Auto-Scrolling und frische Taps gegeneinander).
            val useBreakpoints = !calibrating

            var segmentJustChanged = false
            while (useBreakpoints && nextIdx < breakpoints.size && breakpoints[nextIdx].second <= pos) {
                val (lineIdx, ms) = breakpoints[nextIdx]
                val px = linePositions[lineIdx]
                if (px != null) {
                    anchorPositionMs = ms; anchorScrollPx = px; anchorLineIdx = lineIdx
                    segmentJustChanged = true
                } else {
                    onLogWarn("Kalibrierungspunkt #${nextIdx + 1}/${breakpoints.size} " +
                        "(lineIdx=$lineIdx, ms=$ms) hat KEINE gemessene Zeilen-Position — übersprungen!")
                }
                nextIdx++
            }

            if (dur in 1..<pos && !guardBlockLogged) {
                guardBlockLogged = true
                onLogWarn("Guard blockiert: dur=$dur < pos=$pos — durationMs war zu klein")
            }
            if (dur <= 0L || dur < pos || dur <= anchorPositionMs) continue

            val hasModel   = useBreakpoints && breakpoints.isNotEmpty()
            val nextBreak  = if (useBreakpoints) breakpoints.getOrNull(nextIdx) else null
            val segEndMs   = nextBreak?.second ?: dur
            val segEndPx   = nextBreak?.let { linePositions[it.first] } ?: maxScrollPx
            val segEndLine = nextBreak?.first ?: lines.size
            if (segEndMs <= anchorPositionMs) continue

            val segT    = (segEndMs - anchorPositionMs).toFloat()
            val elapsed = (pos - anchorPositionMs).toFloat()

            // Diagnose: bei JEDEM Abschnittswechsel loggen, unabhängig davon, in welcher
            // Phase wir uns in diesem Frame gerade befinden (vorher feuerte das Log nur,
            // wenn der Wechsel-Frame zufällig noch in Phase 1 lag — z.B. direkt nach dem
            // Neustart des Loops, wenn mehrere Breakpoints in einem Frame aufgeholt
            // werden, blieb das Log stumm, obwohl der Wechsel echt passiert ist).
            if (segmentJustChanged) {
                val segWLog = if (hasModel) weightBetween(anchorLineIdx, segEndLine) else -1f
                val instrLog = if (hasModel) segmentHasInstrumental(anchorLineIdx, segEndLine) else null
                onLogDebug("Segment: anchorLine=$anchorLineIdx->$segEndLine segW=$segWLog " +
                    "segT=${segT}ms pace=$naturalPace instrumental=$instrLog pos=$pos")
            }

            val targetPx: Float
            if (!hasModel) {
                // Fallback (Kalibrierung / noch keine Punkte): einfache lineare Fahrt.
                targetPx = anchorScrollPx + (segEndPx - anchorScrollPx) * (elapsed / segT).coerceIn(0f, 1f)
            } else {
                val segW = weightBetween(anchorLineIdx, segEndLine)
                if (segW <= 0f) {
                    // Reines Instrumental-/Übergangssegment: gleichmäßig gleiten.
                    targetPx = anchorScrollPx + (segEndPx - anchorScrollPx) * (elapsed / segT).coerceIn(0f, 1f)
                } else if (!segmentHasInstrumental(anchorLineIdx, segEndLine)) {
                    // KEIN Instrumental-Label in diesem Segment (Sprint 5.51): eine
                    // niedrige Gewicht/Zeit-Relation hier bedeutet NICHT "es gibt eine
                    // Pause zu warten", sondern nur "hier wird langsamer gesungen als im
                    // Referenz-Segment". Deshalb KEINE Phase 2 — die volle Segmentzeit
                    // wird mit dem SEGMENT-EIGENEN (lokalen) Tempo gleichmäßig auf die
                    // eigenen Zeilen verteilt statt mit dem globalen naturalPace.
                    val targetW = (elapsed / segT) * segW
                    targetPx = readingPixel(anchorLineIdx, segEndLine, targetW)
                } else {
                    val readDuration = (segW * naturalPace).coerceAtMost(segT)
                    if (elapsed < readDuration) {
                        // Phase 1 — Lesen im Sing-Tempo.
                        val targetW = (elapsed / readDuration) * segW
                        targetPx = readingPixel(anchorLineIdx, segEndLine, targetW)
                    } else {
                        // Phase 2 — Instrumental/Ausklang aussitzen, sanft zum nächsten Anker.
                        val readEndPx  = readingPixel(anchorLineIdx, segEndLine, segW)
                        val phase2Dur  = segT - readDuration
                        targetPx = if (phase2Dur > 0f)
                            readEndPx + (segEndPx - readEndPx) *
                                ((elapsed - readDuration) / phase2Dur).coerceIn(0f, 1f)
                        else segEndPx
                    }
                }
            }

            val clamped = targetPx.coerceIn(0f, maxScrollPx)
            if (clamped > scrollOffsetPx) scrollOffsetPx = clamped   // nur vorwärts, nie zurück
        }
    }

    // Ein Handler für beide Tap-Arten: sucht die nächste noch nicht erreichte
    // Zeile (Header oder Lyric, macht keinen Unterschied), setzt den Anker darauf
    // UND springt sofort sichtbar dorthin — sonst bewegt sich während einer
    // laufenden Kalibrierung (noch ohne Kalibrierungspunkte) zwischen zwei Taps
    // nur ein Bruchteil-Pixel, weil die Frame-Loop bis dahin noch mit "Rest des
    // ganzen Songs bis zum Ende" als Zielspanne rechnet. Der Sofort-Sprung ist
    // hier sicher (im Gegensatz zur alten ScrollState-Version, siehe Architektur-
    // Kommentar oben): kein Animate-Aufruf, keine konkurrierende Coroutine — nur
    // eine simple, monoton geklemmte Zuweisung, genau wie in der Frame-Loop.
    // Zeichnet den Punkt zusätzlich auf, wenn gerade kalibriert wird.
    fun handleTap() {
        // Nur echte GESANGSzeilen sind Tap-Ziele (keine Leerzeilen/Labels/Sentinel) —
        // so verankert ein Tap immer die nächste zu singende Zeile, nicht eine Lücke.
        val entry = linePositions.entries
            .filter { it.key < lineIsLyric.size && lineIsLyric[it.key] && it.value > scrollOffsetPx + 4f }
            .minByOrNull { it.value } ?: return
        val pos = estimatedPositionMs()
        anchorPositionMs = pos
        anchorScrollPx   = entry.value
        anchorLineIdx    = entry.key
        if (entry.value > scrollOffsetPx) scrollOffsetPx = entry.value
        if (calibrating) calibrationPoints.add(entry.key to pos)
    }

    // fillMaxSize() deckt zwar optisch den ganzen Screen ab, konsumiert aber
    // ohne eigenen Touch-Handler keine Taps in Lücken zwischen den Buttons —
    // Compose lässt solche Taps sonst zur dahinterliegenden MainScreen-TopBar
    // durchfallen (die an derselben Bildschirmposition oben rechts ihr eigenes
    // "⋮"-Menü hat). Leerer detectTapGestures-Handler auf der äußersten Box
    // fängt jeden nicht anderweitig konsumierten Tap ab, ohne die spezifischeren
    // Handler der Kind-Elemente (Header-Buttons, Tap-to-Sync-Viewport) zu stören
    // — Compose testet Kind-Elemente zuerst.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LyricsBg)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = LyricsVolt, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (song.artist.isNotBlank())
                        Text(song.artist, color = LyricsGray, fontSize = 12.sp, maxLines = 1)
                }
                if (!isPlaying) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Filled.Pause, contentDescription = null, tint = LyricsGray, modifier = Modifier.size(14.dp))
                        Text(" pausiert", color = LyricsGray, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = {
                    if (calibrating) {
                        calibrating = false
                        persistCalibration()
                        Toast.makeText(context, "${calibrationPoints.size} Kalibrierungspunkte gespeichert", Toast.LENGTH_SHORT).show()
                    } else {
                        calibrating = true
                        calibrationPoints.clear()
                        Toast.makeText(context, "Kalibrierung: bei jedem Abschnitts-Anfang tippen, sobald du ihn ansingst. Intro/Solo überspringen — die App fängt sie selbst ab.", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(
                        if (calibrating) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (calibrating) "Kalibrierung beenden & speichern" else "Kalibrierung starten",
                        tint = if (calibrating) LyricsRed else LyricsGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {
                    if (debugLog.isEmpty()) {
                        Toast.makeText(context, "Noch kein Diagnose-Log vorhanden", Toast.LENGTH_SHORT).show()
                    } else {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "LyricsOverlay-Diagnose: ${song.title}")
                            putExtra(Intent.EXTRA_TEXT, debugLog.joinToString("\n"))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Diagnose-Log teilen"))
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Diagnose-Log teilen", tint = LyricsGray, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    if (calibrating) { calibrating = false; persistCalibration() }
                    onClose()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = LyricsGray)
                }
            }

            if (calibrating) {
                Text(
                    "● Kalibrierung läuft — ${calibrationPoints.size} Punkte — bei jedem Abschnitts-Anfang tippen (Intro/Solo überspringen)",
                    color = LyricsRed, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Viewport: fester Ausschnitt, Inhalt wird per eigenem Layout reingeschoben
            // (siehe Architektur-Kommentar oben) statt über Compose's ScrollState.
            //
            // WICHTIG (Sprint 5.38): Ein normales Box(fillMaxSize) { Column(fillMaxWidth) }
            // gibt der Column NICHT automatisch unbegrenzte Höhe — die Box reicht ihre
            // eigene (durch den Viewport begrenzte) maxHeight-Constraint an die Column
            // weiter. Der Text-Block konnte dadurch nie höher gemessen werden als der
            // sichtbare Ausschnitt selbst, `contentHeightPx` blieb praktisch immer gleich
            // `viewportHeightPx`, `maxScrollPx` damit ~0 → die Frame-Loop wartete für
            // immer auf sinnvollen Scroll-Bedarf, der nie kam. Kompletter Stillstand,
            // unabhängig von Taps. Fix: eigenes `Layout` misst die Content-Column
            // EXPLIZIT mit `maxHeight = Constraints.Infinity` — Höhe und Platzierung
            // (inkl. Scroll-Offset) werden hier direkt selbst kontrolliert, kein Verlass
            // mehr auf automatische Constraint-Weitergabe.
            Box(modifier = Modifier.fillMaxSize()) {
                Layout(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { handleTap() })
                        },
                    content = {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            lines.forEachIndexed { index, line ->
                                val sectionLabel = sectionTagRegex.find(line)?.groupValues?.get(1)
                                when {
                                    line.isBlank() -> Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .onGloballyPositioned { coords ->
                                                linePositions[index] = coords.positionInParent().y
                                            }
                                    )
                                    sectionLabel != null -> Text(
                                        text = sectionLabel.uppercase(),
                                        color = LyricsVolt,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 20.dp, bottom = 6.dp)
                                            .onGloballyPositioned { coords ->
                                                linePositions[index] = coords.positionInParent().y
                                            }
                                    )
                                    else -> Text(
                                        text = line,
                                        color = LyricsWhite,
                                        fontSize = 26.sp,
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                linePositions[index] = coords.positionInParent().y
                                            }
                                    )
                                }
                            }
                            // Platzhalter am Ende, damit die Zeilen des letzten Abschnitts
                            // noch bis zum oberen Rand hochscrollen können. Sein Oberrand
                            // (Index lines.size) dient als Sentinel für readingPixel(),
                            // damit auch die allerletzte Zeile eine gemessene Unterkante hat.
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .onGloballyPositioned { coords ->
                                        linePositions[lines.size] = coords.positionInParent().y
                                    }
                            )
                        }
                    }
                ) { measurables, constraints ->
                    viewportHeightPx = constraints.maxHeight.toFloat()
                    val contentConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                    val placeable = measurables.first().measure(contentConstraints)
                    contentHeightPx = placeable.height.toFloat()
                    // Oben-Anker (Abschnitts-Modell, Sprint 5.46): Inhalt wird ab dem
                    // Viewport-Oberrand platziert. Bei scrollOffsetPx == Pixel eines
                    // Abschnitts-Anfangs steht dieser damit exakt oben — genau das, was
                    // die Kalibrierung anpeilt (Abschnitt erreicht zu seinem Zeitpunkt den
                    // oberen Rand). Reine Platzierung, ändert nichts an scrollOffsetPx
                    // selbst (Rate-Berechnung pro Segment, Monoton-Klemmung).
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(0, -scrollOffsetPx.roundToInt())
                    }
                }
            }
        }
    }
}
