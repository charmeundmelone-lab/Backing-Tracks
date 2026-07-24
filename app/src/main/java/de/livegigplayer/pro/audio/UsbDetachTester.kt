package de.livegigplayer.pro.audio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Machbarkeits-Test für den ersten, kritischsten Schritt des nativen UAC2-
 * Multitrack-Umbaus (CQ20B): das Lösen des Kernel-Treibers snd-usb-audio vom
 * AudioStreaming-Interface, ohne Root (Kill-Kriterium 1 aus der Planung).
 *
 * Ablauf:
 *  1. CQ20B finden (via UsbDescriptorScanner-Logik).
 *  2. Das AudioStreaming-Interface mit einem OUT(Wiedergabe)-Endpoint bestimmen
 *     (das ist der Multitrack-Pfad, laut Descriptor-Scan IF 1).
 *  3. Gerät öffnen, rohen Dateideskriptor holen und an den nativen Code geben,
 *     der Detach + Claim + Alt-Setting per usbfs-ioctls versucht.
 *
 * Es wird KEIN Audio geschrieben und nichts dauerhaft übernommen — das Interface
 * wird nativ direkt wieder freigegeben. Nur ein Ja/Nein für die Machbarkeit.
 */
class UsbDetachTester {

    companion object {
        init { System.loadLibrary("nativeusb") }
    }

    /** Native: Detach + Claim + SetInterface(alt) auf dem gegebenen fd. Report-Text zurück. */
    private external fun detachClaimSetAlt(fd: Int, iface: Int, alt: Int): String

    /**
     * Führt den Test aus. Erwartet ein bereits BERECHTIGTES Gerät (USB-Permission).
     * Gibt den kompletten, am Handy anzeigbaren Report-Text zurück.
     */
    fun run(usbManager: UsbManager?, device: UsbDevice?): String {
        val sb = StringBuilder()
        if (usbManager == null || device == null) return "Kein USB-Gerät.\n"
        if (!usbManager.hasPermission(device)) {
            return "KEINE USB-Berechtigung — erst erlauben, dann erneut testen.\n"
        }

        val target = findOutStreamingInterface(device)
        if (target == null) {
            sb.appendLine("Kein AudioStreaming-Interface mit OUT-Endpoint gefunden.")
            sb.appendLine("Ohne diesen Pfad gibt es keinen Multitrack-Ausgang.")
            return sb.toString()
        }
        sb.appendLine("Ziel: ${device.productName ?: "?"}")
        sb.appendLine("Interface ${target.ifNum}, Alt-Setting ${target.altSetting} (OUT-Endpoint)")
        sb.appendLine()

        val conn = usbManager.openDevice(device)
            ?: return sb.append("openDevice() fehlgeschlagen.\n").toString()
        try {
            val fd = conn.fileDescriptor
            if (fd < 0) return sb.append("Ungültiger Dateideskriptor.\n").toString()
            sb.append(detachClaimSetAlt(fd, target.ifNum, target.altSetting))
        } finally {
            conn.close()
        }
        return sb.toString()
    }

    private data class OutIf(val ifNum: Int, val altSetting: Int)

    /**
     * Sucht das AudioStreaming-Interface, das einen OUT-Endpoint (Wiedergabe)
     * anbietet. Die USB-Host-API listet Alt-Settings als eigene UsbInterface-
     * Objekte mit gleicher id — wir nehmen das mit einem OUT-Endpoint.
     */
    private fun findOutStreamingInterface(device: UsbDevice): OutIf? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            // AudioStreaming = subclass 0x02
            if (intf.interfaceSubclass != 0x02) continue
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    return OutIf(intf.id, intf.alternateSetting)
                }
            }
        }
        return null
    }
}
