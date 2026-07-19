package de.livegigplayer.pro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "LyricsOverlay"

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

// "mm:ss" (song.duration, z.B. "5:22") → ms. Diese beim Import einmalig über
// MediaMetadataRetriever gemessene Dauer ist die verlässlichere Quelle als die
// live von ExoPlayer gemeldete durationMs: AudioEngine.durationMs fragt bei
// Multitrack-Songs `tracks.firstOrNull()` ab, ohne den Click-Track auszuschließen
// — FolderImporter tut das für song.duration bewusst (siehe "Bug-Fix 2"-Kommentar
// dort), weil einzelne Stems (v.a. Click) in der Vergangenheit falsche/kurze
// Längen geliefert haben. Eine zu kurze durationMs lässt die Scroll-Rate im
// Frame-Loop explodieren (siehe LaunchedEffect unten).
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
 *    ein Tap pro Abschnittswechsel (Intro, Vers, Chorus, …). Jeder Tap speichert
 *    (Zeilen-Index, Wiedergabeposition) als Kalibrierungspunkt. Danach läuft der
 *    Scroll bei jedem künftigen Play abschnittsweise mit konstanter, aus den
 *    gespeicherten Punkten interpolierter Geschwindigkeit — kein Live-Tippen
 *    mehr nötig. Siehe Gotcha 12.
 * 2. **Live-Tap-to-Sync** (Tap irgendwo im Textbereich, außerhalb Kalibrierung):
 *    einmalige, NICHT gespeicherte Korrektur für die laufende Wiedergabe.
 *
 * Scroll bewegt sich in beiden Fällen ausschließlich vorwärts/abwärts (siehe
 * targetScrollPx-Klemmung in LyricsContent) — niemals zurück.
 */
