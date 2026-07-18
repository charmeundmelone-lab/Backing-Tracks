package de.livegigplayer.pro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private val LyricsBg    = Color(0xFF0A0A0A)
private val LyricsVolt  = Color(0xFFE8FF00)
private val LyricsGray  = Color(0xFF777777)
private val LyricsWhite = Color(0xFFFFFFFF)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Vollbild-Teleprompter: reine Lyrics (keine Akkorde), Hochkant, Auto-Scroll
 * gekoppelt an die echte Wiedergabeposition — läuft dadurch exakt am Songende
 * durch, unabhängig von der genau eingetragenen BPM. Tap irgendwo im Textbereich
 * synct auf die nächste noch nicht erreichte Zeile nach. Scroll bewegt sich
 * ausschließlich vorwärts/abwärts (siehe targetScrollPx-Klemmung in LyricsContent).
 */
@Composable
fun LyricsOverlay(
    visible: Boolean,
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(visible) {
        val original = activity?.requestedOrientation
        if (visible) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (visible && original != null) activity?.requestedOrientation = original
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        if (song != null) {
            LyricsContent(
                song       = song,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying  = isPlaying,
                onClose    = onClose
            )
        }
    }
}

@Composable
private fun LyricsContent(
    song: Song,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit
) {
    val lines = remember(song.id, song.lyrics) { song.lyrics.lines() }

    val scrollState   = rememberScrollState()
    val scope         = rememberCoroutineScope()
    // index -> gemessene Y-Position (px) innerhalb der scrollbaren Column
    val linePositions = remember(song.id) { mutableMapOf<Int, Float>() }

    // Sync-Anker (Zeit, Scroll-Px), ab dem die aktuelle Scroll-Rate berechnet wird.
    // Wird beim Songwechsel und bei jedem Tap-to-Sync neu gesetzt.
    var anchorPositionMs by remember(song.id) { mutableStateOf(0L) }
    var anchorScrollPx   by remember(song.id) { mutableStateOf(0f) }
    // Zuletzt gesetztes Scroll-Ziel — wird NIE verringert (Anforderung: nur abwärts, nie zurück).
    var targetScrollPx   by remember(song.id) { mutableStateOf(0f) }

    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestDurationMs by rememberUpdatedState(durationMs)

    // Kontinuierlicher Frame-für-Frame-Scroll, proportional zur echten Wiedergabeposition.
    // Dadurch ist der Song exakt zu Ende gescrollt, wenn er zu Ende gespielt ist —
    // unabhängig davon, ob die hinterlegte BPM exakt stimmt.
    LaunchedEffect(song.id) {
        while (isActive) {
            withFrameNanos { }
            val dur = latestDurationMs
            val max = scrollState.maxValue
            if (dur > anchorPositionMs && max > 0) {
                val rate    = (max - anchorScrollPx) / (dur - anchorPositionMs).toFloat()
                val raw     = anchorScrollPx + rate * (latestPositionMs - anchorPositionMs).toFloat()
                val clamped = raw.coerceIn(0f, max.toFloat())
                if (clamped > targetScrollPx) targetScrollPx = clamped
            }
            if (targetScrollPx.roundToInt() != scrollState.value) {
                scrollState.scrollTo(targetScrollPx.roundToInt())
            }
        }
    }

    fun tapToSync() {
        val nextLinePx = linePositions.entries
            .filter { it.value > targetScrollPx + 4f }
            .minByOrNull { it.value }
            ?.value ?: return
        anchorPositionMs = latestPositionMs
        anchorScrollPx   = nextLinePx
        if (nextLinePx > targetScrollPx) targetScrollPx = nextLinePx
        scope.launch { scrollState.animateScrollTo(targetScrollPx.roundToInt()) }
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
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = LyricsGray)
                }
            }

            // Lyrics-Bereich — Tap synct auf die nächste noch nicht erreichte Zeile.
            // Manuelles Drag-Scrollen ist bewusst deaktiviert (enabled = false), sonst
            // könnte man aus Versehen zurückscrollen — das darf laut Anforderung nie passieren.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = false)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { tapToSync() })
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    if (line.isBlank()) {
                        Spacer(modifier = Modifier.height(24.dp))
                    } else {
                        Text(
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
