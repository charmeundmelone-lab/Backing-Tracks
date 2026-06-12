package de.minitraxx.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Dekodiert eine beliebige vom Gerät unterstützte Audiodatei (WAV/MP3/FLAC/AAC/OGG …)
 * in das kanonische Stem-Format der Engine: WAV, PCM16, mono, 48 kHz.
 *
 * Stereo-Quellen werden kanalbewusst behandelt: Ist eine Seite praktisch still
 * (vorgepannte Dateien, z. B. Track hart links exportiert), wird ausschließlich
 * die bespielte Seite in voller Lautstärke übernommen — kein −6-dB-Verlust durch
 * stumpfes Mono-Summieren. Nur wenn beide Seiten Inhalt haben, wird gemittelt.
 */
object AudioImporter {

    const val TARGET_RATE = NativeEngine.SAMPLE_RATE

    /** Unter diesem Peak gilt ein Kanal als still (~ -50 dBFS). */
    private const val SILENCE_PEAK = 0.0032f

    class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Result(val frames: Long)

    fun import(context: Context, source: Uri, dest: File): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
        } catch (e: Exception) {
            extractor.release()
            throw ImportException("Datei kann nicht geöffnet werden", e)
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw ImportException("Keine Audiospur gefunden")
        }
        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = try {
            MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
        } catch (e: Exception) {
            extractor.release()
            throw ImportException("Kein Decoder für $mime", e)
        }

        dest.parentFile?.mkdirs()
        val tmpL = File(dest.parentFile, dest.name + ".l.part")
        val tmpR = File(dest.parentFile, dest.name + ".r.part")
        val tmpOut = File(dest.parentFile, dest.name + ".part")
        try {
            val left = ChannelStream(tmpL)
            val right = ChannelStream(tmpR)
            try {
                decodeLoop(extractor, codec, left, right)
            } finally {
                left.close()
                right.close()
            }
            if (left.frames <= 0) throw ImportException("Datei enthält kein Audio")

            val frames = writeCanonical(left, right, tmpL, tmpR, tmpOut)
            if (dest.exists()) dest.delete()
            if (!tmpOut.renameTo(dest)) throw IOException("rename failed")
            return Result(frames)
        } catch (e: Exception) {
            tmpOut.delete()
            if (e is ImportException) throw e
            throw ImportException("Dekodierung fehlgeschlagen", e)
        } finally {
            tmpL.delete()
            tmpR.delete()
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            extractor.release()
        }
    }

    /** Resampler-Zustand und Roh-Ausgabe (PCM16 LE) für einen Kanal. */
    private class ChannelStream(file: File) {
        val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
        var resamplePos = 0.0
        var lastSample = 0f
        var haveLast = false
        var peak = 0f
        var frames = 0L

        private val pending = ShortArray(1 shl 14)

        /** Resampelt [samples] (Quellrate [srcRate]) linear auf 48 kHz und schreibt PCM16. */
        fun process(samples: FloatArray, srcRate: Int) {
            if (samples.isEmpty()) return
            for (s in samples) {
                val a = abs(s)
                if (a > peak) peak = a
            }
            val step = srcRate.toDouble() / TARGET_RATE
            var written = 0
            while (true) {
                val idx = resamplePos
                val i0 = kotlin.math.floor(idx).toInt()
                val frac = (idx - i0).toFloat()
                val s0: Float
                val s1: Float
                if (i0 < 0) {
                    s0 = if (haveLast) lastSample else samples[0]
                    s1 = samples[0]
                } else if (i0 + 1 < samples.size) {
                    s0 = samples[i0]
                    s1 = samples[i0 + 1]
                } else {
                    break
                }
                if (written >= pending.size) {
                    flush(written)
                    written = 0
                }
                pending[written++] = floatToPcm16(s0 + (s1 - s0) * frac)
                resamplePos += step
            }
            if (written > 0) flush(written)
            lastSample = samples[samples.size - 1]
            haveLast = true
            resamplePos -= samples.size
        }

        private fun flush(count: Int) {
            val bytes = ByteArray(count * 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().put(pending, 0, count)
            out.write(bytes)
            frames += count
        }

        fun close() = out.close()
    }

    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        left: ChannelStream,
        right: ChannelStream,
    ) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var srcRate = TARGET_RATE
        var channels = 1
        var pcmFloat = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                }

                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (channels == 2) {
                        // Kanäle getrennt halten — Entscheidung fällt nach dem Dekodieren.
                        val (chL, chR) = splitStereo(buffer, pcmFloat)
                        left.process(chL, srcRate)
                        right.process(chR, srcRate)
                    } else {
                        left.process(downmixAll(buffer, channels, pcmFloat), srcRate)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
    }

    /**
     * Wählt das Mono-Ergebnis: stille Seite wird verworfen (volle Lautstärke der
     * bespielten Seite), sonst Mittelung beider Seiten. Schreibt das fertige WAV.
     */
    private fun writeCanonical(
        left: ChannelStream,
        right: ChannelStream,
        tmpL: File,
        tmpR: File,
        tmpOut: File,
    ): Long {
        val stereo = right.frames > 0
        val useOnlyLeft = stereo && right.peak < SILENCE_PEAK && left.peak >= SILENCE_PEAK
        val useOnlyRight = stereo && left.peak < SILENCE_PEAK && right.peak >= SILENCE_PEAK

        BufferedOutputStream(FileOutputStream(tmpOut), 1 shl 16).use { out ->
            out.write(wavHeaderPlaceholder())
            when {
                !stereo || useOnlyLeft -> copyRaw(tmpL, out)
                useOnlyRight -> copyRaw(tmpR, out)
                else -> averageRaw(tmpL, tmpR, out)
            }
        }
        val frames = if (!stereo || useOnlyLeft) left.frames
        else if (useOnlyRight) right.frames
        else minOf(left.frames, right.frames)
        patchWavHeader(tmpOut, frames)
        return frames
    }

    private fun copyRaw(src: File, out: BufferedOutputStream) {
        FileInputStream(src).use { it.copyTo(out, 1 shl 16) }
    }

    private fun averageRaw(srcL: File, srcR: File, out: BufferedOutputStream) {
        BufferedInputStream(FileInputStream(srcL), 1 shl 16).use { inL ->
            BufferedInputStream(FileInputStream(srcR), 1 shl 16).use { inR ->
                val bufL = ByteArray(1 shl 14)
                val bufR = ByteArray(1 shl 14)
                while (true) {
                    val nL = inL.readNBytesCompat(bufL)
                    val nR = inR.readNBytesCompat(bufR)
                    val n = minOf(nL, nR) and 1.inv()
                    if (n <= 0) break
                    val sbL = ByteBuffer.wrap(bufL, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val sbR = ByteBuffer.wrap(bufR, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val outBytes = ByteArray(n)
                    val sbO = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    for (i in 0 until n / 2) {
                        sbO.put(((sbL.get(i).toInt() + sbR.get(i).toInt()) / 2).toShort())
                    }
                    out.write(outBytes)
                }
            }
        }
    }

    /** Liest so viele Bytes wie möglich in [buf] (wie readNBytes, minSdk-sicher). */
    private fun BufferedInputStream.readNBytesCompat(buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun splitStereo(buffer: ByteBuffer, isFloat: Boolean): Pair<FloatArray, FloatArray> {
        return if (isFloat) {
            val fb = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            val frames = fb.remaining() / 2
            val l = FloatArray(frames)
            val r = FloatArray(frames)
            for (f in 0 until frames) {
                l[f] = fb.get(f * 2)
                r[f] = fb.get(f * 2 + 1)
            }
            l to r
        } else {
            val sb = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            val frames = sb.remaining() / 2
            val l = FloatArray(frames)
            val r = FloatArray(frames)
            for (f in 0 until frames) {
                l[f] = sb.get(f * 2) / 32768f
                r[f] = sb.get(f * 2 + 1) / 32768f
            }
            l to r
        }
    }

    private fun downmixAll(buffer: ByteBuffer, channels: Int, isFloat: Boolean): FloatArray {
        val ch = channels.coerceAtLeast(1)
        return if (isFloat) {
            val fb = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            val frames = fb.remaining() / ch
            FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until ch) sum += fb.get(f * ch + c)
                sum / ch
            }
        } else {
            val sb = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            val frames = sb.remaining() / ch
            FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until ch) sum += sb.get(f * ch + c) / 32768f
                sum / ch
            }
        }
    }

    private fun floatToPcm16(v: Float): Short {
        val clamped = v.coerceIn(-1f, 1f)
        return (clamped * 32767f).toInt().toShort()
    }

    private fun wavHeaderPlaceholder(): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(0) // wird gepatcht
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(TARGET_RATE)
        header.putInt(TARGET_RATE * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(0) // wird gepatcht
        return header.array()
    }

    private fun patchWavHeader(file: File, frames: Long) {
        val dataBytes = frames * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLe((dataBytes + 36).toInt()))
            raf.seek(40)
            raf.write(intLe(dataBytes.toInt()))
        }
    }

    private fun intLe(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
}
