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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

private const val MIN_LOOP_MS = 100L

// ── Palette ───────────────────────────────────────────────────────────────────
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
    vm: PlayerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isAuditioning by vm.isAuditioning.collectAsState()

    var waveformData by remember { mutableStateOf<WaveformAnalyzer.WaveformData?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var auditionUri  by remember { mutableStateOf("") }

    var loopStartMs by remember(song.id) { mutableLongStateOf(song.loopStartMs) }
    var loopEndMs   by remember(song.id) { mutableLongStateOf(song.loopEndMs) }

    // Tap-to-create: -1 = waiting for first tap, >= 0 = first tap set (waiting for second)
    var firstTapMs by remember { mutableLongStateOf(-1L) }

    // ── Zoom / pan state ─────────────────────────────────────────────────────
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) { onDispose { vm.stopAudition() } }

    // Debounced audition refresh when loop points change
    LaunchedEffect(loopStartMs, loopEndMs) {
        delay(200)
        vm.refreshAuditionLoop(loopStartMs, loopEndMs)
    }

    LaunchedEffect(song.id) {
        isLoading  = true
        firstTapMs = -1L
        val mode = withContext(Dispatchers.IO) { SongScanner.scan(song, context) }
        val uri = when (mode) {
            is TrackMode.Multitrack ->
                mode.click ?: mode.drums ?: mode.bass ?: mode.keys ?: mode.vocals ?: mode.cue ?: ""
            is TrackMode.Legacy -> mode.filePath
        }
        auditionUri = uri

        // Cache-first: load saved waveform or analyse and save
        val cached = withContext(Dispatchers.IO) { WaveformAnalyzer.loadCache(context, song.id) }
        val data = if (cached != null) {
            cached
        } else if (uri.isNotEmpty()) {
            val analyzed = withContext(Dispatchers.IO) { WaveformAnalyzer.analyze(context, uri) }
            if (analyzed != null) withContext(Dispatchers.IO) { WaveformAnalyzer.saveCache(context, song.id, analyzed) }
            analyzed
        } else null
        waveformData = data

        val dur = data?.durationMs ?: 0L
        // Initial zoom: show ~12 seconds
        if (dur > 0) {
            scale   = (dur.toFloat() / 12_000f).coerceIn(1f, 100f)
            offsetX = 0f
        }
        // Default loop if none set: 8 bars
        if (loopEndMs <= loopStartMs + MIN_LOOP_MS) {
            val bpm = song.bpmExact.takeIf { it > 0f } ?: song.bpm.toFloat().coerceAtLeast(1f)
            val eightBarsMs = (8.0 * 4.0 * 60_000.0 / bpm).roundToLong()
            loopEndMs = eightBarsMs.coerceAtMost(dur.coerceAtLeast(10_000L))
        }
        isLoading = false
    }

    val effectiveDuration = (waveformData?.durationMs ?: 0L).coerceAtLeast(1L)

    Scaffold(
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = LeBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LeBg)
                .safeDrawingPadding()
                .padding(innerPadding)
        ) {

            // ── Header ────────────────────────────────────────────────────────────
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
                    text = "Loop-Editor: ${song.title}",
                    color = LeWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    vm.stopAudition()
                    vm.updateLoopPoints(song, loopStartMs, loopEndMs)
                    scope.launch {
                        snackbarHostState.showSnackbar("Loop gespeichert", duration = SnackbarDuration.Short)
                        onClose()
                    }
                }) {
                    Icon(Icons.Filled.Check, contentDescription = "Speichern", tint = LeVolt)
                }
            }

            // ── Waveform area ─────────────────────────────────────────────────────
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
                    val handleZonePx = with(density) { 48.dp.toPx() }
                    val knobR        = with(density) { 11.dp.toPx() }
                    val stroke       = with(density) { 3.dp.toPx() }
                    val tapThreshPx  = with(density) { 8.dp.toPx() }

                    fun naturalX(ms: Long) = ms.toFloat() / effectiveDuration * canvasW
                    fun msToScreen(ms: Long) = naturalX(ms) * scale + offsetX
                    fun screenToMs(x: Float) =
                        ((x - offsetX) / scale.coerceAtLeast(0.001f) / canvasW * effectiveDuration)
                            .roundToLong().coerceIn(0L, effectiveDuration)
                    fun clampOffset(s: Float = scale, dx: Float = offsetX) =
                        dx.coerceIn(canvasW * (1f - s), 0f)
                    fun snapToOnset(ms: Long): Long {
                        val threshMs = (effectiveDuration / scale / canvasW * handleZonePx)
                            .roundToLong().coerceAtLeast(50L)
                        return waveformData?.onsets?.minByOrNull { abs(it - ms) }
                            ?.takeIf { abs(it - ms) <= threshMs } ?: ms
                    }

                    val tapToCreate = loopEndMs <= loopStartMs + MIN_LOOP_MS || firstTapMs >= 0L

                    Box(modifier = Modifier.fillMaxSize()) {

                        // ── Canvas: waveform + overlay + handles ──────────────────
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val mid = canvasH / 2f
                            drawRect(LeBg, size = size)

                            // Waveform bars
                            val samples = waveformData?.samples
                            if (samples != null && samples.isNotEmpty()) {
                                val n  = samples.size
                                val bw = (canvasW / n * scale).coerceAtLeast(1f)
                                for (i in samples.indices) {
                                    val sx = msToScreen(i.toLong() * effectiveDuration / n)
                                    if (sx < -bw || sx > canvasW + bw) continue
                                    val lineH = samples[i] * mid * 0.85f
                                    drawLine(LeWave, Offset(sx, mid - lineH), Offset(sx, mid + lineH), bw)
                                }
                            } else {
                                drawLine(LeGray, Offset(0f, mid), Offset(canvasW, mid), 1f)
                                drawRect(Color(0x22777777), size = size)
                            }

                            // Onset markers
                            waveformData?.onsets?.forEach { ms ->
                                val x = msToScreen(ms)
                                if (x < 0f || x > canvasW) return@forEach
                                drawLine(LeOnset, Offset(x, 0f), Offset(x, canvasH), 1.5f)
                            }

                            if (tapToCreate) {
                                // Show pending start marker if first tap is set
                                if (firstTapMs >= 0L) {
                                    val ftX = msToScreen(firstTapMs)
                                    if (ftX in 0f..canvasW) {
                                        drawLine(LeGreen, Offset(ftX, 0f), Offset(ftX, canvasH), stroke)
                                        drawCircle(LeGreen, knobR, Offset(ftX, mid))
                                        drawCircle(LeBg, knobR * 0.5f, Offset(ftX, mid))
                                    }
                                }
                            } else {
                                // Loop overlay
                                val lsx = msToScreen(loopStartMs)
                                val lex = msToScreen(loopEndMs)
                                val ox  = lsx.coerceAtLeast(0f)
                                val ow  = (lex - ox).coerceIn(0f, canvasW - ox)
                                if (ow > 0f) drawRect(LeOverlay, topLeft = Offset(ox, 0f), size = Size(ow, canvasH))

                                // Start handle (green)
                                val sxC = lsx.coerceIn(-knobR, canvasW + knobR)
                                drawLine(LeGreen, Offset(sxC, 0f), Offset(sxC, canvasH), stroke)
                                drawCircle(LeGreen, knobR, Offset(sxC, mid))
                                drawCircle(LeBg, knobR * 0.5f, Offset(sxC, mid))

                                // End handle (red)
                                val exC = lex.coerceIn(-knobR, canvasW + knobR)
                                drawLine(LeRed, Offset(exC, 0f), Offset(exC, canvasH), stroke)
                                drawCircle(LeRed, knobR, Offset(exC, mid))
                                drawCircle(LeBg, knobR * 0.5f, Offset(exC, mid))
                            }
                        }

                        // ── Tap-to-create instruction overlay ─────────────────────
                        if (tapToCreate) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (firstTapMs >= 0L) "Tap: Loop-Ende setzen" else "Tap: Loop-Start setzen",
                                    color = LeVolt.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // ── Gesture capture (transparent, on top) ─────────────────
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(canvasW.toLong(), effectiveDuration) {
                                    awaitEachGesture {
                                        val down   = awaitFirstDown(requireUnconsumed = false)
                                        val touchX = down.position.x
                                        val touchY = down.position.y
                                        var maxMove   = 0f
                                        var prevSpan  = 0f
                                        var nPointers = 1
                                        var didZoom   = false

                                        val startX    = msToScreen(loopStartMs)
                                        val endX      = msToScreen(loopEndMs)
                                        val nearStart = abs(touchX - startX) < handleZonePx
                                        val nearEnd   = abs(touchX - endX)   < handleZonePx
                                        val inLoop    = !nearStart && !nearEnd
                                            && touchX > startX && touchX < endX

                                        do {
                                            val event   = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }

                                            if (pressed.size >= 2) {
                                                val p1   = pressed[0]; val p2 = pressed[1]
                                                val span = abs(p2.position.x - p1.position.x)
                                                val cx   = (p1.position.x + p2.position.x) / 2f
                                                if (prevSpan > 0f && nPointers >= 2) {
                                                    val zoomChange = span / prevSpan.coerceAtLeast(1f)
                                                    val anchorMs   = screenToMs(cx)
                                                    val newScale   = (scale * zoomChange).coerceIn(1f, 100f)
                                                    val natAnchorX = naturalX(anchorMs)
                                                    val newOffsetX = cx - natAnchorX * newScale
                                                    scale   = newScale
                                                    offsetX = clampOffset(newScale, newOffsetX)
                                                    didZoom = true
                                                }
                                                prevSpan  = span
                                                nPointers = 2
                                                pressed.forEach { it.consume() }

                                            } else if (pressed.size == 1) {
                                                val p      = pressed[0]
                                                maxMove    = maxOf(maxMove,
                                                    abs(p.position.x - touchX),
                                                    abs(p.position.y - touchY))
                                                nPointers  = 1
                                                val deltaX = p.position.x - p.previousPosition.x
                                                val msPpx  = effectiveDuration / scale / canvasW
                                                val tapCreate = loopEndMs <= loopStartMs + MIN_LOOP_MS || firstTapMs >= 0L

                                                when {
                                                    nearStart && !didZoom && !tapCreate -> {
                                                        val dms = (deltaX * msPpx).roundToLong()
                                                        loopStartMs = (loopStartMs + dms)
                                                            .coerceIn(0L, loopEndMs - MIN_LOOP_MS)
                                                    }
                                                    nearEnd && !didZoom && !tapCreate -> {
                                                        val dms = (deltaX * msPpx).roundToLong()
                                                        loopEndMs = (loopEndMs + dms)
                                                            .coerceIn(loopStartMs + MIN_LOOP_MS, effectiveDuration)
                                                    }
                                                    inLoop && !didZoom && !tapCreate -> {
                                                        val dms = (deltaX * msPpx).roundToLong()
                                                        val len = loopEndMs - loopStartMs
                                                        val newStart = (loopStartMs + dms)
                                                            .coerceIn(0L, effectiveDuration - len)
                                                        loopStartMs = newStart
                                                        loopEndMs   = newStart + len
                                                    }
                                                    else -> {
                                                        offsetX = clampOffset(dx = offsetX + deltaX)
                                                    }
                                                }
                                                p.consume()
                                            }
                                        } while (event.changes.any { it.pressed })

                                        val isTap     = maxMove < tapThreshPx && !didZoom
                                        val tapCreate = loopEndMs <= loopStartMs + MIN_LOOP_MS || firstTapMs >= 0L

                                        when {
                                            isTap && tapCreate -> {
                                                val tappedMs = screenToMs(touchX)
                                                if (firstTapMs < 0L) {
                                                    firstTapMs  = tappedMs
                                                    loopStartMs = tappedMs
                                                    loopEndMs   = tappedMs
                                                } else if (tappedMs > firstTapMs + MIN_LOOP_MS) {
                                                    loopEndMs  = tappedMs
                                                    firstTapMs = -1L
                                                } else {
                                                    firstTapMs  = tappedMs
                                                    loopStartMs = tappedMs
                                                    loopEndMs   = tappedMs
                                                }
                                            }
                                            !isTap && !didZoom && !tapCreate -> {
                                                if (nearStart) loopStartMs = snapToOnset(loopStartMs)
                                                if (nearEnd)   loopEndMs   = snapToOnset(loopEndMs)
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // ── Vorhör-Player ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().background(LeBg).padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val canAudition = !isLoading && auditionUri.isNotEmpty()
                Text(
                    text      = if (isAuditioning) "◼  STOP" else "▶  VORHÖR",
                    color     = when {
                        !canAudition  -> LeGray
                        isAuditioning -> LeRed
                        else          -> LeGreen
                    },
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    modifier = Modifier
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

            // ── Zeit-Anzeige + Fine-Tune ──────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().background(LeCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                    FineTune("← Start", LeGreen, Modifier.weight(1f)) {
                        loopStartMs = (loopStartMs - 10L).coerceAtLeast(0L)
                    }
                    FineTune("Start →", LeGreen, Modifier.weight(1f)) {
                        loopStartMs = (loopStartMs + 10L).coerceAtMost(loopEndMs - MIN_LOOP_MS)
                    }
                    Spacer(Modifier.width(4.dp))
                    FineTune("← Ende", LeRed, Modifier.weight(1f)) {
                        loopEndMs = (loopEndMs - 10L).coerceAtLeast(loopStartMs + MIN_LOOP_MS)
                    }
                    FineTune("Ende →", LeRed, Modifier.weight(1f)) {
                        loopEndMs = (loopEndMs + 10L).coerceAtMost(effectiveDuration)
                    }
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
