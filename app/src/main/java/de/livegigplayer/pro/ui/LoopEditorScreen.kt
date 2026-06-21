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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

// ── Palette ───────────────────────────────────────────────────────────────────
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
fun LoopEditorScreen(
    song: Song,
    vm: PlayerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isAuditioning by vm.isAuditioning.collectAsState()
    val loopStartMs   by vm.leStartMs.collectAsState()
    val loopEndMs     by vm.leEndMs.collectAsState()
    val firstTapMs    by vm.leFirstTapMs.collectAsState()

    var waveformData by remember { mutableStateOf<WaveformAnalyzer.WaveformData?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var auditionUri  by remember { mutableStateOf("") }

    // Pure UI state — zoom / pan
    var scale   by remember { mutableFloatStateOf(5f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) { onDispose { vm.stopAudition() } }

    // Debounced audition refresh when loop points change
    LaunchedEffect(loopStartMs, loopEndMs) {
        delay(200)
        vm.refreshAuditionLoop(loopStartMs, loopEndMs)
    }

    // Init VM state + load waveform
    LaunchedEffect(song.id) {
        isLoading = true
        vm.initLoopEditor(song)

        val mode = withContext(Dispatchers.IO) { SongScanner.scan(song, context) }
        val uri = when (mode) {
            is TrackMode.Multitrack ->
                mode.click ?: mode.drums ?: mode.bass ?: mode.keys ?: mode.vocals ?: mode.cue ?: ""
            is TrackMode.Legacy -> mode.filePath
        }
        auditionUri = uri

        val cached = withContext(Dispatchers.IO) { WaveformAnalyzer.loadCache(context, song.id) }
        val data = cached ?: run {
            val analyzed = withContext(Dispatchers.IO) { WaveformAnalyzer.analyze(context, uri) }
            if (analyzed != null) withContext(Dispatchers.IO) {
                WaveformAnalyzer.saveCache(context, song.id, analyzed)
            }
            analyzed
        }
        waveformData = data

        val dur = data?.durationMs ?: 0L
        if (dur > 0L) {
            // Initial zoom: show ~12 s so transients are sharp
            scale   = (dur.toFloat() / 12_000f).coerceIn(3f, 100f)
            offsetX = 0f
        }
        // Default 8-bar loop if none is stored
        if (loopEndMs <= loopStartMs + PlayerViewModel.LE_MIN_MS && dur > 0L) {
            val bpm = song.bpmExact.takeIf { it > 0f } ?: song.bpm.toFloat().coerceAtLeast(1f)
            val eightBarsMs = (8.0 * 4.0 * 60_000.0 / bpm).roundToLong()
            vm.leSetBoth(0L, eightBarsMs.coerceAtMost(dur), dur)
        }
        isLoading = false
    }

    val effectiveDuration = (waveformData?.durationMs ?: 0L).coerceAtLeast(1L)
    val hasLoop           = loopEndMs > loopStartMs + PlayerViewModel.LE_MIN_MS
    val tapToCreate       = !hasLoop || firstTapMs >= 0L

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = LeBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LeBg)
                .safeDrawingPadding()
                .padding(innerPadding)
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
                    Icon(Icons.Filled.Close, contentDescription = "Abbrechen", tint = LeGray)
                }
                Text(
                    text = "Loop: ${song.title}",
                    color = LeWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    if (!hasLoop) return@IconButton
                    vm.stopAudition()
                    vm.saveLoopEditor(song)
                    scope.launch {
                        snackbarHostState.showSnackbar("Loop gespeichert", duration = SnackbarDuration.Short)
                        delay(700)
                        onClose()
                    }
                }) {
                    Icon(Icons.Filled.Check, contentDescription = "Speichern",
                        tint = if (hasLoop) LeVolt else LeGray)
                }
            }

            // ── Waveform ──────────────────────────────────────────────────────
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
                    val canvasW      = constraints.maxWidth.toFloat()
                    val canvasH      = constraints.maxHeight.toFloat()
                    val handleZonePx = with(density) { 44.dp.toPx() }
                    val knobR        = with(density) { 10.dp.toPx() }
                    val tapThreshPx  = with(density) { 8.dp.toPx() }

                    fun naturalX(ms: Long) = ms.toFloat() / effectiveDuration * canvasW
                    fun msToScreen(ms: Long) = naturalX(ms) * scale + offsetX
                    fun screenToMs(x: Float) =
                        ((x - offsetX) / scale.coerceAtLeast(0.001f) / canvasW * effectiveDuration)
                            .roundToLong().coerceIn(0L, effectiveDuration)
                    fun clampOffset(s: Float = scale, dx: Float) =
                        dx.coerceIn(canvasW * (1f - s), 0f)
                    fun snapToOnset(ms: Long): Long {
                        val threshMs = (effectiveDuration / scale / canvasW * handleZonePx)
                            .roundToLong().coerceAtLeast(50L)
                        return waveformData?.onsets
                            ?.minByOrNull { abs(it - ms) }
                            ?.takeIf { abs(it - ms) <= threshMs } ?: ms
                    }

                    // Keep rememberUpdatedState for stable closure reads in pointerInput
                    val latestTapToCreate  by rememberUpdatedState(tapToCreate)
                    val latestLoopStartMs  by rememberUpdatedState(loopStartMs)
                    val latestLoopEndMs    by rememberUpdatedState(loopEndMs)

                    Box(modifier = Modifier.fillMaxSize()) {

                        // ── Canvas ────────────────────────────────────────────
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val mid = canvasH / 2f

                            drawRect(LeBg, size = size)

                            // ── Waveform: filled Path + top/bottom stroke ─────
                            val samples = waveformData?.samples
                            if (samples != null && samples.isNotEmpty()) {
                                val n = samples.size

                                // Build closed shape (top L→R, bottom R→L)
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
                                    val h  = samples[i] * mid * 0.80f
                                    closedPath.lineTo(sx, mid + h)
                                }
                                closedPath.close()

                                drawPath(closedPath, LeWave.copy(alpha = 0.28f))
                                drawPath(topPath, LeWave, style = Stroke(width = 1.5f))
                                drawPath(botPath, LeWave.copy(alpha = 0.55f), style = Stroke(width = 1f))
                            } else {
                                drawLine(LeGray, Offset(0f, mid), Offset(canvasW, mid), 1f)
                            }

                            // Center line
                            drawLine(LeGray.copy(alpha = 0.20f), Offset(0f, mid), Offset(canvasW, mid), 1f)

                            // Onset flickers (top + bottom third only, not full height)
                            waveformData?.onsets?.forEach { ms ->
                                val x = msToScreen(ms)
                                if (x < 0f || x > canvasW) return@forEach
                                drawLine(LeOnset, Offset(x, 0f), Offset(x, canvasH * 0.28f), 1.5f)
                                drawLine(LeOnset, Offset(x, canvasH * 0.72f), Offset(x, canvasH), 1.5f)
                            }

                            // ── Markers ───────────────────────────────────────
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

                                // Filled loop region
                                val ox = lsx.coerceAtLeast(0f)
                                val ow = (lex.coerceAtMost(canvasW) - ox).coerceAtLeast(0f)
                                if (ow > 0f) {
                                    drawRect(LeOverlay, topLeft = Offset(ox, 0f), size = Size(ow, canvasH))
                                }

                                // Start handle (green)
                                drawLine(LeGreen, Offset(lsx, 0f), Offset(lsx, canvasH), 2.5f)
                                val sKnobX = lsx.coerceIn(knobR, canvasW - knobR)
                                if (lsx > -knobR && lsx < canvasW + knobR) {
                                    drawCircle(LeGreen, knobR, Offset(sKnobX, mid))
                                    drawCircle(LeBg, knobR * 0.42f, Offset(sKnobX, mid))
                                }
                                // Off-screen left edge pill
                                if (lsx < 0f) {
                                    drawRect(LeGreen.copy(alpha = 0.7f),
                                        topLeft = Offset(0f, mid - 18f), size = Size(5f, 36f))
                                }

                                // End handle (red)
                                drawLine(LeRed, Offset(lex, 0f), Offset(lex, canvasH), 2.5f)
                                val eKnobX = lex.coerceIn(knobR, canvasW - knobR)
                                if (lex > -knobR && lex < canvasW + knobR) {
                                    drawCircle(LeRed, knobR, Offset(eKnobX, mid))
                                    drawCircle(LeBg, knobR * 0.42f, Offset(eKnobX, mid))
                                }
                                // Off-screen right edge pill
                                if (lex > canvasW) {
                                    drawRect(LeRed.copy(alpha = 0.7f),
                                        topLeft = Offset(canvasW - 5f, mid - 18f), size = Size(5f, 36f))
                                }
                            }
                        }

                        // ── Tap-to-create hint ────────────────────────────────
                        if (tapToCreate) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (firstTapMs >= 0L)
                                        "2. Tap: Loop-Ende setzen"
                                    else
                                        "1. Tap: Loop-Start setzen",
                                    color = LeVolt.copy(alpha = 0.72f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // ── Gesture layer ─────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(canvasW, effectiveDuration) {
                                    awaitEachGesture {
                                        val down   = awaitFirstDown(requireUnconsumed = false)
                                        val touchX = down.position.x
                                        val touchY = down.position.y

                                        val startScreenX = msToScreen(latestLoopStartMs)
                                        val endScreenX   = msToScreen(latestLoopEndMs)
                                        val dragTarget = when {
                                            latestTapToCreate -> DragTarget.NONE
                                            abs(touchX - startScreenX) < handleZonePx -> DragTarget.START
                                            abs(touchX - endScreenX)   < handleZonePx -> DragTarget.END
                                            touchX > startScreenX && touchX < endScreenX -> DragTarget.LOOP
                                            else -> DragTarget.NONE
                                        }

                                        var prevSpan = 0f
                                        var didZoom  = false
                                        var didDrag  = false
                                        var maxMove  = 0f

                                        do {
                                            val event   = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }

                                            if (pressed.size >= 2) {
                                                val p1   = pressed[0]; val p2 = pressed[1]
                                                val span = abs(p2.position.x - p1.position.x)
                                                val cx   = (p1.position.x + p2.position.x) / 2f
                                                if (prevSpan > 0f) {
                                                    val factor    = span / prevSpan.coerceAtLeast(1f)
                                                    val anchorMs  = screenToMs(cx)
                                                    val newScale  = (scale * factor).coerceIn(1f, 100f)
                                                    val newOffset = cx - naturalX(anchorMs) * newScale
                                                    scale   = newScale
                                                    offsetX = clampOffset(newScale, newOffset)
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
                                                        didZoom -> {
                                                            offsetX = clampOffset(dx = offsetX + dx)
                                                        }
                                                        dragTarget == DragTarget.START -> {
                                                            vm.leSetStart(
                                                                latestLoopStartMs + (dx * msPpx).roundToLong()
                                                            )
                                                            didDrag = true
                                                        }
                                                        dragTarget == DragTarget.END -> {
                                                            vm.leSetEnd(
                                                                latestLoopEndMs + (dx * msPpx).roundToLong(),
                                                                effectiveDuration
                                                            )
                                                            didDrag = true
                                                        }
                                                        dragTarget == DragTarget.LOOP -> {
                                                            val len = latestLoopEndMs - latestLoopStartMs
                                                            val ns  = (latestLoopStartMs + (dx * msPpx).roundToLong())
                                                                .coerceIn(0L, effectiveDuration - len)
                                                            vm.leSetBoth(ns, ns + len, effectiveDuration)
                                                            didDrag = true
                                                        }
                                                        else -> {
                                                            offsetX = clampOffset(dx = offsetX + dx)
                                                        }
                                                    }
                                                    p.consume()
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })

                                        val isTap = maxMove < tapThreshPx && !didDrag && !didZoom
                                        when {
                                            isTap && latestTapToCreate -> {
                                                vm.leTapCanvas(screenToMs(touchX))
                                            }
                                            didDrag && !didZoom -> {
                                                // Snap to onset on drag release
                                                when (dragTarget) {
                                                    DragTarget.START ->
                                                        vm.leSetStart(snapToOnset(latestLoopStartMs))
                                                    DragTarget.END ->
                                                        vm.leSetEnd(snapToOnset(latestLoopEndMs), effectiveDuration)
                                                    else -> {}
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // ── Vorhör Player ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeBg)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val canAudition = !isLoading && auditionUri.isNotEmpty()
                Text(
                    text = if (isAuditioning) "◼  STOP" else "▶  VORHÖR",
                    color = when {
                        !canAudition  -> LeGray
                        isAuditioning -> LeRed
                        else          -> LeGreen
                    },
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .background(
                            when {
                                !canAudition  -> Color(0x22777777)
                                isAuditioning -> Color(0x33FF4444)
                                else          -> Color(0x3300FF88)
                            },
                            MaterialTheme.shapes.small
                        )
                        .then(
                            if (canAudition) Modifier.clickable {
                                vm.toggleAudition(auditionUri, loopStartMs, loopEndMs)
                            } else Modifier
                        )
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                )
            }

            // ── Zeit-Anzeige + Fine-Tune Buttons ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeLabel("START", loopStartMs, LeGreen)
                    TimeLabel("LÄNGE", (loopEndMs - loopStartMs).coerceAtLeast(0L), LeVolt)
                    TimeLabel("ENDE",  loopEndMs,   LeRed)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FineTune("← Start", LeGreen, Modifier.weight(1f)) { vm.leNudgeStart(-10L) }
                    FineTune("Start →", LeGreen, Modifier.weight(1f)) { vm.leNudgeStart(+10L) }
                    Spacer(Modifier.width(4.dp))
                    FineTune("← Ende",  LeRed,   Modifier.weight(1f)) { vm.leNudgeEnd(-10L, effectiveDuration) }
                    FineTune("Ende →",  LeRed,   Modifier.weight(1f)) { vm.leNudgeEnd(+10L, effectiveDuration) }
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
    val s   = ms % 60_000L / 1_000L
    val ms3 = ms % 1_000L
    return "%d:%02d.%03d".format(m, s, ms3)
}
