package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream

private const val TAG = "BpmAnalyzer"
private const val MAX_SECONDS = 12

object BpmAnalyzer {

    fun analyze(context: Context, uri: Uri, rightChannelOnly: Boolean = false): Float = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val wav = readWav(stream, rightChannelOnly) ?: return 0f
            detectBpm(wav.first, wav.second)
        } ?: 0f
    } catch (e: Exception) { Log.w(TAG, "analyze failed: ${e.message}"); 0f }

    private fun readWav(stream: InputStream, rightChannelOnly: Boolean): Pair<FloatArray, Int>? {
        val riff = ByteArray(12)
        if (stream.read(riff) < 12) return null
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null

        var sampleRate = 0; var channels = 0; var bitsPerSample = 0
        val hdr = ByteArray(8)
        while (true) {
            if (stream.read(hdr) < 8) return null
            val id = String(hdr, 0, 4)
            val sz = intLE(hdr, 4)
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(sz.coerceAtMost(40))
                    stream.read(fmt)
                    if (sz > 40) stream.skip((sz - 40).toLong())
                    channels = shortLE(fmt, 2).toInt()
                    sampleRate = intLE(fmt, 4)
                    bitsPerSample = shortLE(fmt, 14).toInt()
                }
                "data" -> {
                    val bps = bitsPerSample / 8
                    val frameSize = channels * bps
                    val maxBytes = (sampleRate * MAX_SECONDS * frameSize).coerceAtMost(sz).coerceAtMost(12 * 1024 * 1024)
                    val buf = ByteArray(maxBytes)
                    val read = stream.read(buf)
                    val frames = read / frameSize
                    val chOff = if (rightChannelOnly && channels >= 2) bps else 0
                    val samples = FloatArray(frames)
                    for (i in 0 until frames) {
                        val o = i * frameSize + chOff
                        samples[i] = when (bps) {
                            2 -> ((buf[o].toInt() and 0xFF) or (buf[o + 1].toInt() shl 8)).toShort().toFloat() / 32768f
                            3 -> ((buf[o].toInt() and 0xFF) or ((buf[o+1].toInt() and 0xFF) shl 8) or (buf[o+2].toInt() shl 16)).toFloat() / 8388608f
                            else -> 0f
                        }
                    }
                    return Pair(samples, sampleRate)
                }
                else -> stream.skip(sz.toLong())
            }
        }
    }

    private fun detectBpm(samples: FloatArray, sr: Int): Float {
        if (samples.size < sr / 2) return 0f
        val win = sr / 100  // 10ms windows
        val n = samples.size / win
        val energy = FloatArray(n) { i -> var e = 0f; repeat(win) { j -> val s = samples[i*win+j]; e += s*s }; e }
        val maxE = energy.maxOrNull()?.takeIf { it > 0f } ?: return 0f
        val thr = maxE * 0.35f
        val minGap = (180 / 10)  // min 180ms between peaks
        val peaks = mutableListOf<Int>()
        var last = -minGap
        for (i in 1 until n - 1) {
            if (energy[i] > thr && energy[i] >= energy[i-1] && energy[i] >= energy[i+1] && i - last >= minGap) {
                peaks.add(i); last = i
            }
        }
        if (peaks.size < 4) return 0f
        val intervals = (1 until peaks.size).map { peaks[it] - peaks[it-1] }.sorted()
        val med = intervals[intervals.size / 2].toFloat() * win / sr
        val bpm = 60f / med
        return if (bpm in 40f..240f) (bpm * 10).toInt() / 10f else 0f
    }

    private fun shortLE(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) or (b[o+1].toInt() shl 8)).toShort()
    private fun intLE(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o+1].toInt() and 0xFF) shl 8) or
            ((b[o+2].toInt() and 0xFF) shl 16) or ((b[o+3].toInt() and 0xFF) shl 24)
}
