package de.livegigplayer.pro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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

// Sucht das nächste [Struktur-Label] rückwärts ab lineIdx (inklusive). Wird vom
// Countdown-Balken oben genutzt, um "Chorus"/"Verse 1" o.ä. anzuzeigen. Falls
// vor lineIdx kein Label existiert → leer.
private fun sectionLabelAt(lines: List<String>, lineIdx: Int): String {
    var i = lineIdx.coerceAtMost(lines.size - 1)
    while (i >= 0) {
        val m = sectionTagRegex.find(lines[i].trim())
        if (m != null) return m.groupValues[1]
        i--
    }
    return ""
}

// Formatiert Restzeit für den Countdown-Balken: <10s mit 0.1s-Auflösung, sonst
// ganze Sekunden. Beispiel: 4200 → "4.2s", 15400 → "15s".
private fun formatCountdown(ms: Long): String {
    if (ms < 0) return "0s"
    return if (ms < 10_000) {
        val sec = ms / 1000
        val tenth = (ms % 1000) / 100
        "${sec}.${tenth}s"
    } else {
        "${ms / 1000}s"
    }
}

// Textbasierte Instrumental-Erkennung (ersetzt eine frühere Zeit-Schwelle):
// bei sparse Kalibrierung — ein Tap pro Abschnitt statt pro Zeile — dauert eine
// normale Strophe oft länger als jede sinnvolle Zeit-Schwelle, eine reine
// Zeit-Lücke ist daher kein verlässliches Signal. Ein Segment zwischen zwei
// Kalibrier-Punkten gilt als Instrumental, wenn in diesem Zeilenbereich KEINE
// einzige echte Songtext-Zeile liegt (nur Leerzeilen/[Label]-Zeilen) — nutzt
// die ohnehin geltende Text-Konvention aus, unabhängig von Kalibrier-Dichte
// oder tatsächlicher Abschnittsdauer.
private fun segmentIsInstrumental(lines: List<String>, fromLine: Int, toLine: Int): Boolean {
    var i = fromLine.coerceAtLeast(0)
    val end = toLine.coerceAtMost(lines.size)
    while (i < end) {
        val line = lines[i].trim()
        if (line.isNotEmpty() && sectionTagRegex.find(line) == null) return false
        i++
    }
    return true
}

