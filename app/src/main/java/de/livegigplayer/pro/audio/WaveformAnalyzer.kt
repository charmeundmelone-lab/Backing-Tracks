package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

private const val WTAG = "WaveformAnalyzer"

object WaveformAnalyzer {

    data class WaveformData(
        val samples: FloatArray,   // normalized RMS 0..1, length = maxSamples
        val onsets: LongArray,     // onset times in ms (transient feet)
        val durationMs: Long
    )

    fun analyze(context: Context, audioUri: String, maxSamples: Int = 1000): WaveformData? {
        if (audioUri.isEmpty()) return null
        return try {
            val uri = Uri.parse(audioUri)
            context.contentResolver.openInputStream(uri)?.buffered(65536)?.use { stream ->
                parseWav(stream, maxSamples)
            }
        } catch (e: Exception) {
            Log.e(WTAG, "analyze failed for $audioUri", e)
            null
        }
    }

    private fun parseWav(stream: InputStream, maxSamples: Int): WaveformData? {
        // RIFF header: 4 "RIFF" + 4 size + 4 "WAVE"
        val riff = ByteArray(12)
        if (stream.read(riff) < 12) return null
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null

        var channels      = 1
        var sampleRate    = 44100
        var bitsPerSample = 16

        // Iterate chunks until we find "data"
        val chunkHdr = ByteArray(8)
        outer@ while (true) {
            if (stream.read(chunkHdr) < 8) return null
            val id       = String(chunkHdr, 0, 4)
            val chunkLen = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

            when (id) {
                "fmt " -> {
                    val fmtLen = chunkLen.toInt().coerceIn(16, 512)
                    val fmt    = ByteArray(fmtLen)
                    stream.read(fmt)
                    if (chunkLen > fmtLen) stream.skip(chunkLen - fmtLen)
                    val fb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    fb.short                             // audioFormat (1=PCM)
                    channels      = fb.short.toInt()
                    sampleRate    = fb.int
                    fb.int; fb.short                     // byteRate, blockAlign
                    bitsPerSample = if (fmt.size >= 16) fb.short.toInt() else 16
                }
                "data" -> break@outer
                else   -> skipFully(stream, chunkLen)
            }
        }

        val bytesPerSample = bitsPerSample / 8
        val frameSize      = channels * bytesPerSample
        if (frameSize <= 0 || sampleRate <= 0) return null

        // We don't know exact data byte count if "data" size was 0xFFFFFFFF (streaming WAV).
        // Read until EOF and count dynamically.
        val framesPerWindow = (sampleRate.toLong() * 50 / 1000).coerceAtLeast(1L) // 50ms windows

        val resultSamples = FloatArray(maxSamples)
        val onsetList     = mutableListOf<Long>()
        var sampleIdx     = 0
        var frameIdx      = 0L
        var smoothed      = 0f

        val winBuf = ByteArray((frameSize * framesPerWindow).coerceAtMost(65536L).toInt())

        while (sampleIdx < maxSamples) {
            val read = stream.read(winBuf)
            if (read <= 0) break

            val frames = read / frameSize
            val fb     = ByteBuffer.wrap(winBuf, 0, frames * frameSize).order(ByteOrder.LITTLE_ENDIAN)
            var sumSq  = 0.0

            for (f in 0 until frames) {
                val s = when (bitsPerSample) {
                    8    -> ((fb.get().toInt() and 0xFF) - 128).toDouble() / 128.0
                    16   -> fb.short.toDouble() / 32768.0
                    32   -> fb.int.toDouble()   / Int.MAX_VALUE.toDouble()
                    else -> { fb.get(); 0.0 }
                }
                sumSq += s * s
                repeat(channels - 1) {
                    when (bitsPerSample) {
                        8    -> fb.get()
                        16   -> fb.short
                        32   -> fb.int
                        else -> fb.get()
                    }
                }
            }

            val rms     = sqrt(sumSq / frames.coerceAtLeast(1)).toFloat()
            val timeMs  = frameIdx * 1000L / sampleRate

            // Onset: energy rise > 2.5× smoothed baseline
            if (rms > smoothed * 2.5f && rms > 0.025f && sampleIdx > 1) {
                onsetList.add(timeMs)
            }
            smoothed = 0.88f * smoothed + 0.12f * rms

            resultSamples[sampleIdx] = rms
            frameIdx += frames
            sampleIdx++
        }

        if (sampleIdx == 0) return null

        val peak = resultSamples.take(sampleIdx).maxOrNull()?.coerceAtLeast(0.001f) ?: return null
        for (i in 0 until sampleIdx) resultSamples[i] /= peak

        val durationMs = frameIdx * 1000L / sampleRate
        return WaveformData(resultSamples.copyOf(sampleIdx), onsetList.toLongArray(), durationMs)
    }

    private fun skipFully(stream: InputStream, n: Long) {
        var remaining = n
        val skip = ByteArray(4096)
        while (remaining > 0) {
            val r = stream.read(skip, 0, remaining.coerceAtMost(4096L).toInt())
            if (r <= 0) break
            remaining -= r
        }
    }
}
