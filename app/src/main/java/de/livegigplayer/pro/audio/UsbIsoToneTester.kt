package de.livegigplayer.pro.audio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

/**
 * Phase 3 des nativen UAC2-Multitrack-Umbaus (CQ20B): schreibt einen 440-Hz-
 * Sinuston isochron auf GENAU EINEN der 24 Kanäle (EP 0x01, IF1/alt1), alle
 * anderen Kanäle bleiben still. Beweist am Pult, dass die Kanäle diskret
 * getrennt ankommen, und liefert beim Stoppen eine Fehlerstatistik
 * (Kill-Kriterium 2: sauberer Ton ohne Knacken?).
 *
 * Die UsbDeviceConnection bleibt für die gesamte Streaming-Dauer offen (der
 * native Code arbeitet auf ihrem Dateideskriptor) und wird erst beim Stoppen
 * geschlossen.
 */
class UsbIsoToneTester {

    companion object {
        init { System.loadLibrary("nativeusb") }
    }

    private external fun nativeStart(fd: Int, ifnum: Int, alt: Int, chan: Int): String
    private external fun nativeStop(): String

    private var conn: UsbDeviceConnection? = null

    val running: Boolean get() = conn != null

    /**
     * Startet den Ton auf dem gegebenen 0-basierten Kanalindex (Kanal 9 = 8).
     * Erwartet ein bereits berechtigtes Gerät.
     */
    fun start(usbManager: UsbManager?, device: UsbDevice?, channelIndex: Int): String {
        if (conn != null) return "Ton läuft bereits — erst stoppen."
        if (usbManager == null || device == null) return "Kein USB-Gerät."
        if (!usbManager.hasPermission(device)) return "KEINE USB-Berechtigung — erst erlauben."

        val target = findOutStreamingInterface(device)
            ?: return "Kein AudioStreaming-Interface mit OUT-Endpoint gefunden."

        val c = usbManager.openDevice(device) ?: return "openDevice() fehlgeschlagen."
        val fd = c.fileDescriptor
        if (fd < 0) { c.close(); return "Ungültiger Dateideskriptor." }
        conn = c
        val r = runCatching { nativeStart(fd, target.ifNum, target.altSetting, channelIndex) }
            .getOrElse { conn = null; c.close(); return "Native Bibliothek/Start-Fehler: ${it.message}" }
        return r
    }

    /** Stoppt den Ton und schließt die Verbindung. Gibt die Statistik zurück. */
    fun stop(): String {
        if (conn == null) return "Ton läuft nicht."
        val r = runCatching { nativeStop() }.getOrElse { "Stop-Fehler: ${it.message}" }
        conn?.close()
        conn = null
        return r
    }

    private data class OutIf(val ifNum: Int, val altSetting: Int)

    private fun findOutStreamingInterface(device: UsbDevice): OutIf? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            if (intf.interfaceSubclass != 0x02) continue // AudioStreaming
            for (e in 0 until intf.endpointCount) {
                if (intf.getEndpoint(e).direction == UsbConstants.USB_DIR_OUT) {
                    return OutIf(intf.id, intf.alternateSetting)
                }
            }
        }
        return null
    }
}