// Berechnet den Ziel-Scroll-Offset in Pixeln aus der aktuellen Wiedergabeposition
// und den kalibrierten Sync-Punkten (Plan Section 6.2 / 6.4). Rein deklarativ —
// KEIN Frame-Loop, KEIN Anker-State. Ergebnis wird über animateFloatAsState
// geglättet, damit die 200ms-Position-Ticks in 60fps-Scroll umgesetzt werden.
private fun computeTargetOffsetPx(
    positionMs: Long,
    breakpoints: List<Pair<Int, Long>>,
    lines: List<String>,
    linePositions: Map<Int, Float>,
    maxScrollPx: Float,
    readpointY: Float
): Float {
    if (breakpoints.isEmpty()) return 0f
    val currentBpIdx = breakpoints.indexOfLast { it.second <= positionMs }
    val currentBp = if (currentBpIdx >= 0) breakpoints[currentBpIdx] else null
    val nextBp = if (currentBpIdx + 1 < breakpoints.size) breakpoints[currentBpIdx + 1] else null

    // Vor dem ersten Kalibrier-Punkt: Scroll bleibt bei 0 (Song-Anfang, Balken oben
    // zeigt Countdown zur ersten Vocal-Zeile).
    if (currentBp == null) return 0f

    val currentY = linePositions[currentBp.first] ?: return 0f

    // Nach dem letzten Kalibrier-Punkt: letzte Zeile am Lesepunkt einfrieren.
    if (nextBp == null) return (currentY - readpointY).coerceIn(0f, maxScrollPx)

    val nextY = linePositions[nextBp.first] ?: return (currentY - readpointY).coerceIn(0f, maxScrollPx)
    val gap = nextBp.second - currentBp.second
    if (gap <= 0L) return (currentY - readpointY).coerceIn(0f, maxScrollPx)

    val targetY: Float = if (segmentIsInstrumental(lines, currentBp.first, nextBp.first)) {
        // Instrumental-Passage — Scroll steht komplett still bis zum exakten Start
        // der nächsten Zeile. Kein Vorab-Gleiten: sobald positionMs den Breakpoint
        // erreicht, schaltet currentBp automatisch weiter (siehe oben) — die
        // animateFloatAsState-Glättung im Renderer macht daraus einen sanften Sprung.
        currentY
    } else {
        // Vocal — kontinuierlicher linearer Scroll durch das Segment.
        val t = ((positionMs - currentBp.second).toFloat() / gap).coerceIn(0f, 1f)
        currentY + t * (nextY - currentY)
    }
    return (targetY - readpointY).coerceIn(0f, maxScrollPx)
}

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
 * gekoppelt an die echte Wiedergabeposition.
 *
 * **TabSync-Kalibrierung** (Record-Button im Header): bei jeder Zeile tippen,
 * sobald man sie singt. Jeder Tap speichert (Zeilen-Index, Wiedergabeposition)
 * in Reihenfolge — robust, weil der Song immer vorwärts läuft. Instrumental-
 * Passagen werden NICHT getippt: `segmentIsInstrumental()` erkennt sie
 * automatisch textbasiert (kein Songtext zwischen zwei Kalibrier-Punkten).
 *
 * **Rendering:** rein deklarativ aus `positionMs` + Kalibrier-Punkten
 * (`computeTargetOffsetPx()`), kein Frame-Loop. Die zu singende Zeile landet
 * am literalen oberen Bildschirmrand (Top-Anchor); innerhalb eines Vocal-
 * Segments läuft der Scroll linear zur nächsten Zeile, bei Instrumental steht
 * er still bis zum exakten nächsten Einsatz. `animateFloatAsState(tween(200))`
 * glättet die 200ms-Position-Ticks zu 60fps.
 *
 * Eigenes `Layout` (statt `ScrollState`) misst den Text-Block explizit mit
 * `maxHeight = Constraints.Infinity` und platziert ihn selbst per
 * `placeRelative(0, -animatedOffsetPx…)`.
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

    // Musiker-kritisch: Bildschirm darf während Overlay-Anzeige NICHT einschlafen.
    // Sonst reißt der Auto-Sleep den Text während einer langen Instrumental-Passage
    // weg — der User müsste live mit der Gitarrenhand aufwecken. Flag wird beim
    // Schließen wieder entfernt (Batterie-Schutz für den Rest der App).
    DisposableEffect(visible) {
        val window = activity?.window
        if (visible) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (visible) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    // Echte Textzeile (kein Label, nicht leer) — Tap-Ziele beim Kalibrieren.
    val lineIsLyric = remember(lines) {
        lines.map { it.isNotBlank() && sectionTagRegex.find(it.trim()) == null }
    }

    // Gespeicherte Kalibrierungspunkte dieses Songs, sortiert nach Position.
    val savedBreakpoints = remember(song.id, song.lyricsSyncPoints) { parseSyncPoints(song.lyricsSyncPoints) }

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

    // Während der Kalibrierung treiben die frisch getippten Punkte den Scroll live —
    // optische Referenz: die gerade getippte Zeile springt sofort an den oberen Rand.
    // Nach dem Beenden übernehmen die gespeicherten Punkte wieder normal.
    val breakpoints = if (calibrating) calibrationPoints.sortedBy { it.second } else savedBreakpoints

    // Zusätzliche Absicherung gegen eine zu kurze live gemeldete durationMs.
    val dbDurationMs = remember(song.id, song.duration) { parseDurationString(song.duration) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestDurationMs by rememberUpdatedState(maxOf(durationMs, dbDurationMs))

    // Auto-Close mit Fade-out bei Song-Ende: sobald die Wiedergabe sicher am Ende ist
    // (letzter Kalibrier-Punkt + Puffer erreicht ODER durationMs überschritten), löst
    // dieser LaunchedEffect onClose() aus. AnimatedVisibility im Wrapper übernimmt
    // automatisch den Fade-out. Beim Songwechsel wird der Effect neu gekeyt und
    // startet frisch — kein voreiliges Schließen bei Auto-Advance.
    val lastBreakpointMs = remember(savedBreakpoints) {
        savedBreakpoints.maxByOrNull { it.second }?.second ?: 0L
    }
    val onCloseLatest by rememberUpdatedState(onClose)
    LaunchedEffect(song.id, openSession) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val posNow = latestPositionMs
            val durNow = latestDurationMs
            val endThreshold = maxOf(durNow, lastBreakpointMs + 5000L)
            if (durNow > 0 && posNow > endThreshold) {
                onCloseLatest()
                break
            }
        }
    }

    // Nächste zu tippende Zeile: erste Gesangszeile NACH dem zuletzt getippten
    // Index — robust nach reiner Reihenfolge, unabhängig von Scroll-Position oder
    // Layout-Messung (Layout-Timing-Race war in der Vorversion mehrfach Bug-Ursache).
    val lastTappedLineIdx = calibrationPoints.maxOfOrNull { it.first } ?: -1
    val nextLineToTap = lineIsLyric.indices.firstOrNull { it > lastTappedLineIdx && lineIsLyric[it] }

    // Tippen ist ausschließlich ein Kalibrier-Werkzeug — das neue Modell ist rein
    // deterministisch aus den Breakpoints berechnet, es gibt keine Laufzeit-Drift
    // mehr, die ein Live-Tap außerhalb der Kalibrierung korrigieren müsste.
    fun handleTap() {
        if (!calibrating) return
        val target = nextLineToTap ?: return
        calibrationPoints.add(target to positionMs)
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
                        Toast.makeText(context, "Kalibrierung: bei jeder Zeile tippen, sobald du sie singst. Instrumental-Teile werden automatisch erkannt.", Toast.LENGTH_LONG).show()
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
                    "● Kalibrierung läuft — ${calibrationPoints.size} Punkte — bei jeder Zeile tippen, sobald du sie singst",
                    color = LyricsRed, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // ── Countdown-Balken oben (Plan Section 6.3) ─────────────────────────
            // Zeigt beim aktuellen Instrumental "▶ CHORUS in 4.2s", bei Vocal das
            // laufende Sektions-Label. Rechnet direkt aus positionMs + breakpoints,
            // unabhängig vom Frame-Loop-Anker.
            run {
                val currentBp = breakpoints.lastOrNull { it.second <= positionMs }
                val nextBp: Pair<Int, Long>? = if (currentBp == null) {
                    breakpoints.firstOrNull()
                } else {
                    val idx = breakpoints.indexOf(currentBp)
                    breakpoints.getOrNull(idx + 1)
                }
                val isInstrumental = currentBp != null && nextBp != null &&
                    segmentIsInstrumental(lines, currentBp.first, nextBp.first)
                val beforeFirst = currentBp == null && nextBp != null

                val barText: String = when {
                    beforeFirst -> {
                        val lbl = sectionLabelAt(lines, nextBp!!.first).ifEmpty { "Start" }
                        "▶ ${lbl.uppercase()} in ${formatCountdown(nextBp.second - positionMs)}"
                    }
                    isInstrumental && nextBp != null -> {
                        val lbl = sectionLabelAt(lines, nextBp.first).ifEmpty { "weiter" }
                        "▶ ${lbl.uppercase()} in ${formatCountdown(nextBp.second - positionMs)}"
                    }
                    currentBp != null -> {
                        sectionLabelAt(lines, currentBp.first).uppercase()
                    }
                    else -> ""
                }
                val barColor = if (isInstrumental || beforeFirst) LyricsVolt else LyricsWhite

                if (barText.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = barText,
                            color = barColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Scroll-Berechnung: deklarativ aus positionMs + breakpoints. Die zu
            // singende Zeile landet am literalen oberen Bildschirmrand (Top-Anchor).
            val maxScrollPxCalc = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
            val targetOffsetPx = computeTargetOffsetPx(
                positionMs = positionMs,
                breakpoints = breakpoints,
                lines = lines,
                linePositions = linePositions,
                maxScrollPx = maxScrollPxCalc,
                readpointY = 0f
            )
            val animatedOffsetPx by animateFloatAsState(
                targetValue = targetOffsetPx,
                animationSpec = tween(200, easing = LinearEasing),
                label = "lyricsScroll"
            )

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
                                    else -> {
                                        val isNextToTap = calibrating && index == nextLineToTap
                                        val isTapped = calibrating && index <= lastTappedLineIdx
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coords ->
                                                    linePositions[index] = coords.positionInParent().y
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(30.dp)
                                                    .background(if (isNextToTap) LyricsVolt else Color.Transparent)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = line,
                                                color = if (isTapped) LyricsGray else LyricsWhite,
                                                fontSize = 26.sp,
                                                lineHeight = 36.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
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
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(0, -animatedOffsetPx.roundToInt())
                    }
                }
            }
        }
    }
}
