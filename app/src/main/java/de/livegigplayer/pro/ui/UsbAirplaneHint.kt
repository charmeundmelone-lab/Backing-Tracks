package de.livegigplayer.pro.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// Warnfarbe — bewusst weder Volt (heißt "läuft/aktiv") noch Rot (heißt "Fehler"):
// das hier ist ein Hinweis, kein Defekt.
private val HintAmber   = Color(0xFFFFB020)
private val HintAmberBg = Color(0x2EFFB020)

/**
 * Erinnert an den Flugmodus, solange ein Kabel zur PA am Telefon hängt und der
 * Mobilfunk noch sendet.
 *
 * Hintergrund: Die Sendebursts des Mobilfunks koppeln über das Kabel in den Audioweg
 * ein und sind als Knattern in der PA zu hören ("GSM-Sirren"). Am Gerät des Users
 * tritt es ausschließlich bei angeschlossenem Kabel auf, nie über die Luft.
 *
 * Die App kann den Flugmodus NICHT selbst schalten — das ist seit Android 4.2 eine
 * geschützte Systemeinstellung (WRITE_SECURE_SETTINGS, nur System-Apps oder per adb
 * freigeschaltete Tools). Möglich ist nur: Zustand lesen und mit einem Tap direkt in
 * die passende Systemeinstellung springen.
 *
 * Erkennung läuft über den AudioManager, NICHT über UsbManager: Ein USB-auf-Klinke-
 * Adapter (aktuelles Setup des Users) erscheint gar nicht in `UsbManager.deviceList`,
 * weil Android ihn direkt ans Audio-System durchreicht — die erste Fassung dieses
 * Hinweises blieb genau deshalb unsichtbar. Der UsbManager-Zweig bleibt als Ergänzung
 * für den späteren Multitrack-Fall bestehen, in dem der Kernel-Audiotreiber vom
 * Interface gelöst wird und das Pult dann nur noch als rohes USB-Gerät dasteht.
 */
