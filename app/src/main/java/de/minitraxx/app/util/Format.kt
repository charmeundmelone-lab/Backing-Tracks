package de.minitraxx.app.util

import de.minitraxx.app.audio.NativeEngine
import java.util.Locale

fun framesToSeconds(frames: Long): Long = frames / NativeEngine.SAMPLE_RATE

fun formatTime(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val minutes = s / 60
    val seconds = s % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

fun formatFrames(frames: Long): String = formatTime(framesToSeconds(frames))

/** Restzeit als Countdown, z. B. "-2:34". */
fun formatRemaining(positionFrames: Long, durationFrames: Long): String {
    val remaining = (durationFrames - positionFrames).coerceAtLeast(0)
    // Aufrunden, damit der Countdown bei Songstart die volle Länge zeigt.
    val seconds = (remaining + NativeEngine.SAMPLE_RATE - 1) / NativeEngine.SAMPLE_RATE
    return "-" + formatTime(seconds)
}
