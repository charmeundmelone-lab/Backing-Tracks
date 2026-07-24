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
 * ummischen" — genau was eine Mehrkanal-USB-Schnittstelle braucht.
 *
 * Enthält eine ausführliche In-App-Diagnose ([diagnostics]) — Android/USB-Audio
 * hat viele stille Fehlerquellen (falsches Routing auf den Handy-Lautsprecher,
 * nicht unterstütztes Format, 0 Bytes geschrieben). Ohne PC/adb muss der Log
 * direkt im Gerät sichtbar & teilbar sein (gleiche Lehre wie beim Lyrics-Log).
 */
class UsbToneTester {

    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    private val log = ArrayList<String>()
    @Volatile private var routedInfo: String = "—"
    @Volatile private var firstWrite: Int = Int.MIN_VALUE

    val isRunning: Boolean get() = running

    private fun logLine(s: String) { synchronized(log) { log.add(s) } }

    /** Kompletter Diagnose-Text (für Anzeige + Teilen). */
    fun diagnostics(): String = synchronized(log) {
        buildString {
            append(log.joinToString("\n"))
            append("\n\n[laufend] geroutet-nach=").append(routedInfo)
            append("  erste-write()=")
            append(if (firstWrite == Int.MIN_VALUE) "noch nichts" else firstWrite.toString())
        }
    }

    /**
     * Startet den Dauerton. Gibt die tatsächlich verwendete Kanalzahl zurück
     * (oder null bei Fehler — Details dann in [diagnostics]).
     */
    fun start(audioManager: AudioManager?): Int? = runCatching {
        startInternal(audioManager)
    }.getOrElse { e ->
        logLine("EXCEPTION beim Start: ${e.javaClass.simpleName}: ${e.message}")
        stop()
        null
    }

    private fun startInternal(audioManager: AudioManager?): Int? {
        if (running) { logLine("Start ignoriert — läuft bereits."); return null }
        synchronized(log) { log.clear() }
        routedInfo = "—"; firstWrite = Int.MIN_VALUE

        if (audioManager == null) { logLine("FEHLER: AudioManager == null."); return null }

        // --- Alle Output-Geräte auflisten (nicht nur USB) fürs Debugging ---
        val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        logLine("Output-Geräte gesamt: ${allOutputs.size}")
        allOutputs.forEach { d ->
            logLine("  • ${typeName(d.type)} '${d.productName}' id=${d.id} " +
                "ch=${d.channelCounts.joinToString(",").ifEmpty { "?" }} " +
                "sr=${d.sampleRates.joinToString(",").ifEmpty { "?" }}")
        }

        val device = allOutputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (device == null) {
            logLine("FEHLER: Kein USB-Audio-Ausgang gefunden. CQ20B verbunden & als Audiogerät erkannt?")
            return null
        }
        logLine("USB-Gerät gewählt: '${device.productName}' id=${device.id} type=${typeName(device.type)}")

        val channelCount = device.channelCounts.maxOrNull()?.takeIf { it >= 2 } ?: 8
        val ch = channelCount.coerceIn(2, 24)
        val sampleRate = pickSampleRate(device)
        val indexMask = (1 shl ch) - 1
        logLine("Gewählt: Kanäle=$ch  SampleRate=$sampleRate  indexMask=0x${indexMask.toString(16)}")

        val format = try {
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelIndexMask(indexMask)
                .build()
        } catch (e: Exception) {
            logLine("FEHLER AudioFormat: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }

        val bytesPerFrame = ch * 2
        val bufFrames = sampleRate / 10
        val bufBytes = (bufFrames * bytesPerFrame).coerceAtLeast(bytesPerFrame * 256)
        logLine("Puffer: $bufBytes Bytes (~$bufFrames Frames)")

        val t = try {
            AudioTrack.Builder()
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
        } catch (e: Exception) {
            logLine("FEHLER AudioTrack.build(): ${e.javaClass.simpleName}: ${e.message}")
            logLine("→ Format vermutlich nicht unterstützt ($ch Kanäle @ $sampleRate Hz).")
            return null
        }

        val prefOk = t.setPreferredDevice(device)
        logLine("setPreferredDevice(USB) -> $prefOk")

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            logLine("FEHLER: AudioTrack.state=${t.state} (nicht INITIALIZED). Release.")
            t.release()
            return null
        }
        logLine("AudioTrack INITIALIZED. Format: ch=${t.channelCount} sr=${t.sampleRate}")

        val freqs = DoubleArray(ch) { c -> 150.0 * 2.0.pow(c / 12.0) }
        val amp = 0.4

        track = t
        running = true
        t.play()
        logLine("play() aufgerufen, playState=${t.playState}")

        worker = thread(name = "usb-tone-test", isDaemon = true) {
            val framesPerChunk = bufFrames.coerceAtLeast(256)
            val chunk = ShortArray(framesPerChunk * ch)
            var phase = 0L
            val local = track
            var loggedRoute = false
            while (running && local != null) {
                var idx = 0
                for (f in 0 until framesPerChunk) {
                    val n = phase + f
                    for (c in 0 until ch) {
                        val v = amp * sin(2.0 * PI * freqs[c] * n / sampleRate)
                        chunk[idx++] = (v * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                phase += framesPerChunk
                val written = local.write(chunk, 0, chunk.size)
                if (firstWrite == Int.MIN_VALUE) firstWrite = written
                if (!loggedRoute) {
                    // Erst nach dem ersten write() kennt Android das echte Routing.
                    val rd = local.routedDevice
                    routedInfo = if (rd != null) "${typeName(rd.type)} '${rd.productName}' id=${rd.id}"
                                 else "unbekannt (null)"
                    logLine("erste write() -> $written Samples; geroutet nach: $routedInfo")
                    if (rd != null && rd.type != AudioDeviceInfo.TYPE_USB_DEVICE &&
                        rd.type != AudioDeviceInfo.TYPE_USB_HEADSET &&
                        rd.type != AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                        logLine("⚠ WARNUNG: Ton läuft NICHT über USB, sondern ${typeName(rd.type)}!")
                    }
                    loggedRoute = true
                }
                if (written < 0) { logLine("FEHLER write() -> $written, Abbruch."); break }
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

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "LAUTSPRECHER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "HÖRMUSCHEL"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "KOPFHÖRER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "HEADSET"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
        else -> "TYPE_$type"
    }

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