@Composable
fun UsbAirplaneHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var cableAttached by remember { mutableStateOf(hasWiredAudioOutput(context)) }
    var airplaneOn    by remember { mutableStateOf(isAirplaneModeOn(context)) }

    DisposableEffect(context) {
        fun refresh() {
            cableAttached = hasWiredAudioOutput(context)
            airplaneOn    = isAirplaneModeOn(context)
        }

        // Audio-Seite: meldet Klinken-/USB-Adapter zuverlässig, auch wenn sie in der
        // USB-Geräteliste fehlen.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }
        audioManager?.registerAudioDeviceCallback(audioCallback, null)

        // Flugmodus-Wechsel und rohe USB-Geräte (ohne Audio-Anbindung).
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        // RECEIVER_NOT_EXPORTED ist ab Android 14 Pflichtangabe; für geschützte
        // System-Broadcasts wie diese drei ist es der richtige (sichere) Wert.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            audioManager?.unregisterAudioDeviceCallback(audioCallback)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    if (!cableAttached || airplaneOn) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HintAmberBg)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null,
            tint = HintAmber, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Flugmodus aus — Störgeräusche über das Kabel möglich",
            color = HintAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("Einstellungen ›", color = HintAmber, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

/**
 * Diagnose-Abschnitt für den USB-Dialog: zeigt schwarz auf weiß, was das Telefon
 * gerade meldet und ob der Flugmodus-Hinweis danach erscheinen müsste.
 *
 * Grund: Ohne PC gibt es kein Logcat — ohne diese Anzeige lässt sich ein "der Balken
 * kommt nicht" nicht von einem "das Kabel wird gar nicht gemeldet" unterscheiden.
 * Ein Screenshot davon reicht zur Auswertung.
 */
@Composable
fun WiredAudioDiagnostic(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Zählt hoch, wenn "Neu einlesen" gedrückt wird — Kabel kann bei offenem Dialog
    // gesteckt werden, ohne den Dialog schließen zu müssen.
    var reloads by remember { mutableStateOf(0) }

    val airplane   = remember(reloads) { isAirplaneModeOn(context) }
    val sinks      = remember(reloads) { wiredAudioSinkLabels(context) }
    val rawUsb     = remember(reloads) { rawUsbDeviceLabels(context) }
    val hintShown  = (sinks.isNotEmpty() || rawUsb.any { it.contains("Audio") }) && !airplane

    Column(modifier = modifier) {
        Text("Flugmodus-Hinweis — Diagnose", color = HintAmber,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Flugmodus: ${if (airplane) "AN" else "AUS"}", color = Color.White, fontSize = 12.sp)
        Text("Hinweis müsste erscheinen: ${if (hintShown) "JA" else "NEIN"}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        Text("Kabel-Audio-Ausgänge (${sinks.size}):", color = Color.White, fontSize = 12.sp)
        if (sinks.isEmpty()) {
            Text("  – keine –", color = Color.Gray, fontSize = 12.sp)
        } else {
            sinks.forEach { Text("  • $it", color = Color.Gray, fontSize = 12.sp) }
        }

        Text("USB-Geräte (${rawUsb.size}):", color = Color.White, fontSize = 12.sp)
        if (rawUsb.isEmpty()) {
            Text("  – keine –", color = Color.Gray, fontSize = 12.sp)
        } else {
            rawUsb.forEach { Text("  • $it", color = Color.Gray, fontSize = 12.sp) }
        }

        Text(
            "Neu einlesen",
            color = HintAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { reloads++ }
                .padding(vertical = 6.dp)
        )
    }
}

/** Alle kabelgebundenen Audio-Ausgänge als lesbare Zeilen ("USB_HEADSET — Name"). */
private fun wiredAudioSinkLabels(context: Context): List<String> {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
    return runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isWiredSink(it.type) }
            .map { "${audioTypeName(it.type)} — ${it.productName}" }
    }.getOrDefault(emptyList())
}

/** Rohe USB-Geräte samt Interface-Klassen ("22F0:0022 — Klassen: Audio, HID"). */
private fun rawUsbDeviceLabels(context: Context): List<String> {
    val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
    return runCatching {
        manager.deviceList.values.map { device ->
            val classes = (0 until device.interfaceCount)
                .map { usbClassName(device.getInterface(it).interfaceClass) }
                .distinct()
                .joinToString(", ")
            val vid = "%04X".format(device.vendorId)
            val pid = "%04X".format(device.productId)
            "$vid:$pid — Klassen: $classes"
        }
    }.getOrDefault(emptyList())
}

private fun audioTypeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_USB_DEVICE      -> "USB_DEVICE"
    AudioDeviceInfo.TYPE_USB_HEADSET     -> "USB_HEADSET"
    AudioDeviceInfo.TYPE_USB_ACCESSORY   -> "USB_ACCESSORY"
    AudioDeviceInfo.TYPE_WIRED_HEADSET   -> "KLINKE_HEADSET"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES-> "KLINKE_KOPFHOERER"
    AudioDeviceInfo.TYPE_LINE_ANALOG     -> "LINE_ANALOG"
    AudioDeviceInfo.TYPE_LINE_DIGITAL    -> "LINE_DIGITAL"
    AudioDeviceInfo.TYPE_AUX_LINE        -> "AUX"
    AudioDeviceInfo.TYPE_DOCK            -> "DOCK"
    else                                 -> "TYP_$type"
}

private fun usbClassName(cls: Int): String = when (cls) {
    UsbConstants.USB_CLASS_AUDIO       -> "Audio"
    UsbConstants.USB_CLASS_HID         -> "HID"
    UsbConstants.USB_CLASS_MASS_STORAGE-> "Speicher"
    UsbConstants.USB_CLASS_COMM        -> "Comm"
    UsbConstants.USB_CLASS_VIDEO       -> "Video"
    UsbConstants.USB_CLASS_MISC        -> "Misc"
    UsbConstants.USB_CLASS_PER_INTERFACE -> "PerInterface"
    UsbConstants.USB_CLASS_VENDOR_SPEC -> "Hersteller"
    else                               -> "Klasse $cls"
}

private fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

private fun hasWiredAudioOutput(context: Context): Boolean =
    hasWiredAudioSink(context) || hasRawUsbAudioInterface(context)

/**
 * True, sobald irgendein kabelgebundener Audio-Ausgang aktiv ist — USB-Interface,
 * USB-Headset-Adapter, Klinke, Line-Out, Dock. Absichtlich breit: eingestreut wird
 * über JEDES Kabel, das am Telefon steckt, egal ob digital oder analog. Eingebauter
 * Lautsprecher und Bluetooth zählen bewusst nicht.
 */
private fun hasWiredAudioSink(context: Context): Boolean {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isWiredSink(it.type) }
    }.getOrDefault(false)
}

private fun isWiredSink(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_DOCK -> true
    else -> false
}

/**
 * Fallback für den späteren Multitrack-Betrieb: Dort wird der Kernel-Treiber
 * `snd-usb-audio` vom Interface gelöst, das Pult verschwindet dadurch aus der
 * Audio-Geräteliste und ist nur noch als rohes USB-Gerät sichtbar.
 */
private fun hasRawUsbAudioInterface(context: Context): Boolean {
    val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
    return runCatching {
        manager.deviceList.values.any { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }
        }
    }.getOrDefault(false)
}
