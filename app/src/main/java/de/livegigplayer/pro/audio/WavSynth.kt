package de.livegigplayer.pro.audio

import java.io.ByteArrayOutputStream
import java.io.File

object WavSynth {
    private const val SR = 44100

    /** Berechnet die Anzahl Samples für N exakte Beats bei gegebenem BPM. */
    fun beatsToSamples(bpm: Int, beats: Int): Int = beats * 60 * SR / bpm

    fun writeLeftSine(file: File, freqHz: Float, numSamples: Int) =
        file.writeBytes(buildWav(sinePcm(freqHz, numSamples, leftChannel = true)))

    fun writeRightSine(file: File, freqHz: Float, numSamples: Int) =
        file.writeBytes(buildWav(sinePcm(freqHz, numSamples, leftChannel = false)))

    /** Click-Track auf rechtem Kanal, Loop-Länge = exakt numSamples. */
    fun writeClickRight(file: File, bpm: Int, numSamples: Int) {
        val pcm = ShortArray(numSamples * 2)
        val beatSamples = SR * 60 / bpm
        val tickLen = SR / 40          // 25 ms Abkling-Tick
        var beat = 0
        while (beat * beatSamples < numSamples) {
            val start = beat * beatSamples
            for (t in 0 until tickLen) {
                val idx = start + t
                if (idx >= numSamples) break
                val angle = 2.0 * Math.PI * 1000.0 * t / SR
                val decay = 1.0 - t.toDouble() / tickLen
                pcm[idx * 2 + 1] = (Short.MAX_VALUE * 0.9 * decay * Math.sin(angle)).toInt().toShort()
            }
            beat++
        }
        file.writeBytes(buildWav(pcm))
    }

    fun writeStereoMix(file: File, numSamples: Int) {
        val pcm = ShortArray(numSamples * 2)
        val freqs = floatArrayOf(196f, 246.94f, 293.66f, 392f)  // G-Dur-Akkord
        for (i in 0 until numSamples) {
            var v = 0.0
            freqs.forEach { f -> v += Math.sin(2.0 * Math.PI * f * i / SR) }
            val s = (Short.MAX_VALUE * 0.2 * v).toInt().toShort()
            pcm[i * 2]     = s
            pcm[i * 2 + 1] = s
        }
        file.writeBytes(buildWav(pcm))
    }

    private fun sinePcm(freqHz: Float, numSamples: Int, leftChannel: Boolean): ShortArray {
        val pcm = ShortArray(numSamples * 2)
        for (i in 0 until numSamples) {
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
