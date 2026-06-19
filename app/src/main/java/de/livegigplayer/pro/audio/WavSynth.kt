package de.livegigplayer.pro.audio

import java.io.ByteArrayOutputStream
import java.io.File

object WavSynth {
    private const val SR = 44100

    fun writeLeftSine(file: File, freqHz: Float, durationSec: Float = 4f) =
        file.writeBytes(buildWav(sineChannelPcm(freqHz, durationSec, leftChannel = true)))

    fun writeRightSine(file: File, freqHz: Float, durationSec: Float = 4f) =
        file.writeBytes(buildWav(sineChannelPcm(freqHz, durationSec, leftChannel = false)))

    fun writeClickRight(file: File, bpm: Int, durationSec: Float = 4f) {
        val n = (SR * durationSec).toInt()
        val pcm = ShortArray(n * 2)
        val beatSamples = (SR * 60.0 / bpm).toInt()
        val tickLen = SR / 40          // 25 ms decaying tick
        var beat = 0
        while (beat * beatSamples < n) {
            val start = beat * beatSamples
            for (t in 0 until tickLen) {
                val idx = start + t
                if (idx >= n) break
                val angle = 2.0 * Math.PI * 1000.0 * t / SR
                val decay = 1.0 - t.toDouble() / tickLen
                pcm[idx * 2 + 1] = (Short.MAX_VALUE * 0.9 * decay * Math.sin(angle)).toInt().toShort()
            }
            beat++
        }
        file.writeBytes(buildWav(pcm))
    }

    fun writeStereoMix(file: File, durationSec: Float = 4f) {
        val n = (SR * durationSec).toInt()
        val pcm = ShortArray(n * 2)
        // G major chord: G3 B3 D4 G4
        val freqs = floatArrayOf(196f, 246.94f, 293.66f, 392f)
        for (i in 0 until n) {
            var v = 0.0
            freqs.forEach { f -> v += Math.sin(2.0 * Math.PI * f * i / SR) }
            val s = (Short.MAX_VALUE * 0.2 * v).toInt().toShort()
            pcm[i * 2]     = s
            pcm[i * 2 + 1] = s
        }
        file.writeBytes(buildWav(pcm))
    }

    private fun sineChannelPcm(freqHz: Float, durationSec: Float, leftChannel: Boolean): ShortArray {
        val n = (SR * durationSec).toInt()
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val s = (Short.MAX_VALUE * 0.7 * Math.sin(2.0 * Math.PI * freqHz * i / SR)).toInt().toShort()
            if (leftChannel) pcm[i * 2] = s else pcm[i * 2 + 1] = s
        }
        return pcm
    }

    private fun buildWav(pcm: ShortArray): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        fun i32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun i16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray(Charsets.US_ASCII)); i32(36 + dataBytes)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII)); i32(16)
        i16(1); i16(2); i32(SR); i32(SR * 4); i16(4); i16(16)
        out.write("data".toByteArray(Charsets.US_ASCII)); i32(dataBytes)
        pcm.forEach { s -> out.write(s.toInt() and 0xFF); out.write((s.toInt() shr 8) and 0xFF) }
        return out.toByteArray()
    }
}
