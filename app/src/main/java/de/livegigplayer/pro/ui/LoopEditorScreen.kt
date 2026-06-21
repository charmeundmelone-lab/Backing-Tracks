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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

private val LeBg      = Color(0xFF0A0A0A)
private val LeCard    = Color(0xFF1A1A1A)
private val LeHeader  = Color(0xFF1E293B)
private val LeVolt    = Color(0xFFE8FF00)
private val LeWhite   = Color(0xFFFFFFFF)
private val LeGray    = Color(0xFF777777)
private val LeGreen   = Color(0xFF00FF88)
private val LeRed     = Color(0xFFFF4444)
private val LeWave    = Color(0xFF4A80C0)
private val LeOverlay = Color(0x2200FF88)
private val LeOnset   = Color(0x60E8FF00)
private val LeBgTrack = Color(0xFF2A2A2A)

private enum class DragTarget { NONE, START, END, LOOP }

@Composable
fun LoopEditorScreen(song: Song, onClose: () -> Unit) {

    val vm: LoopEditorViewModel = viewModel(
        key     = "loopEditor_${song.id}",
        factory = LoopEditorViewModel.factory(song)
    )

    val density       = LocalDensity.current
    val scope         = rememberCoroutineScope()
    val snackbar      = remember { SnackbarHostState() }

    val wfState       by vm.waveformState.collectAsState()
    val loopStartMs   by vm.startMs.collectAsState()
    val loopEndMs     by vm.endMs.collectAsState()
    val firstTapMs    by vm.firstTapMs.collectAsState()
    val isAuditioning by vm.isAuditioning.collectAsState()

    var scale   by remember { mutableFloatStateOf(5f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) { onDispose { vm.stopAudition() } }

    LaunchedEffect(wfState.durationMs) {
        val dur = wfState.durationMs
        if (dur > 0L) {
            scale   = (dur.toFloat() / 12_000f).coerceIn(3f, 100f)
            offsetX = 0f
        }
    }

    LaunchedEffect(loopStartMs, loopEndMs) {
        delay(200)
        vm.refreshAudition()
    }

    val effectiveDuration = wfState.durationMs.coerceAtLeast(1L)
    val hasLoop           = loopEndMs > loopStartMs + LoopEditorViewModel.MIN_LOOP_MS
    val tapToCreate       = !hasLoop || firstTapMs >= 0L

    // Scaffold handles window insets via `pad` — no safeDrawingPadding() here
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = LeBg) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LeBg)
                .padding(pad)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeHeader)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.stopAudition(); onClose() }) {
                    Icon(Icons.Filled.Close, null, tint = LeGray)
                }
                Text(
                    text = "Loop: ${song.title}", color = LeWhite,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    if (!hasLoop) return@IconButton
                    vm.stopAudition(); vm.save()
                    scope.launch {
                        snackbar.showSnackbar("Loop gespeichert", duration = SnackbarDuration.Short)
                        delay(700); onClose()
                    }
                }) {
                    Icon(Icons.Filled.Check, null, tint = if (hasLoop) LeVolt else LeGray)
                }
            }

            // ── Waveform ──────────────────────────────────────────────────────
            when {
                wfState.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LeVolt)
                        Spacer(Modifier.height(12.dp))
                        Text("Analysiere Wellenform…", color = LeGray, fontSize = 13.sp)
                    }
                }
                wfState.errorMsg != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = wfState.errorMsg!!,
                        color = LeRed, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else ->
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val canvasW      = constraints.maxWidth.toFloat()
                    val canvasH      = constraints.maxHeight.toFloat()
                    val handleZonePx = with(density) { 44.dp.toPx() }
                    val knobR        = with(density) { 10.dp.toPx() }
                    val tapThreshPx  = with(density) { 8.dp.toPx() }

                    fun naturalX(ms: Long)   = ms.toFloat() / effectiveDuration * canvasW
                    fun msToScreen(ms: Long) = naturalX(ms) * scale + offsetX
                    fun screenToMs(x: Float) =
                        ((x - offsetX) / scale.coerceAtLeast(0.001f) / canvasW * effectiveDuration)
                            .roundToLong().coerceIn(0L, effectiveDuration)
                    fun clampOffset(s: Float = scale, dx: Float) =
                        dx.coerceIn(canvasW * (1f - s), 0f)
                    fun snapToOnset(ms: Long): Long {
                        val thresh = (effectiveDuration / scale / canvasW * handleZonePx)
                            .roundToLong().coerceAtLeast(50L)
                        return wfState.onsets
                            ?.minByOrNull { abs(it - ms) }
                            ?.takeIf { abs(it - ms) <= thresh } ?: ms
                    }

                    val latestTapToCreate by rememberUpdatedState(tapToCreate)
                    val latestStartMs     by rememberUpdatedState(loopStartMs)
                    val latestEndMs       by rememberUpdatedState(loopEndMs)

                    Box(Modifier.fillMaxSize()) {

                        // ── Canvas ────────────────────────────────────────────
                        // Single unified gesture handler — no competing pointerInput blocks.
                        // Priority: 2-finger → pinch-zoom | 1-finger on marker → drag |
                        //           1-finger off marker → pan | tap → tap-to-create
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(canvasW, effectiveDuration) {
                                    awaitEachGesture {
                                        val down   = awaitFirstDown(requireUnconsumed = false)
                                        val touchX = down.position.x
                                        val touchY = down.position.y

                                        val sX = msToScreen(latestStartMs)
                                        val eX = msToScreen(latestEndMs)
                                        val dragTarget = when {
                                            latestTapToCreate                -> DragTarget.NONE
                                            abs(touchX - sX) < handleZonePx -> DragTarget.START
                                            abs(touchX - eX) < handleZonePx -> DragTarget.END
                                            touchX > sX && touchX < eX      -> DragTarget.LOOP
                                            else                             -> DragTarget.NONE
                                        }

                                        var prevSpan = 0f
                                        var didZoom  = false
                                        var didDrag  = false
                                        var maxMove  = 0f

                                        do {
                                            val event   = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }

                                            if (pressed.size >= 2) {
                                                // ── Pinch-to-zoom + 2-finger pan ──────────────
                                                val p1   = pressed[0]; val p2 = pressed[1]
                                                val span = abs(p2.position.x - p1.position.x)
                                                val cx   = (p1.position.x  + p2.position.x)  / 2f
                                                val panX = ((p1.position.x  + p2.position.x) -
                                                             (p1.previousPosition.x + p2.previousPosition.x)) / 2f
                                                if (prevSpan > 0f) {
                                                    val factor   = span / prevSpan.coerceAtLeast(1f)
                                                    val anchorMs = screenToMs(cx)
                                                    val newScale = (scale * factor).coerceIn(1f, 100f)
                                                    scale   = newScale
                                                    offsetX = clampOffset(newScale,
                                                        cx - naturalX(anchorMs) * newScale + panX)
                                                    didZoom = true
                                                }
                                                prevSpan = span
                                                pressed.forEach { it.consume() }
                                            } else {
                                                prevSpan = 0f
                                                if (pressed.size == 1) {
                                                    val p     = pressed[0]
                                                    val dx    = p.position.x - p.previousPosition.x
                                                    val msPpx = effectiveDuration.toFloat() /
                                                        scale.coerceAtLeast(0.001f) / canvasW
                                                    maxMove = maxOf(maxMove,
                                                        abs(p.position.x - touchX),
                                                        abs(p.position.y - touchY))

                                                    when {
                                                        // ── Marker drag (highest priority) ────
                                                        dragTarget == DragTarget.START && !didZoom -> {
                                                            vm.setLoopStart(latestStartMs + (dx * msPpx).roundToLong())
                                                            didDrag = true
                                                        }
                                                        dragTarget == DragTarget.END && !didZoom -> {
                                                            vm.setLoopEnd(latestEndMs + (dx * msPpx).roundToLong(), effectiveDuration)
                                                            didDrag = true
                                                        }
                                                        dragTarget == DragTarget.LOOP && !didZoom -> {
                                                            val len = latestEndMs - latestStartMs
                                                            val ns  = (latestStartMs + (dx * msPpx).roundToLong())
                                                                .coerceIn(0L, effectiveDuration - len)
                                                            vm.setLoopBoth(ns, ns + len, effectiveDuration)
                                                            didDrag = true
                                                        }
                                                        // ── Pan (background or post-zoom) ─────
                                                        else -> offsetX = clampOffset(dx = offsetX + dx)
                                                    }
                                                    p.consume()
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })

                                        // ── Tap-to-create ─────────────────────────────────────
                                        val isTap = maxMove < tapThreshPx && !didDrag && !didZoom
                                        if (isTap && latestTapToCreate) {
                                            vm.tapCanvas(screenToMs(touchX))
                                        }
                                        // Snap to onset on marker release
                                        if (didDrag && !didZoom) when (dragTarget) {
                                            DragTarget.START -> vm.setLoopStart(snapToOnset(latestStartMs))
                                            DragTarget.END   -> vm.setLoopEnd(snapToOnset(latestEndMs), effectiveDuration)
                                            else             -> {}
                                        }
                                    }
                                }
                        ) {
                            val mid = canvasH / 2f
                            drawRect(LeBg, size = size)

                            // Waveform: max 800 downsampled points → smooth Path, never a freeze
                            val samples = wfState.samples
                            if (samples != null && samples.isNotEmpty()) {
                                val n          = samples.size
                                val closedPath = Path()
                                val topPath    = Path()
                                val botPath    = Path()
                                for (i in samples.indices) {
                                    val sx = msToScreen(i.toLong() * effectiveDuration / n)
                                    val h  = samples[i] * mid * 0.80f
                                    if (i == 0) {
                                        closedPath.moveTo(sx, mid - h)
                                        topPath.moveTo(sx, mid - h)
                                        botPath.moveTo(sx, mid + h)
                                    } else {
                                        closedPath.lineTo(sx, mid - h)
                                        topPath.lineTo(sx, mid - h)
                                        botPath.lineTo(sx, mid + h)
                                    }
                                }
                                for (i in samples.indices.reversed()) {
                                    val sx = msToScreen(i.toLong() * effectiveDuration / n)
                                    closedPath.lineTo(sx, mid + samples[i] * mid * 0.80f)
                                }
                                closedPath.close()
                                drawPath(closedPath, LeWave.copy(alpha = 0.28f))
                                drawPath(topPath, LeWave, style = Stroke(width = 1.5f))
                                drawPath(botPath, LeWave.copy(alpha = 0.50f), style = Stroke(width = 1f))
                            } else {
                                drawLine(LeGray, Offset(0f, mid), Offset(canvasW, mid), 1f)
                            }

                            drawLine(LeGray.copy(alpha = 0.18f), Offset(0f, mid), Offset(canvasW, mid), 1f)

                            // Onset ticks
                            wfState.onsets?.forEach { ms ->
                                val x = msToScreen(ms)
                                if (x < 0f || x > canvasW) return@forEach
                                drawLine(LeOnset, Offset(x, 0f), Offset(x, canvasH * 0.28f), 1.5f)
                                drawLine(LeOnset, Offset(x, canvasH * 0.72f), Offset(x, canvasH), 1.5f)
                            }

                            // Markers
                            if (firstTapMs >= 0L) {
                                val ftX = msToScreen(firstTapMs)
                                drawLine(LeGreen.copy(alpha = 0.75f), Offset(ftX, 0f), Offset(ftX, canvasH), 2f)
                                if (ftX in 0f..canvasW) {
                                    drawCircle(LeGreen, knobR, Offset(ftX, mid))
                                    drawCircle(LeBg, knobR * 0.45f, Offset(ftX, mid))
                                }
                            } else if (hasLoop) {
                                val lsx = msToScreen(loopStartMs)
                                val lex = msToScreen(loopEndMs)
                                val ox  = lsx.coerceAtLeast(0f)
                                val ow  = (lex.coerceAtMost(canvasW) - ox).coerceAtLeast(0f)
                                if (ow > 0f) drawRect(LeOverlay, Offset(ox, 0f), Size(ow, canvasH))

                                drawLine(LeGreen, Offset(lsx, 0f), Offset(lsx, canvasH), 2.5f)
                                if (lsx > -knobR && lsx < canvasW + knobR) {
                                    val kx = lsx.coerceIn(knobR, canvasW - knobR)
                                    drawCircle(LeGreen, knobR, Offset(kx, mid))
                                    drawCircle(LeBg, knobR * 0.42f, Offset(kx, mid))
                                }
                                if (lsx < 0f) drawRect(
                                    LeGreen.copy(alpha = 0.7f), Offset(0f, mid - 18f), Size(5f, 36f))

                                drawLine(LeRed, Offset(lex, 0f), Offset(lex, canvasH), 2.5f)
                                if (lex > -knobR && lex < canvasW + knobR) {
                                    val kx = lex.coerceIn(knobR, canvasW - knobR)
                                    drawCircle(LeRed, knobR, Offset(kx, mid))
                                    drawCircle(LeBg, knobR * 0.42f, Offset(kx, mid))
                                }
                                if (lex > canvasW) drawRect(
                                    LeRed.copy(alpha = 0.7f), Offset(canvasW - 5f, mid - 18f), Size(5f, 36f))
                            }
                        }

                        // Tap-to-create hint — kein pointerInput, Touches gehen durch zum Canvas
                        if (tapToCreate) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (firstTapMs >= 0L) "2. Tap: Loop-Ende"
                                           else "1. Tap: Loop-Start",
                                    color = LeVolt.copy(alpha = 0.72f), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } // end when

            // ── Vorhör — komplett isoliert von Canvas-Gestenlogik ─────────────
            val canAudition = !wfState.isLoading && wfState.auditionUri.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeBg)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isAuditioning) "◼  STOP" else "▶  VORHÖR",
                    color = when {
                        !canAudition  -> LeGray
                        isAuditioning -> LeRed
                        else          -> LeGreen
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(
                            when {
                                !canAudition  -> Color(0x22777777)
                                isAuditioning -> Color(0x33FF4444)
                                else          -> Color(0x3300FF88)
                            }, MaterialTheme.shapes.small
                        )
                        .then(if (canAudition) Modifier.clickable { vm.toggleAudition() } else Modifier)
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                )
            }

            // ── Fine-Tune — komplett isoliert von Canvas-Gestenlogik ──────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LeTimeLabel("START", loopStartMs, LeGreen)
                    LeTimeLabel("LÄNGE", (loopEndMs - loopStartMs).coerceAtLeast(0L), LeVolt)
                    LeTimeLabel("ENDE",  loopEndMs, LeRed)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LeFineTune("← Start", LeGreen, Modifier.weight(1f)) { vm.nudgeStart(-10L) }
                    LeFineTune("Start →", LeGreen, Modifier.weight(1f)) { vm.nudgeStart(+10L) }
                    Spacer(Modifier.width(4.dp))
                    LeFineTune("← Ende",  LeRed,   Modifier.weight(1f)) { vm.nudgeEnd(-10L, effectiveDuration) }
                    LeFineTune("Ende →",  LeRed,   Modifier.weight(1f)) { vm.nudgeEnd(+10L, effectiveDuration) }
                }
            }
        }
    }
}

@Composable
private fun LeTimeLabel(label: String, ms: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LeGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(leFmtMs(ms), color = color, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LeFineTune(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(LeBgTrack, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    )
}

private fun leFmtMs(ms: Long): String {
    val m = ms / 60_000L; val s = ms % 60_000L / 1_000L; val r = ms % 1_000L
    return "%d:%02d.%03d".format(m, s, r)
}
