package de.livegigplayer.pro.audio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Liest die rohen USB-Descriptoren eines angeschlossenen USB-Audiogeräts (CQ20B)
 * aus und wertet sie aus — speziell die AudioStreaming-Interfaces mit ihren
 * Alternate-Settings, der Endpoint-Richtung (OUT = Wiedergabe ins Pult) und der
 * Kanalzahl (bNrChannels) je Format.
 *
 * Hintergrund: Androids normale Audio-API (AudioTrack) mischt USB-Ausgabe auf
 * Stereo herunter. Um echtes Multitrack zu erreichen, müsste die App das Gerät
 * direkt über die USB-Host-API (rohes UAC2, isochron) ansteuern. Dieser Scan
 * beweist VORAB, ob das Pult überhaupt einen Mehrkanal-Ausgabepfad anbietet,
 * den man direkt bespielen könnte — bevor dieser große Umbau begonnen wird.
 *
 * getRawDescriptors() erfordert eine geöffnete Verbindung → USB-Host-Berechtigung.
 */
class UsbDescriptorScanner {

    /** Zuletzt gefundenes Zielgerät — für die Berechtigungsanfrage von außen. */
    var target: UsbDevice? = null
        private set

    /** Sucht das erste USB-Audiogerät (bevorzugt Name mit "CQ"). */
    fun findDevice(usbManager: UsbManager?): UsbDevice? {
        val devices = usbManager?.deviceList?.values ?: emptyList()
        target = devices.firstOrNull { it.productName?.contains("CQ", true) == true }
            ?: devices.firstOrNull { hasAudioInterface(it) }
            ?: devices.firstOrNull()
        return target
    }

    private fun hasAudioInterface(d: UsbDevice): Boolean {
        for (i in 0 until d.interfaceCount) {
            if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) return true
        }
        return false
    }

    /**
     * Voll-Scan. Braucht eine bereits berechtigte, offene Verbindung
     * (rawDescriptors). Gibt den kompletten Auswerte-Text zurück.
     */
    fun scan(usbManager: UsbManager?): String {
        val sb = StringBuilder()
        val devices = usbManager?.deviceList?.values ?: emptyList()
        sb.appendLine("USB-Geräte gesamt: ${devices.size}")
        devices.forEach { d ->
            sb.appendLine("• ${d.productName ?: "?"}  VID=${hex(d.vendorId)} PID=${hex(d.productId)} " +
                "if=${d.interfaceCount}")
        }
        val dev = findDevice(usbManager)
        if (dev == null) { sb.appendLine("Kein USB-Gerät gefunden."); return sb.toString() }
        sb.appendLine("\nZiel: ${dev.productName ?: "?"} (VID=${hex(dev.vendorId)} PID=${hex(dev.productId)})")

        if (usbManager?.hasPermission(dev) != true) {
            sb.appendLine("KEINE USB-Berechtigung — bitte im Dialog erlauben und erneut scannen.")
            return sb.toString()
        }

        val conn = usbManager.openDevice(dev)
        if (conn == null) { sb.appendLine("openDevice() fehlgeschlagen."); return sb.toString() }
        try {
            val raw = conn.rawDescriptors
            if (raw == null) { sb.appendLine("rawDescriptors == null."); return sb.toString() }
            sb.appendLine("Roh-Descriptor-Länge: ${raw.size} Bytes")
            sb.append(parseDescriptors(raw))
        } finally {
            conn.close()
        }
        return sb.toString()
    }

    /** Parst die rohe Descriptor-Kette und zieht die relevanten Audio-Infos raus. */
    private fun parseDescriptors(raw: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        // Kontext, während wir durch die Interfaces laufen:
        var curIfClass = -1
        var curIfSub = -1
        var curAlt = -1
        var curIfNum = -1
        var uac2 = false // grob: aus AudioControl-Header (bcdADC) abgeleitet

        fun u8(off: Int) = raw[off].toInt() and 0xFF

        while (i + 1 < raw.size) {
            val len = u8(i)
            if (len < 2) break
            if (i + len > raw.size) break // abgeschnittener Descriptor -> sicher raus
            val type = u8(i + 1)

            when (type) {
                0x04 -> { // INTERFACE
                    curIfNum = u8(i + 2)
                    curAlt = u8(i + 3)
                    val numEp = u8(i + 4)
                    curIfClass = u8(i + 5)
                    curIfSub = u8(i + 6)
                    if (curIfClass == UsbConstants.USB_CLASS_AUDIO) {
                        val subName = when (curIfSub) {
                            0x01 -> "AUDIOCONTROL"
                            0x02 -> "AUDIOSTREAMING"
                            else -> "sub$curIfSub"
                        }
                        sb.appendLine("IF $curIfNum alt=$curAlt $subName eps=$numEp")
                    }
                }
                0x24 -> { // CS_INTERFACE (class-specific)
                    val sub = u8(i + 2)
                    if (curIfClass == UsbConstants.USB_CLASS_AUDIO &&
                        curIfSub == 0x01 && sub == 0x01 && len >= 8) {
                        // AudioControl HEADER: bcdADC bei Offset 3..4
                        val bcd = u8(i + 3) or (u8(i + 4) shl 8)
                        uac2 = bcd >= 0x0200
                        sb.appendLine("  UAC-Version: 0x${bcd.toString(16)} (${if (uac2) "UAC2" else "UAC1"})")
                    }
                    if (curIfSub == 0x02) { // AUDIOSTREAMING class-specific
                        when (sub) {
                            0x01 -> { // AS_GENERAL
                                if (uac2 && len >= 11) {
                                    val nr = u8(i + 10) // UAC2: bNrChannels
                                    sb.appendLine("  → AS_GENERAL (UAC2) bNrChannels=$nr  [IF $curIfNum alt=$curAlt]")
                                } else {
                                    sb.appendLine("  → AS_GENERAL (UAC1) [IF $curIfNum alt=$curAlt]")
                                }
                            }
                            0x02 -> { // FORMAT_TYPE
                                if (!uac2 && len >= 5) {
                                    val nr = u8(i + 4) // UAC1: bNrChannels
                                    sb.appendLine("  → FORMAT_TYPE (UAC1) bNrChannels=$nr  [IF $curIfNum alt=$curAlt]")
                                } else if (len >= 6) {
                                    sb.appendLine("  → FORMAT_TYPE bSubslotSize=${u8(i + 4)} bBitResolution=${u8(i + 5)}")
                                }
                            }
                        }
                    }
                }
                0x05 -> { // ENDPOINT
                    if (curIfClass == UsbConstants.USB_CLASS_AUDIO && curIfSub == 0x02) {
                        val addr = u8(i + 2)
                        val attr = u8(i + 3)
                        val dir = if (addr and 0x80 != 0) "IN(Aufnahme)" else "OUT(Wiedergabe)"
                        val ttype = when (attr and 0x03) {
                            0x01 -> "isochron"; 0x02 -> "bulk"; 0x03 -> "interrupt"; else -> "ctrl"
                        }
                        val maxPkt = u8(i + 4) or (u8(i + 5) shl 8)
                        sb.appendLine("    EP ${hex(addr)} $dir $ttype maxPkt=$maxPkt")
                    }
                }
            }
            i += len
        }
        sb.appendLine("\n→ Gesucht: ein AUDIOSTREAMING-Interface mit OUT(Wiedergabe)-Endpoint")
        sb.appendLine("  und bNrChannels > 2. Das wäre der direkt bespielbare Multitrack-Pfad.")
        return sb.toString()
    }

    private fun hex(v: Int) = "0x%04X".format(v)
}
