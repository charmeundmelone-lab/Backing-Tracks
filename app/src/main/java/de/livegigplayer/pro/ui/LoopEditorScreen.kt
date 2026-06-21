package de.livegigplayer.pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.audio.SongScanner
import de.livegigplayer.pro.audio.WaveformAnalyzer
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

// ── Palette (lokal, unabhängig von MainScreen) ────────────────────────────────
private val LeBg      = Color(0xFF0A0A0A)
private val LeCard    = Color(0xFF1A1A1A)
private val LeHeader  = Color(0xFF1E293B)
private val LeVolt    = Color(0xFFE8FF00)
private val LeWhite   = Color(0xFFFFFFFF)
private val LeGray    = Color(0xFF777777)
private val LeGreen   = Color(0xFF00FF88)
private val LeRed     = Color(0xFFFF4444)
private val LeWave    = Color(0xFF3A6EA8)
private val LeOverlay = Color(0x3300FF88)
private val LeOnset   = Color(0x50E8FF00)
private val LeBgTrack = Color(0xFF2A2A2A)

@Composable
fun LoopEditorScreen(
    song: Song,
    onSave: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var waveformData by remember { mutableStateOf<WaveformAnalyzer.WaveformData?>(null) }
    var isLoading    by remember { mutableStateOf(true) }

    var loopStartMs by remember(song.id) { mutableLongStateOf(song.loopStartMs) }
    var loopEndMs   by remember(song.id) { mutableLongStateOf(song.loopEndMs) }

    var zoomLevel         by remember { mutableFloatStateOf(1f) }
    var viewStartFraction by remember { mutableFloatStateOf(0f) }

    // Load waveform in IO background
    LaunchedEffect(song.id) {
        isLoading = true
        val mode = withContext(Dispatchers.IO) { SongScanner.scan(song, context) }
        val uri = when (mode) {
            is TrackMode.Multitrack ->
                mode.click ?: mode.drums ?: mode.bass ?: mode.keys ?: mode.vocals ?: mode.cue ?: ""
            is TrackMode.Legacy -> mode.filePath
        }
        val data = if (uri.isNotEmpty()) {
            withContext(Dispatchers.IO) { WaveformAnalyzer.analyze(context, uri) }
        } else null
        waveformData = data

        val dur = data?.durationMs ?: 0L
        if (loopEndMs <= loopStartMs) {
            val bpm = song.bpmExact.takeIf { it > 0f } ?: song.bpm.toFloat().coerceAtLeast(1f)
            val eightBarsMs = (8.0 * 4.0 * 60_000.0 / bpm).roundToLong()
            loopEndMs = eightBarsMs.coerceAtMost(dur.coerceAtLeast(10_000L))
        }
        isLoading = false
    }

    val effectiveDuration = (waveformData?.durationMs ?: 0L).coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxSize().background(LeBg)) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().background(LeHeader)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Abbrechen", tint = LeGray)
            }
            Text(
                text     = "Loop-Editor: ${song.title}",
                color    = LeWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { onSave(loopStartMs, loopEndMs) }) {
                Icon(Icons.Filled.Check, contentDescription = "Speichern", tint = LeVolt)
            }
        }

        // ── Waveform canvas ───────────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LeVolt)
                    Spacer(Modifier.height(12.dp))
                    Text("Analysiere Wellenform…", color = LeGray, fontSize = 13.sp)
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val canvasW    = constraints.maxWidth.toFloat()
                val canvasH    = constraints.maxHeight.toFloat()
                val handleZone = with(density) { 48.dp.toPx() }

                // ── Coordinate helpers ────────────────────────────────────────
                fun visMs()   = effectiveDuration.toDouble() / zoomLevel
                fun vsMs()    = (viewStartFraction * effectiveDuration).toLong()
                fun msToX(ms: Long)  = ((ms - vsMs()) / visMs() * canvasW).toFloat()
                fun xToMs(x: Float)  = (vsMs() + x / canvasW * visMs()).roundToLong()
                fun snapToOnset(ms: Long): Long {
                    val threshold = (visMs() / canvasW * handleZone).roundToLong().coerceAtLeast(50L)
                    return waveformData?.onsets?.minByOrNull { abs(it - ms) }
                        ?.takeIf { abs(it - ms) <= threshold } ?: ms
                }
                fun clampView() {
                    val max = (1f - 1f / zoomLevel).coerceAtLeast(0f)
                    viewStartFraction = viewStartFraction.coerceIn(0f, max)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canvasW, effectiveDuration) {
                            awaitEachGesture {
                                val down       = awaitFirstDown(requireUnconsumed = false)
                                val touchX     = down.position.x
                                val startHandleX = msToX(loopStartMs)
                                val endHandleX   = msToX(loopEndMs)
                                val nearStart  = abs(touchX - startHandleX) < handleZone
                                val nearEnd    = abs(touchX - endHandleX)   < handleZone

                                var prevSpan   = 0f
                                var nPointers  = 1

                                do {
                                    val event    = awaitPointerEvent()
                                    val pressed  = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        // ── Pinch zoom ──────────────────────
                                        val p1 = pressed[0]; val p2 = pressed[1]
                                        val span = abs(p2.position.x - p1.position.x)
                                        val cx   = (p1.position.x + p2.position.x) / 2f

                                        if (prevSpan > 0f && nPointers >= 2) {
                                            val scale     = span / prevSpan.coerceAtLeast(1f)
                                            val anchorMs  = xToMs(cx)
                                            zoomLevel     = (zoomLevel * scale).coerceIn(1f, 200f)
                                            val newVisMs  = effectiveDuration / zoomLevel
                                            val newVsMs   = anchorMs - (cx / canvasW * newVisMs).roundToLong()
                                            viewStartFraction = (newVsMs.toFloat() / effectiveDuration)
                                            clampView()
                                        }
                                        prevSpan  = span
                                        nPointers = 2
                                        pressed.forEach { it.consume() }
                                    } else if (pressed.size == 1) {
                                        val p      = pressed[0]
                                        val deltaX = p.position.x - p.previousPosition.x
                                        nPointers  = 1

                                        when {
                                            nearStart -> {
                                                val dms = (deltaX / canvasW * visMs()).roundToLong()
                                                loopStartMs = (loopStartMs + dms).coerceIn(0L, loopEndMs - 100L)
                                            }
                                            nearEnd -> {
                                                val dms = (deltaX / canvasW * visMs()).roundToLong()
                                                loopEndMs = (loopEndMs + dms).coerceIn(loopStartMs + 100L, effectiveDuration)
                                            }
                                            else -> {
                                                // Pan
                                                val df = deltaX / canvasW * (1f / zoomLevel)
                                                viewStartFraction -= df
                                                clampView()
                                            }
                                        }
                                        p.consume()
                                    }
                                } while (event.changes.any { it.pressed })

                                // Snap to nearest onset on release
                                if (nearStart) loopStartMs = snapToOnset(loopStartMs)
                                if (nearEnd)   loopEndMs   = snapToOnset(loopEndMs)
                            }
                        }
                ) {
                    val mid = canvasH / 2f

                    // Background
                    drawRect(LeBg, size = size)

                    // ── Waveform ──────────────────────────────────────────────
                    val samples = waveformData?.samples
                    if (!samples.isNullOrEmpty()) {
                        val n       = samples.size
                        val msPer   = effectiveDuration.toFloat() / n
                        val barW    = (canvasW / n * zoomLevel).coerceAtLeast(1f)
                        for (i in samples.indices) {
                            val x = msToX((i * msPer).toLong())
                            if (x < -barW || x > canvasW + barW) continue
                            val lineH = samples[i] * mid * 0.85f
                            drawLine(LeWave, Offset(x, mid - lineH), Offset(x, mid + lineH), barW)
                        }
                    } else {
                        // No audio: center line placeholder
                        drawLine(LeGray, Offset(0f, mid), Offset(canvasW, mid), 1f)
                        drawRect(Color(0x22777777), size = size)
                    }

                    // ── Onset markers ─────────────────────────────────────────
                    waveformData?.onsets?.forEach { ms ->
                        val x = msToX(ms)
                        if (x < 0f || x > canvasW) return@forEach
                        drawLine(LeOnset, Offset(x, 0f), Offset(x, canvasH), 1.5f)
                    }

                    // ── Loop overlay ──────────────────────────────────────────
                    val sx = msToX(loopStartMs)
                    val ex = msToX(loopEndMs)
                    if (ex > sx) {
                        drawRect(LeOverlay, topLeft = Offset(sx.coerceAtLeast(0f), 0f),
                            size = Size((ex - sx).coerceAtMost(canvasW - sx.coerceAtLeast(0f)), canvasH))
                    }

                    // ── Start handle (green) ──────────────────────────────────
                    val stroke = 3.dp.toPx()
                    val knobR  = 11.dp.toPx()
                    drawLine(LeGreen, Offset(sx, 0f), Offset(sx, canvasH), stroke)
                    drawCircle(LeGreen, knobR, Offset(sx, mid))
                    drawCircle(LeBg,    knobR * 0.5f, Offset(sx, mid))

                    // ── End handle (red) ──────────────────────────────────────
                    drawLine(LeRed, Offset(ex, 0f), Offset(ex, canvasH), stroke)
                    drawCircle(LeRed, knobR, Offset(ex, mid))
                    drawCircle(LeBg,  knobR * 0.5f, Offset(ex, mid))
                }
            }
        }

        // ── Fine-tune + Zeit-Anzeige ──────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().background(LeCard)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Zeitanzeige
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeLabel("START", loopStartMs, LeGreen)
                TimeLabel("LÄNGE", (loopEndMs - loopStartMs).coerceAtLeast(0L), LeVolt)
                TimeLabel("ENDE",  loopEndMs,   LeRed)
            }

            // Fine-tune Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FineTune("← Start", LeGreen, Modifier.weight(1f)) {
                    loopStartMs = (loopStartMs - 1L).coerceAtLeast(0L)
                }
                FineTune("Start →", LeGreen, Modifier.weight(1f)) {
                    loopStartMs = (loopStartMs + 1L).coerceAtMost(loopEndMs - 100L)
                }
                Spacer(Modifier.width(4.dp))
                FineTune("← Ende", LeRed, Modifier.weight(1f)) {
                    loopEndMs = (loopEndMs - 1L).coerceAtLeast(loopStartMs + 100L)
                }
                FineTune("Ende →", LeRed, Modifier.weight(1f)) {
                    loopEndMs = (loopEndMs + 1L).coerceAtMost(effectiveDuration)
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(label: String, ms: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LeGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text       = fmtMs(ms),
            color      = color,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun FineTune(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text       = label,
        color      = color,
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign  = TextAlign.Center,
        modifier   = modifier
            .background(LeBgTrack, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    )
}

private fun fmtMs(ms: Long): String {
    val m   = ms / 60_000L
    val s   = (ms % 60_000L) / 1_000L
    val ms3 = ms % 1_000L
    return "%d:%02d.%03d".format(m, s, ms3)
}
