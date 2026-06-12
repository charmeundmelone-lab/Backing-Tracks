package de.minitraxx.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dekodiert eine beliebige vom Gerät unterstützte Audiodatei (WAV/MP3/FLAC/AAC/OGG …)
 * in das kanonische Stem-Format der Engine: WAV, PCM16, mono, 48 kHz.
 * Mehrkanal-Quellen werden mono-summiert, andere Sampleraten linear resampelt.
 */
object AudioImporter {

    const val TARGET_RATE = NativeEngine.SAMPLE_RATE

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
        val tmp = File(dest.parentFile, dest.name + ".part")
        var frames = 0L
        try {
            BufferedOutputStream(FileOutputStream(tmp), 1 shl 16).use { out ->
                out.write(wavHeaderPlaceholder())
                frames = decodeLoop(extractor, codec, out)
            }
            if (frames <= 0) throw ImportException("Datei enthält kein Audio")
            patchWavHeader(tmp, frames)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("rename failed")
        } catch (e: Exception) {
            tmp.delete()
            if (e is ImportException) throw e
            throw ImportException("Dekodierung fehlgeschlagen", e)
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            extractor.release()
        }
        return Result(frames)
    }

    /** Liefert geschriebene Ziel-Frames (mono, 48 kHz). */
    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        out: BufferedOutputStream,
    ): Long {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var totalFrames = 0L

        // Resampler-Zustand (linear, über Chunk-Grenzen hinweg).
        var srcRate = TARGET_RATE
        var channels = 1
        var pcmFloatEncoding = false
        var resamplePos = 0.0
        var lastSample = 0f
        var haveLast = false

        val pending = ShortArray(1 shl 16)

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
                    pcmFloatEncoding = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }

                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val mono = downmixToMono(buffer, channels, pcmFloatEncoding)
                    // Linear auf 48 kHz resampeln.
                    val step = srcRate.toDouble() / TARGET_RATE
                    var written = 0
                    var i = 0
                    while (true) {
                        // resamplePos zählt in Quell-Samples relativ zum Chunk-Anfang
                        // (lastSample = letztes Sample des vorigen Chunks bei Index -1).
                        val idx = resamplePos
                        val i0 = kotlin.math.floor(idx).toInt()
                        val frac = (idx - i0).toFloat()
                        val s0: Float
                        val s1: Float
                        if (i0 < 0) {
                            s0 = if (haveLast) lastSample else if (mono.isNotEmpty()) mono[0] else 0f
                            s1 = if (mono.isNotEmpty()) mono[0] else s0
                        } else if (i0 + 1 < mono.size) {
                            s0 = mono[i0]
                            s1 = mono[i0 + 1]
                        } else {
                            break
                        }
                        val v = s0 + (s1 - s0) * frac
                        if (written >= pending.size) {
                            writeShorts(out, pending, written)
                            totalFrames += written
                            written = 0
                        }
                        pending[written++] = floatToPcm16(v)
                        resamplePos += step
                        i++
                    }
                    if (written > 0) {
                        writeShorts(out, pending, written)
                        totalFrames += written
                    }
                    if (mono.isNotEmpty()) {
                        lastSample = mono[mono.size - 1]
                        haveLast = true
                        resamplePos -= mono.size
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
        return totalFrames
    }

    private fun downmixToMono(buffer: ByteBuffer, channels: Int, isFloat: Boolean): FloatArray {
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

    private fun writeShorts(out: BufferedOutputStream, data: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(data, 0, count)
        out.write(bytes)
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
