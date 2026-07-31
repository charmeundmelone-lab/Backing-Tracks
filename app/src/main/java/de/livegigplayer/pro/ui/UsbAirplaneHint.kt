package de.livegigplayer.pro.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Erinnert an den Flugmodus, solange ein USB-Audiogerät (Mischpult) am Telefon
 * hängt und der Mobilfunk noch sendet.
 *
 * Hintergrund: Die Sendebursts des Mobilfunks koppeln über das USB-Kabel in den
 * Audioweg ein und sind als Knattern in der PA zu hören ("GSM-Sirren"). Am Gerät
 * des Users tritt es ausschließlich bei angeschlossenem USB-Kabel auf.
 *
 * Die App kann den Flugmodus NICHT selbst schalten — das ist seit Android 4.2 eine
 * geschützte Systemeinstellung (WRITE_SECURE_SETTINGS, nur System-Apps oder per adb
 * freigeschaltete Tools). Möglich ist nur: Zustand lesen und mit einem Tap direkt in
 * die passende Systemeinstellung springen.
 *
 * Zeigt sich bewusst NUR bei angeschlossenem USB-Audiogerät (nicht bei jedem
 * App-Start) — sonst stumpft der Hinweis ab und wird auf der Bühne übersehen.
 */
@Composable
fun UsbAirplaneHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var usbAudioAttached by remember { mutableStateOf(hasUsbAudioDevice(context)) }
    var airplaneOn       by remember { mutableStateOf(isAirplaneModeOn(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                // Beide Werte neu lesen: der Anlass ist egal, der Zustand zählt.
                usbAudioAttached = hasUsbAudioDevice(context)
                airplaneOn       = isAirplaneModeOn(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        // RECEIVER_NOT_EXPORTED ist ab Android 14 Pflichtangabe; für geschützte
        // System-Broadcasts wie diese drei ist es der richtige (sichere) Wert.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    if (!usbAudioAttached || airplaneOn) return

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
            "Flugmodus aus — Störgeräusche über USB möglich",
            color = HintAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("Einstellungen ›", color = HintAmber, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

/**
 * True, sobald ein angeschlossenes USB-Gerät ein Audio-Interface anbietet. Ein reines
 * Ladekabel taucht gar nicht erst in der Geräteliste auf; die Klassenprüfung hält
 * zusätzlich Tastaturen/Sticks draußen, damit der Hinweis nur im Bühnenfall kommt.
 */
private fun hasUsbAudioDevice(context: Context): Boolean {
    val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
    return runCatching {
        manager.deviceList.values.any { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }
        }
    }.getOrDefault(false)
}