@Composable
fun LyricsOverlay(
    visible: Boolean,
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSetLyricsSyncPoints: (Long, String) -> Unit = { _, _ -> }
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
                onSetLyricsSyncPoints = onSetLyricsSyncPoints
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
    onSetLyricsSyncPoints: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val lines   = remember(song.id, song.lyrics) { song.lyrics.lines() }
    // Gespeicherte Kalibrierungspunkte dieses Songs, sortiert nach Position.
    val breakpoints = remember(song.id, song.lyricsSyncPoints) { parseSyncPoints(song.lyricsSyncPoints) }

    // Wie der übrige Scroll-Zustand mit openSession gekeyt (statt rememberScrollState()),
    // damit auch die rohe Scroll-Position bei jedem Öffnen bei 0 startet.
    val scrollState   = remember(song.id, openSession) { ScrollState(0) }
    val scope         = rememberCoroutineScope()
    // index -> gemessene Y-Position (px) innerhalb der scrollbaren Column. Wie der
    // gesamte Scroll-Zustand unten mit openSession gekeyt: jedes Öffnen des Screens
    // startet komplett frisch, statt einen eventuell verkorksten Zustand aus einer
    // vorherigen Session (z.B. nach einem Bug in einer älteren Version) mitzuschleppen.
    val linePositions = remember(song.id, openSession) { mutableMapOf<Int, Float>() }

    // Kalibrierungs-Modus: Record-Button an → jeder Tap wird zusätzlich zum
    // Live-Sync in calibrationPoints aufgezeichnet und beim Beenden persistiert.
    var calibrating by remember(song.id, openSession) { mutableStateOf(false) }
    val calibrationPoints = remember(song.id, openSession) { mutableStateListOf<Pair<Int, Long>>() }

    fun persistCalibration() {
        if (calibrationPoints.isNotEmpty()) {
            onSetLyricsSyncPoints(song.id, serializeSyncPoints(calibrationPoints))
        }
    }

    // Segment-Anker (Zeit, Scroll-Px), ab dem die aktuelle Scroll-Rate berechnet
    // wird. Schaltet automatisch durch die Kalibrierungspunkte weiter, sobald die
    // Wiedergabe sie erreicht (siehe LaunchedEffect unten) — ohne Kalibrierung
    // bleibt es bei (0, 0), also reine Positions-Proportion wie zuvor.
    var anchorPositionMs by remember(song.id, openSession) { mutableStateOf(0L) }
    var anchorScrollPx   by remember(song.id, openSession) { mutableStateOf(0f) }
    // Zuletzt gesetztes Scroll-Ziel — wird NIE verringert (Anforderung: nur abwärts, nie zurück).
    var targetScrollPx   by remember(song.id, openSession) { mutableStateOf(0f) }

    // Die verlässlichere der beiden Dauer-Quellen: siehe parseDurationString(). Das
    // Maximum aus beiden zu nehmen schützt in beide Richtungen — welche der beiden
    // auch zu kurz melden sollte, die längere gewinnt und verhindert die Explosion
    // der Scroll-Rate.
    val dbDurationMs = remember(song.id, song.duration) { parseDurationString(song.duration) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestDurationMs by rememberUpdatedState(maxOf(durationMs, dbDurationMs))

    // Kontinuierlicher Frame-für-Frame-Scroll. Rate wird pro Segment (zwischen
    // zwei aufeinanderfolgenden Kalibrierungspunkten, bzw. Songanfang/-ende als
    // Rand) neu berechnet — dadurch abschnittsweise konstante Geschwindigkeit
    // statt einer einzigen globalen Rate für den ganzen Song.
    LaunchedEffect(song.id, openSession, song.lyricsSyncPoints) {
        Log.d(TAG, "Lyrics-Loop start: song='${song.title}' liveDurationMsParam=$durationMs " +
            "dbDurationMs=$dbDurationMs (song.duration='${song.duration}') breakpoints=$breakpoints")
        var nextIdx = 0
        var guardBlockLogged = false
        var bigJumpLogCount = 0
        while (isActive) {
            withFrameNanos { }
            val pos = latestPositionMs
            val dur = latestDurationMs
            val max = scrollState.maxValue

            // Anker automatisch durch bereits erreichte Kalibrierungspunkte weiterschalten.
            // Noch nicht vermessene Zeile (Layout-Timing-Race) → Schleife abbrechen und im
            // nächsten Frame erneut versuchen, statt den Punkt stillschweigend zu verlieren.
            while (nextIdx < breakpoints.size && breakpoints[nextIdx].second <= pos) {
                val (lineIdx, ms) = breakpoints[nextIdx]
                val px = linePositions[lineIdx] ?: break
                anchorPositionMs = ms
                anchorScrollPx   = px
                nextIdx++
            }

            // dur >= pos ist eine Plausibilitätsprüfung: durationMs kann beim Songwechsel
            // (Preload/Crossfade der A/B-Player in AudioEngine) für einen Frame noch einen
            // veralteten, zu kleinen Wert liefern, während positionMs schon weiterläuft. Ohne
            // diese Prüfung würde raw sofort auf max geklemmt (pos > dur → Rate viel zu hoch)
            // und blieb wegen der Monoton-Klemmung fälschlich für den Rest der Wiedergabe dort
            // hängen — noch bevor überhaupt ein Kalibrierungs-Tap ankommen konnte.
            if (dur in 1..<pos && !guardBlockLogged) {
                guardBlockLogged = true
                Log.w(TAG, "Guard blockiert: dur=$dur < pos=$pos (max=$max) — durationMs war zu klein")
            }
            if (dur > anchorPositionMs && dur >= pos && max > 0) {
                val nextBreak = breakpoints.getOrNull(nextIdx)
                val segEndMs  = nextBreak?.second ?: dur
                // Bei einem noch nicht vermessenen Zwischen-Ziel (Layout-Timing-Race beim
                // Öffnen) NICHT auf "volle Scroll-Länge" ausweichen — sonst würde ein
                // Zwischenpunkt fälschlich wie das Songende behandelt (Rate schießt hoch)
                // und bleibt wegen der Monoton-Klemmung dauerhaft hängen. Stattdessen: in
                // diesem Frame einfach nichts aktualisieren, nächster Frame versucht's erneut.
                val segEndPx: Float? = if (nextBreak == null) max.toFloat() else linePositions[nextBreak.first]
                if (segEndPx != null) {
                    val rate = if (segEndMs > anchorPositionMs)
                        (segEndPx - anchorScrollPx) / (segEndMs - anchorPositionMs).toFloat() else 0f
                    val raw     = anchorScrollPx + rate * (pos - anchorPositionMs).toFloat()
                    val clamped = raw.coerceIn(0f, max.toFloat())
                    if (clamped > targetScrollPx) {
                        val jump = clamped - targetScrollPx
                        if (jump > 30f && bigJumpLogCount < 10) {
                            bigJumpLogCount++
                            Log.w(TAG, "Großer Scroll-Sprung: +${jump}px in einem Frame " +
                                "(pos=$pos dur=$dur anchor=($anchorPositionMs,$anchorScrollPx) " +
                                "segEnd=($segEndMs,$segEndPx) rate=$rate max=$max)")
                        }
                        targetScrollPx = clamped
                    }
                }
            }
            if (targetScrollPx.roundToInt() != scrollState.value) {
                scrollState.scrollTo(targetScrollPx.roundToInt())
            }
        }
    }

    // Ein Handler für beide Tap-Arten: sucht die nächste noch nicht erreichte
    // Zeile (Header oder Lyric, macht keinen Unterschied), synct live darauf —
    // und zeichnet den Punkt zusätzlich auf, wenn gerade kalibriert wird.
    fun handleTap() {
        val entry = linePositions.entries
            .filter { it.value > targetScrollPx + 4f }
            .minByOrNull { it.value } ?: return
        val (lineIdx, linePx) = entry.key to entry.value
        anchorPositionMs = latestPositionMs
        anchorScrollPx   = linePx
        if (linePx > targetScrollPx) targetScrollPx = linePx
        scope.launch { scrollState.animateScrollTo(targetScrollPx.roundToInt()) }
        if (calibrating) calibrationPoints.add(lineIdx to latestPositionMs)
    }

    Box(modifier = Modifier.fillMaxSize().background(LyricsBg)) {
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
                        Toast.makeText(context, "Kalibrierung läuft — bei jedem Abschnittswechsel tippen", Toast.LENGTH_SHORT).show()
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
                    if (calibrating) { calibrating = false; persistCalibration() }
                    onClose()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = LyricsGray)
                }
            }

            if (calibrating) {
                Text(
                    "● Kalibrierung läuft — ${calibrationPoints.size} Punkte — bei jedem Abschnittswechsel tippen",
                    color = LyricsRed, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Lyrics-Bereich — Tap synct auf die nächste noch nicht erreichte Zeile
            // (live, oder als Kalibrierungspunkt gespeichert, siehe handleTap()).
            // Manuelles Drag-Scrollen ist bewusst deaktiviert (enabled = false), sonst
            // könnte man aus Versehen zurückscrollen — das darf laut Anforderung nie passieren.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = false)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { handleTap() })
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    val sectionLabel = sectionTagRegex.find(line)?.groupValues?.get(1)
                    when {
                        line.isBlank() -> Spacer(modifier = Modifier.height(24.dp))
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
                // Platzhalter am Ende, damit auch die letzte Zeile bis ganz oben scrollen kann.
                Spacer(modifier = Modifier.height(240.dp))
            }
        }
    }
}
