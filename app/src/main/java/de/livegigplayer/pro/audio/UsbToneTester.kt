package de.livegigplayer.pro.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Minimaler Wegwerf-Test fürs geplante USB-Multitrack-Feature (CQ20B).
 *
 * Spielt einen DAUERTON gleichzeitig auf ALLEN Kanälen eines angeschlossenen
 * USB-Audiogeräts — jeder Kanal mit EIGENER Frequenz, damit man am Pult sieht,
 * dass die Kanäle wirklich diskret ankommen (jeder Eingangskanal zeigt Pegel).
 *
 * Kerntrick gegen das Stereo-Downmix-Problem: [AudioFormat.Builder.setChannelIndexMask].
 * Ein Index-Mask sagt Android "das sind rohe Kanäle 0..N-1, NICHT positionsbasiert
 * ummischen" — genau was eine Mehrkanal-USB-Schnittstelle braucht. Positionsbasierte
 * Masken (CHANNEL_OUT_*) würden bei >2 Kanälen ggf. heruntergemischt.
 *
 * Bewusst NICHT an AudioEngine/ExoPlayer gekoppelt — reiner Diagnose-Pfad, der die
 * Plattform-Fähigkeit beweist, bevor die eigentliche Engine umgebaut wird.
 */
class UsbToneTester {

    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    /** Läuft gerade ein Testton? */
    val isRunning: Boolean get() = running

    /**
     * Startet den Dauerton. Gibt die tatsächlich verwendete Kanalzahl zurück
     * (oder null, wenn kein USB-Gerät gefunden / Start fehlschlug).
     */
    fun start(audioManager: AudioManager?): Int? = runCatching {
        startInternal(audioManager)
    }.getOrElse {
        // Nicht unterstütztes Format o.ä. -> sauber aufräumen, kein Crash.
        stop()
        null
    }

    private fun startInternal(audioManager: AudioManager?): Int? {
        if (running) return null
        val device = findUsbOutput(audioManager) ?: return null

        // Größte vom Gerät gemeldete Kanalzahl wählen (CQ20B: bis 24), Fallback 8.
        val channelCount = device.channelCounts.maxOrNull()?.takeIf { it >= 2 } ?: 8
        // Auf sinnvolle Obergrenze klemmen (Index-Mask ist ein Int -> max 31 Bit).
        val ch = channelCount.coerceIn(2, 24)

        val sampleRate = pickSampleRate(device)
        val indexMask = (1 shl ch) - 1  // Kanäle 0..ch-1 aktiv

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelIndexMask(indexMask)
            .build()

        // Puffer: ~100ms, mind. eine sinnvolle Untergrenze.
        val bytesPerFrame = ch * 2
        val bufFrames = (sampleRate / 10)
        val bufBytes = (bufFrames * bytesPerFrame).coerceAtLeast(bytesPerFrame * 256)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Ausgabe explizit aufs USB-Gerät zwingen.
        t.setPreferredDevice(device)

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return null
        }

        // Frequenz pro Kanal: Halbton-Abstände ab 150 Hz -> klar unterscheidbar.
        val freqs = DoubleArray(ch) { c -> 150.0 * 2.0.pow(c / 12.0) }
        val amp = 0.4 // Headroom

        track = t
        running = true
        t.play()

        worker = thread(name = "usb-tone-test", isDaemon = true) {
            val framesPerChunk = bufFrames.coerceAtLeast(256)
            val chunk = ShortArray(framesPerChunk * ch)
            var phase = 0L
            val local = track
            while (running && local != null) {
                var idx = 0
                for (f in 0 until framesPerChunk) {
                    val n = phase + f
                    for (c in 0 until ch) {
                        val v = amp * sin(2.0 * PI * freqs[c] * n / sampleRate)
                        chunk[idx++] = (v * 32767.0).toInt()
                            .coerceIn(-32767, 32767).toShort()
                    }
                }
                phase += framesPerChunk
                val written = local.write(chunk, 0, chunk.size)
                if (written < 0) break // Fehler -> raus
            }
        }
        return ch
    }

    fun stop() {
        running = false
        worker?.join(500L)
        worker = null
        track?.run {
            runCatching { pause() }
            runCatching { flush() }
            runCatching { stop() }
            release()
        }
        track = null
    }

    private fun findUsbOutput(audioManager: AudioManager?): AudioDeviceInfo? =
        audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

    // Bevorzugt 48 kHz (Pro-Audio-Standard, CQ20B), sonst die erste gemeldete Rate.
    private fun pickSampleRate(device: AudioDeviceInfo): Int {
        val rates = device.sampleRates
        return when {
            rates.isEmpty() -> 48000
            rates.contains(48000) -> 48000
            rates.contains(44100) -> 44100
            else -> rates.first()
        }
    }
}
