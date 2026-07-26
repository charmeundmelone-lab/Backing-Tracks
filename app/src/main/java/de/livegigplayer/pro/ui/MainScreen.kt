package de.livegigplayer.pro.ui

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import de.livegigplayer.pro.audio.PdfLyricsImporter
import de.livegigplayer.pro.audio.UsbDescriptorScanner
import de.livegigplayer.pro.audio.UsbDetachTester
import de.livegigplayer.pro.audio.UsbIsoToneTester
import de.livegigplayer.pro.audio.UsbToneTester
import de.livegigplayer.pro.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ── Palette ──────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0A0A0A)
private val BgCard      = Color(0xFF1A1A1A)
private val BgTrack     = Color(0xFF2A2A2A)
private val BgBatch     = Color(0x33EAB308)  // #EAB308 @ 20%
private val BgPlayer    = Color(0xFF1E293B)
private val Volt        = Color(0xFFE8FF00)
private val VoltDim     = Color(0x8CE8FF00)
private val White       = Color(0xFFFFFFFF)
private val Gray        = Color(0xFF777777)
private val RedStop     = Color(0xFFDC2626)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun MainScreen(vm: PlayerViewModel = viewModel(), gigVm: GigViewModel = viewModel()) {
    val context       = LocalContext.current
    val currentSong   by vm.currentSong.collectAsState()
    val isPlaying     by vm.isPlaying.collectAsState()
    val trackMode     by vm.trackMode.collectAsState()
    val showMixer     by vm.showMixer.collectAsState()
    val showLyrics    by vm.showLyrics.collectAsState()
    val positionMs    by vm.positionMs.collectAsState()
    val durationMs    by vm.durationMs.collectAsState()
    val isScanning    by vm.isScanning.collectAsState()
    val scanProgress  by vm.scanProgress.collectAsState()
    val nextSong      by vm.nextSong.collectAsState()
    val loopActive    by vm.loopActive.collectAsState()
    val loopState     by vm.loopState.collectAsState()
    val loopStartMs   by vm.loopStartMs.collectAsState()
    val loopEndMs     by vm.loopEndMs.collectAsState()
    val isLoopModified    by vm.isLoopModified.collectAsState()
    val isArmed           by vm.isArmed.collectAsState()
    val isLoopActiveLive  by vm.isLoopActiveLive.collectAsState()
    val isExitPending     by vm.isExitPending.collectAsState()
    val currentPlaylistId by vm.currentPlaylistId.collectAsState()
    val isGigSetMode      by vm.isGigSetMode.collectAsState()
    val activeEndAction   by vm.activeEndAction.collectAsState()
    val activeSetId       by gigVm.activeSetId.collectAsState()
    val loopHint          by vm.loopHint.collectAsState()

    var selectedTab      by remember { mutableStateOf(0) }  // 0=Archiv 1=Sets
    var isLocked         by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) Toast.makeText(context, "❌ Kein Ordner gewählt (URI=null)", Toast.LENGTH_LONG).show()
        else { Toast.makeText(context, "✅ Ordner erkannt – starte Import…", Toast.LENGTH_SHORT).show(); vm.importFolder(context, uri) }
    }

    // Kurzer, wiederholter Hinweis beim A/B-Loop-Setzen — der LOOP-Button hat je
    // nach Kontext drei verschiedene Bedeutungen (Archiv A/B-Setzer, Set-Modus
    // Ein/Aus-Schalter, Live-Exit), ohne Erklärung im UI selbst nicht ersichtlich.
    LaunchedEffect(loopHint) {
        loopHint?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearLoopHint() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(BgDeep).systemBarsPadding()
        ) {
            // Top bar (enthält Tab-Navigation)
            TopBar(
                selectedTab   = selectedTab,
                onTabSelect   = { selectedTab = it },
                isLocked      = isLocked,
                onLockToggle  = { isLocked = !isLocked },
                onMixerToggle = { vm.toggleMixer() },
                onImport      = { importLauncher.launch(null) },
                onDeleteAll   = { vm.deleteAllSongs() }
            )

            // Tab content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ArchivTab(vm = vm, gigVm = gigVm, isLocked = isLocked)
                    1 -> GigManagementScreen(gigVm = gigVm, playerVm = vm, isLocked = isLocked)
                }
            }

            // Global Player — fest verankert, in beiden Tabs sichtbar
            GlobalPlayer(
                song             = currentSong,
                nextSong         = nextSong,
                isPlaying        = isPlaying,
                loopState        = loopState,
                isArmed          = isArmed,
                isLoopActiveLive = isLoopActiveLive,
                isExitPending    = isExitPending,
                isInSetMode      = currentPlaylistId != null,
                isGigSetMode     = isGigSetMode,
                positionMs       = positionMs,
                durationMs       = durationMs,
                loopStartMs      = loopStartMs,
                loopEndMs        = loopEndMs,
                activeEndAction  = activeEndAction,
                onSeekTo         = { ms -> vm.seekTo(ms) },
                onPlayPause      = { vm.togglePlayPause() },
                onStop           = { vm.stopPlayback() },
                onToggleLoop     = { vm.onLoopButtonPressed(positionMs) },
                onSetLoopButton  = { vm.onSetLoopButtonPressed() },
                onOpenLyrics     = { vm.openLyrics() },
                onCycleEndAction = {
                    val sid = activeSetId
                    val song = currentSong
                    if (sid != null && song != null) {
                        val next = (activeEndAction + 1) % 3
                        vm.activeEndAction.value = next   // sofort im UI sichtbar
                        gigVm.cycleEndAction(sid, song.id, activeEndAction)
                    }
                }
            )
            // A/B Loop Panel — nur im Archiv-Modus (nicht im Gig-Set-Modus)
            if (!isGigSetMode) LoopPanel(
                loopState      = loopState,
                loopStartMs    = loopStartMs,
                loopEndMs      = loopEndMs,
                durationMs     = durationMs,
                isLoopModified = isLoopModified,
                onNudgeStart   = { vm.nudgeLoopStart(it) },
                onNudgeEnd     = { vm.nudgeLoopEnd(it) },
                onRangeChanged = { s, e -> vm.setLoopRange(s, e) },
                onSave         = { vm.executeHardDatabaseSave() },
                onClearLoop    = { vm.clearLoop() }
            )
        }

        // Mixer overlay
        MixerOverlay(
            visible        = showMixer,
            song           = currentSong,
            trackMode      = trackMode,
            isPlaying      = isPlaying,
            onVolumeChange = { name, db -> vm.updateMixerVolume(name, db) },
            onReset        = { vm.resetAllMixer() },
            onPlayPause    = { vm.togglePlayPause() },
            onStop         = { vm.stopPlayback() },
            onClose        = { vm.closeMixer() }
        )

        // Lyrics-Teleprompter — Hochkant, Auto-Scroll an echter Wiedergabeposition
        LyricsOverlay(
            visible          = showLyrics,
            song             = currentSong,
            positionMs       = positionMs,
            durationMs       = durationMs,
            isPlaying        = isPlaying,
            onClose          = { vm.closeLyrics() },
            onSetLyricsSyncPoints = { _, points -> currentSong?.let { vm.updateLyricsSyncPoints(it, points) } },
            debugLog         = vm.lyricsDebugLog,
            onLogDebug       = { vm.logLyricsDebug(it) },
            onLogWarn        = { vm.logLyricsWarn(it) }
        )

        // Scan overlay
        if (isScanning) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Volt, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SCANNE ORDNER", color = Volt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (scanProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(scanProgress, color = Gray, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    selectedTab: Int, onTabSelect: (Int) -> Unit,
    isLocked: Boolean, onLockToggle: () -> Unit,
    onMixerToggle: () -> Unit, onImport: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var menuExpanded        by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showUsbDiagnostic   by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(BgDeep)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab-Icons links
        IconButton(onClick = { onTabSelect(0) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = "Archiv",
                tint = if (selectedTab == 0) Volt else Gray,
                modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = { onTabSelect(1) }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.QueueMusic, contentDescription = "Sets",
                tint = if (selectedTab == 1) Volt else Gray,
                modifier = Modifier.size(30.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Aktions-Icons rechts
        IconButton(onClick = onImport) {
            Icon(Icons.Filled.AddCircleOutline, contentDescription = "Import",
                tint = Gray, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onMixerToggle) {
            Icon(Icons.Filled.Tune, contentDescription = "Mixer",
                tint = Gray, modifier = Modifier.size(26.dp))
        }
        // Diagnose für geplantes USB-Multitrack-Feature (CQ20B): zeigt, wie viele
        // Kanäle Android für ein angeschlossenes USB-Audiogerät tatsächlich meldet,
        // BEVOR die Audio-Engine für echtes Multichannel-Output umgebaut wird.
        IconButton(onClick = { showUsbDiagnostic = true }) {
            Icon(Icons.Filled.Usb, contentDescription = "USB-Audio-Diagnose",
                tint = Gray, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Lock",
                tint = if (isLocked) Volt else Gray,
                modifier = Modifier.size(26.dp)
            )
        }
        if (selectedTab == 0) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menü",
                        tint = Gray, modifier = Modifier.size(26.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = BgCard
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle Songs löschen", color = RedStop) },
                        onClick = { menuExpanded = false; showDeleteAllDialog = true }
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor   = BgCard,
            title = { Text("Alle Songs löschen?", color = White, fontWeight = FontWeight.Bold) },
            text  = { Text(
                "Wirklich ALLE Songs aus dem Archiv löschen? Diese Aktion kann nicht rückgängig gemacht werden.",
                color = Gray, fontSize = 13.sp
            ) },
            confirmButton = {
                TextButton(onClick = { showDeleteAllDialog = false; onDeleteAll() }) {
                    Text("Alle löschen", color = RedStop, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Abbrechen", color = Gray)
                }
            }
        )
    }

    if (showUsbDiagnostic) {
        UsbAudioDiagnosticDialog(onDismiss = { showUsbDiagnostic = false })
    }
}

// Zeigt für jedes aktuell angeschlossene USB-Audiogerät die von Android gemeldete
// Kanalzahl (AudioDeviceInfo.channelCounts) — reiner Lesevorgang, keine eigene
// Berechtigung nötig. Diente als schneller Vorab-Check fürs geplante
// USB-Multitrack-Feature (CQ20B): bevor die Audio-Engine für echtes
// Multichannel-Output umgebaut wird, muss klar sein, ob Android für das konkrete
// Handy überhaupt mehr als 2 Kanäle über USB freigibt.
@Composable
private fun UsbAudioDiagnosticDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val usbDevices = remember {
        audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            ?: emptyList()
    }

    // Dauerton-Test: beweist diskretes Mehrkanal-Output übers USB-Gerät.
    val tester = remember { UsbToneTester() }
    var toneRunning by remember { mutableStateOf(false) }
    var toneInfo by remember { mutableStateOf<String?>(null) }
    var diag by remember { mutableStateOf<String?>(null) }
    // Bei Dialog-Schließen (oder Recompose-Ende) Ton sicher stoppen.
    DisposableEffect(Unit) { onDispose { tester.stop() } }

    // USB-Descriptor-Scan (direkte USB-Host-API) + Berechtigungs-Handling.
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    val scanner = remember { UsbDescriptorScanner() }
    var scanResult by remember { mutableStateOf<String?>(null) }

    // Nativer Detach-/Claim-Test (Kill-Kriterium 1: snd-usb-audio ohne Root lösen).
    var detachResult by remember { mutableStateOf<String?>(null) }

    // Nativer isochroner Sinuston-Test (Kill-Kriterium 2: diskreter Ton, kein Knacken).
    val isoTone = remember { UsbIsoToneTester() }
    var isoRunning by remember { mutableStateOf(false) }
    var isoResult by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { if (isoTone.running) isoTone.stop() } }
    val usbPermAction = remember { context.packageName + ".USB_PERMISSION" }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == usbPermAction) {
                    scanResult = scanner.scan(usbManager) // Berechtigung ggf. jetzt erteilt
                }
            }
        }
        val filter = IntentFilter(usbPermAction)
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    AlertDialog(
        onDismissRequest = { tester.stop(); onDismiss() },
        containerColor   = BgCard,
        title = { Text("USB-Audio-Diagnose", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (usbDevices.isEmpty()) {
                    Text(
                        "Kein USB-Audiogerät verbunden. CQ20B per USB-C→USB-B anschließen und Dialog erneut öffnen.",
                        color = Gray, fontSize = 13.sp
                    )
                } else {
                    usbDevices.forEach { device ->
                        val channels = device.channelCounts
                        val channelText = if (channels.isEmpty()) "unbekannt" else channels.joinToString(", ")
                        Text(
                            "${device.productName} — Kanäle: $channelText",
                            color = White, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Dauerton-Test: spielt gleichzeitig auf ALLEN Kanälen je einen eigenen Ton. Am CQ20B prüfen, ob mehrere Eingangskanäle getrennt Pegel zeigen.",
                        color = Gray, fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            if (toneRunning) {
                                tester.stop()
                                toneRunning = false
                                toneInfo = "Ton gestoppt."
                            } else {
                                val ch = tester.start(audioManager)
                                if (ch != null) {
                                    toneRunning = true
                                    toneInfo = "Läuft auf $ch Kanälen (jeder Kanal eigener Ton)."
                                } else {
                                    toneInfo = "Start fehlgeschlagen — siehe Diagnose unten."
                                }
                            }
                            diag = tester.diagnostics()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (toneRunning) RedStop else Volt
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            if (toneRunning) "Dauerton stoppen" else "Dauerton starten",
                            color = if (toneRunning) White else BgDeep, fontWeight = FontWeight.Bold
                        )
                    }
                    toneInfo?.let {
                        Text(it, color = White, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    diag?.let { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Diagnose", color = Volt, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { diag = tester.diagnostics() }) {
                                Text("Aktualisieren", color = Gray, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "USB-Multitrack-Diagnose")
                                    putExtra(Intent.EXTRA_TEXT, tester.diagnostics())
                                }
                                context.startActivity(
                                    Intent.createChooser(share, "Diagnose teilen"))
                            }) {
                                Text("Teilen", color = Volt, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            d,
                            color = White, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text("USB-Struktur-Scan (für echtes Multitrack)",
                        color = Gray, fontSize = 12.sp)
                    Button(
                        onClick = {
                            val dev = scanner.findDevice(usbManager)
                            when {
                                dev == null -> scanResult = "Kein USB-Gerät gefunden."
                                usbManager?.hasPermission(dev) == true ->
                                    scanResult = scanner.scan(usbManager)
                                else -> {
                                    val pi = PendingIntent.getBroadcast(
                                        context, 0,
                                        Intent(usbPermAction).setPackage(context.packageName),
                                        PendingIntent.FLAG_IMMUTABLE
                                    )
                                    usbManager?.requestPermission(dev, pi)
                                    scanResult = "USB-Berechtigung angefragt — bitte bestätigen, dann erscheint das Ergebnis automatisch."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BgTrack),
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("USB-Struktur scannen", color = White, fontSize = 13.sp) }
                    scanResult?.let { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Scan-Ergebnis", color = Volt, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "USB-Descriptor-Scan CQ20B")
                                    putExtra(Intent.EXTRA_TEXT, s)
                                }
                                context.startActivity(Intent.createChooser(share, "Scan teilen"))
                            }) {
                                Text("Teilen", color = Volt, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            s,
                            color = White, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text("Detach-Test (nativ, für echtes Multitrack)",
                        color = Gray, fontSize = 12.sp)
                    Text(
                        "Prüft, ob sich der Kernel-Treiber (snd-usb-audio) ohne Root vom Pult-Interface lösen lässt — die Voraussetzung fürs isochrone Multitrack. Kein Audio, gibt das Interface danach wieder frei.",
                        color = Gray, fontSize = 11.sp
                    )
                    Button(
                        onClick = {
                            val dev = scanner.findDevice(usbManager)
                            when {
                                dev == null -> detachResult = "Kein USB-Gerät gefunden."
                                usbManager?.hasPermission(dev) == true ->
                                    detachResult = runCatching {
                                        UsbDetachTester().run(usbManager, dev)
                                    }.getOrElse { "Native Bibliothek nicht ladbar: ${it.message}" }
                                else -> {
                                    val pi = PendingIntent.getBroadcast(
                                        context, 0,
                                        Intent(usbPermAction).setPackage(context.packageName),
                                        PendingIntent.FLAG_IMMUTABLE
                                    )
                                    usbManager?.requestPermission(dev, pi)
                                    detachResult = "USB-Berechtigung angefragt — bitte bestätigen und dann erneut auf \"Detach testen\" tippen."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BgTrack),
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Detach testen", color = White, fontSize = 13.sp) }
                    detachResult?.let { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Detach-Ergebnis", color = Volt, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "USB-Detach-Test CQ20B")
                                    putExtra(Intent.EXTRA_TEXT, r)
                                }
                                context.startActivity(Intent.createChooser(share, "Ergebnis teilen"))
                            }) {
                                Text("Teilen", color = Volt, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            r,
                            color = White, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.padding(vertical = 6.dp))
                    Text("Sinuston-Test (nativ, isochron) — Kanal 9",
                        color = Gray, fontSize = 12.sp)
                    Text(
                        "Schreibt einen 440-Hz-Ton isochron auf GENAU Kanal 9 (alle anderen still). Am CQ20B prüfen: zeigt nur ein Return Pegel? Beim Stoppen kommt die Fehlerstatistik (Knacken?).",
                        color = Gray, fontSize = 11.sp
                    )
                    Button(
                        onClick = {
                            if (isoRunning) {
                                isoResult = isoTone.stop()
                                isoRunning = false
                            } else {
                                val dev = scanner.findDevice(usbManager)
                                when {
                                    dev == null -> isoResult = "Kein USB-Gerät gefunden."
                                    usbManager?.hasPermission(dev) == true -> {
                                        // Kanal 9 = 0-basierter Index 8
                                        isoResult = isoTone.start(usbManager, dev, 8)
                                        isoRunning = isoTone.running
                                    }
                                    else -> {
                                        val pi = PendingIntent.getBroadcast(
                                            context, 0,
                                            Intent(usbPermAction).setPackage(context.packageName),
                                            PendingIntent.FLAG_IMMUTABLE
                                        )
                                        usbManager?.requestPermission(dev, pi)
                                        isoResult = "USB-Berechtigung angefragt — bitte bestätigen und dann erneut auf \"Ton starten\" tippen."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isoRunning) RedStop else Volt
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            if (isoRunning) "Ton stoppen" else "Ton starten (Kanal 9)",
                            color = if (isoRunning) White else BgDeep, fontWeight = FontWeight.Bold
                        )
                    }
                    isoResult?.let { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Ton-Ergebnis", color = Volt, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "USB-Sinuston-Test CQ20B")
                                    putExtra(Intent.EXTRA_TEXT, r)
                                }
                                context.startActivity(Intent.createChooser(share, "Ergebnis teilen"))
                            }) {
                                Text("Teilen", color = Volt, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            r,
                            color = White, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { tester.stop(); if (isoTone.running) isoTone.stop(); onDismiss() }) {
                Text("Schließen", color = Volt)
            }
        }
    )
}

// ── Tab A: Archiv ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArchivTab(vm: PlayerViewModel, gigVm: GigViewModel, isLocked: Boolean) {
    val context       = LocalContext.current
    val songs         by vm.filteredSongs.collectAsState()
    val allSongs      by vm.songs.collectAsState()
    val currentSong   by vm.currentSong.collectAsState()
    val selectedIds   by vm.selectedIds.collectAsState()
    val importStatus  by vm.importStatus.collectAsState()
    val searchQuery   by vm.searchQuery.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    val activeSetId              by gigVm.activeSetId.collectAsState()
    val completedSongIdsInSet    by gigVm.completedSongIdsInActiveSet.collectAsState()
    val plannedSongIdsInGig      by gigVm.songIdsInSelectedGig.collectAsState()

    var searchActive     by remember { mutableStateOf(false) }
    var editSheet        by remember { mutableStateOf<Song?>(null) }
    var showSetPicker    by remember { mutableStateOf(false) }
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope            = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            active = searchActive,
            query  = searchQuery,
            onToggle = { searchActive = !searchActive; if (!searchActive) vm.setSearchQuery("") },
            onChange = { vm.setSearchQuery(it) }
        )

        if (songs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null,
                        tint = Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (allSongs.isEmpty()) "Noch keine Songs importiert" else "Keine Treffer",
                        color = Gray, fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when {
                            allSongs.isEmpty() && importStatus.isNotEmpty() -> importStatus
                            allSongs.isEmpty() -> "Tippe oben auf + um einen Ordner zu importieren"
                            else -> "Suchbegriff anpassen oder Suche schließen"
                        },
                        color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isCompletedInActiveSet = song.id in completedSongIdsInSet
                    val isPlannedInGig         = song.id in plannedSongIdsInGig
                    ArchivSongRow(
                        index                  = index + 1,
                        song                   = song,
                        selected               = song.id == currentSong?.id,
                        isBatchSelected        = song.id in selectedIds,
                        isLocked               = isLocked,
                        selectionMode          = selectionMode,
                        isCompletedInActiveSet = isCompletedInActiveSet,
                        isPlannedInGig         = isPlannedInGig,
                        onPlay          = { if (!isLocked) vm.selectSong(song, context) },
                        onToggleSelect  = { vm.toggleSelect(song.id) },
                        onActivateBatch = { vm.toggleSelect(song.id) },
                        onOpenSheet     = { editSheet = song },
                        onDelete        = { vm.deleteSong(song) },
                        onQueueNext     = {
                            if (activeSetId != null) {
                                gigVm.insertSpontaneousNext(activeSetId!!, song, vm)
                                Toast.makeText(context, "★ ${song.title} → nächster", Toast.LENGTH_SHORT).show()
                            } else vm.addToQueueNext(song)
                        },
                        onQueueEnd      = {
                            if (activeSetId != null) {
                                gigVm.insertSpontaneousLater(activeSetId!!, song, vm)
                                Toast.makeText(context, "★ ${song.title} → später", Toast.LENGTH_SHORT).show()
                            } else vm.addToQueueEnd(song)
                        }
                    )
                }
            }
        }

        // Batch genre bar + Set-Zuweisung
        if (selectionMode) {
            GenreBar(
                count      = selectedIds.size,
                onGenre    = { vm.applyGenre(it) },
                onClear    = { vm.clearSelection() },
                onAddToSet = { showSetPicker = true }
            )
        }
    }

    // Set-Picker Dialog
    if (showSetPicker) {
        val orderedIds = songs.filter { it.id in selectedIds }.map { it.id }
        AddToSetDialog(
            gigVm      = gigVm,
            songCount  = selectedIds.size,
            songIds    = orderedIds,
            onConfirm  = { setId ->
                gigVm.addSongsToSet(setId, orderedIds, vm)
                vm.clearSelection()
                showSetPicker = false
            },
            onDismiss  = { showSetPicker = false }
        )
    }

    // Bottom-Sheet Editor
    if (editSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { editSheet = null },
            sheetState       = sheetState,
            containerColor   = BgCard
        ) {
            SongEditorSheet(
                song             = editSheet!!,
                songs            = songs,
                onSave           = { t, ar, bpmStr, lyrics ->
                    val bpm = bpmStr.toIntOrNull() ?: editSheet!!.bpm
                    vm.updateTitle(editSheet!!, t)
                    vm.updateArtist(editSheet!!, ar)
                    if (lyrics != editSheet!!.lyrics) vm.updateLyrics(editSheet!!, lyrics)
                    scope.launch { sheetState.hide(); editSheet = null }
                },
                onAutoStopChange = { enabled -> vm.updateAutoStop(editSheet!!, enabled) },
                onCapoChange     = { delta -> vm.updateCapo(editSheet!!, delta) },
                onNavigate       = { newSong -> editSheet = newSong },
                onDismiss        = { scope.launch { sheetState.hide(); editSheet = null } }
            )
        }
    }
}

// ── Archiv Song Row ───────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivSongRow(
    index: Int, song: Song,
    selected: Boolean, isBatchSelected: Boolean,
    isLocked: Boolean, selectionMode: Boolean,
    isCompletedInActiveSet: Boolean = false,
    isPlannedInGig: Boolean = false,
    onPlay: () -> Unit, onToggleSelect: () -> Unit, onActivateBatch: () -> Unit,
    onOpenSheet: () -> Unit, onDelete: () -> Unit,
    onQueueNext: () -> Unit, onQueueEnd: () -> Unit
) {
    var dragX                 by remember { mutableStateOf(0f) }
    var showDeleteDialog      by remember { mutableStateOf(false) }
    var showAlreadyPlayedDialog by remember { mutableStateOf(false) }
    var pendingAction         by remember { mutableStateOf<(() -> Unit)?>(null) }

    // rememberUpdatedState: der pointerInput-Block (Key = selectionMode/isLocked)
    // startet beim Swipen nicht neu. Ohne das bleiben onQueueNext/onQueueEnd auf
    // den damaligen activeSetId eingefroren → Song landet im falschen / alten Set.
    val latestNext      by rememberUpdatedState(onQueueNext)
    val latestEnd       by rememberUpdatedState(onQueueEnd)
    val latestCompleted by rememberUpdatedState(isCompletedInActiveSet)

    fun guarded(action: () -> Unit) {
        if (latestCompleted) { pendingAction = action; showAlreadyPlayedDialog = true }
        else action()
    }

    if (showAlreadyPlayedDialog) {
        AlertDialog(
            onDismissRequest = { showAlreadyPlayedDialog = false },
            containerColor   = BgCard,
            title  = { Text("Heute bereits gespielt.", color = White, fontWeight = FontWeight.Bold) },
            text   = { Text("Trotzdem verwenden?", color = Gray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showAlreadyPlayedDialog = false; pendingAction?.invoke() }) {
                    Text("Ja", color = Volt, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadyPlayedDialog = false }) {
                    Text("Abbrechen", color = Gray)
                }
            }
        )
    }

    val bgColor = when {
        isBatchSelected -> BgBatch
        selected        -> BgTrack
        else            -> BgCard
    }
    val rowAlpha = if (isCompletedInActiveSet || isPlannedInGig) 0.4f else 1f

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = BgCard,
            title = { Text("Song löschen?", color = White, fontWeight = FontWeight.Bold) },
            text  = { Text("\"${song.title}\" wirklich aus dem Archiv löschen?",
                color = Gray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Löschen", color = RedStop, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen", color = Gray)
                }
            }
        )
    }

    val interactive = !isLocked && !selectionMode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            // Dimmen ohne Offscreen-Puffer (ModulateAlpha) — sonst legt Compose bei
            // alpha < 1 pro Zeile einen Layer an → Ruckeln, wenn viele Zeilen grau sind.
            .graphicsLayer {
                alpha = rowAlpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .background(bgColor, shape = MaterialTheme.shapes.small)
            // Orientierungs-sensitive Geste statt detectHorizontalDragGestures: das
            // Original akkumulierte dx unabhängig von dy — schnelle, leicht diagonale
            // Vertikal-Flicks überschritten dabei versehentlich die 80f-Schwelle
            // (Popup "★ Song → nächster" ohne Tap) UND kämpfte um Touch-Events mit dem
            // vertikalen Scroll der LazyColumn (Ruckeln). Fix: erst nach Touch-Slop
            // per Achsen-Dominanz entscheiden — Vertikal-dominant → Event NICHT
            // konsumieren (LazyColumn scrollt ungestört), erst bei klar horizontaler
            // Bewegung (>1.5x dy) wird der Swipe überhaupt aktiviert.
            .pointerInput(selectionMode, isLocked) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var locked = false
                    var totalX = 0f
                    var totalY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        if (!locked) {
                            if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                                if (abs(totalX) > abs(totalY) * 1.5f) {
                                    locked = true
                                } else {
                                    break
                                }
                            }
                        }
                        if (locked) {
                            change.consume()
                            dragX += delta.x
                        }
                    }
                    if (locked && !isLocked && !selectionMode) {
                        if (dragX > 80f) guarded(latestNext)
                        else if (dragX < -80f) guarded(latestEnd)
                    }
                    dragX = 0f
                }
            }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (!isLocked) guarded(onPlay) },
                onLongClick = { if (!isLocked && !selectionMode) onActivateBatch() }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (interactive) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null,
                tint = Gray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (isBatchSelected || selected) Volt else VoltDim,
            fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title, color = White, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val bpmTxt = if (song.bpmExact > 0f) "%.1f BPM".format(song.bpmExact) else "${song.bpm} BPM"
            val pre    = if (song.artist.isNotEmpty()) "${song.artist}  ·  " else ""
            val suf    = if (song.genre.isNotEmpty()) "  ·  ${song.genre}" else ""
            Text("$pre$bpmTxt  |  ${song.duration}$suf", color = Gray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (interactive) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = Gray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }

        // Bearbeiten + Löschen (ausgeblendet im Batch-/Auswahl-Modus)
        if (!selectionMode) {
            Spacer(modifier = Modifier.width(2.dp))
            Column(
                modifier = Modifier.height(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onOpenSheet, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten",
                        tint = Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Löschen",
                        tint = RedStop, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Song Editor Bottom Sheet ───────────────────────────────────────────────────
@Composable
private fun SongEditorSheet(
    song: Song,
    songs: List<Song>,
    onSave: (String, String, String, String) -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onCapoChange: (Int) -> Unit,
    onNavigate: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var title    by remember(song.id) { mutableStateOf(song.title) }
    var artist   by remember(song.id) { mutableStateOf(song.artist) }
    var bpm      by remember(song.id) { mutableStateOf(song.bpm.toString()) }
    var capo     by remember(song.id) { mutableStateOf(song.capoPosition) }
    var autoStop by remember(song.id) { mutableStateOf(song.autoStop) }
    var lyrics   by remember(song.id) { mutableStateOf(song.lyrics) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var pendingPdfText by remember { mutableStateOf<String?>(null) }

    // Zuletzt genutzten Ordner merken → Picker startet beim nächsten Mal wieder dort.
    val lastFolderUri = remember { mutableStateOf(PdfLyricsImporter.loadLastFolder(context)) }
    val pdfContract = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(ctx: Context, input: Array<String>): Intent =
                super.createIntent(ctx, input).apply {
                    lastFolderUri.value?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(pdfContract) { uri: Uri? ->
        if (uri != null) {
            lastFolderUri.value = PdfLyricsImporter.rememberFolderOf(context, uri)
            importing = true
            scope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    runCatching { PdfLyricsImporter.importLyricsFromPdf(context, uri) }
                        .getOrDefault("")
                }
                importing = false
                when {
                    extracted.isBlank() ->
                        Toast.makeText(context, "Kein Text im PDF gefunden", Toast.LENGTH_LONG).show()
                    lyrics.isBlank() -> lyrics = extracted          // Feld leer → direkt einsetzen
                    else -> pendingPdfText = extracted              // Feld belegt → nachfragen
                }
            }
        }
    }

    val idx     = songs.indexOfFirst { it.id == song.id }
    val hasPrev = idx > 0
    val hasNext = idx in 0 until songs.size - 1

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        // Navigation header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(enabled = hasPrev, onClick = { onNavigate(songs[idx - 1]) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Zurück",
                    tint = if (hasPrev) Volt else Gray, modifier = Modifier.size(28.dp))
            }
            Text(
                text = if (idx >= 0) "${idx + 1} / ${songs.size}" else "—",
                color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(enabled = hasNext, onClick = { onNavigate(songs[idx + 1]) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Weiter",
                    tint = if (hasNext) Volt else Gray, modifier = Modifier.size(28.dp))
            }
        }
        Text("Song bearbeiten", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp))
        SheetField("Titel", title) { title = it }
        Spacer(modifier = Modifier.height(12.dp))
        SheetField("Künstler", artist) { artist = it }
        Spacer(modifier = Modifier.height(12.dp))
        SheetField("BPM", bpm) { bpm = it }
        Spacer(modifier = Modifier.height(12.dp))
        // Capo stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capo", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("−", color = if (capo > 0) Volt else Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = capo > 0) { capo--; onCapoChange(-1) }
                    .padding(horizontal = 14.dp, vertical = 4.dp))
            Text(capo.toString(), color = if (capo > 0) Volt else Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(30.dp))
            Text("+", color = if (capo < 11) Volt else Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = capo < 11) { capo++; onCapoChange(1) }
                    .padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Lyrics (nur Text, keine Akkorde — wird im Teleprompter beim Abspielen gezeigt)
        Text("Lyrics", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = lyrics, onValueChange = { lyrics = it },
            placeholder = { Text("Songtext einfügen — ohne Akkorde …", color = Gray, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
            textStyle = TextStyle(color = White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Volt, unfocusedBorderColor = Gray,
                focusedTextColor = White, unfocusedTextColor = White,
                cursorColor = Volt
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BgTrack)
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Volt, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Article, contentDescription = null, tint = Volt, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aus PDF importieren (Akkorde entfernt)", color = Volt, fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Auto-Stop toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Stop", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Song stoppt automatisch am Ende", color = Gray, fontSize = 11.sp)
            }
            Switch(
                checked = autoStop,
                onCheckedChange = { autoStop = it; onAutoStopChange(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Volt,
                    uncheckedThumbColor = Gray,
                    uncheckedTrackColor = BgTrack
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BgTrack)) {
                Text("Abbrechen", color = Gray)
            }
            Button(onClick = { onSave(title, artist, bpm, lyrics) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Volt)) {
                Text("Speichern", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (pendingPdfText != null) {
            AlertDialog(
                onDismissRequest = { pendingPdfText = null },
                containerColor = BgTrack,
                title = { Text("Lyrics-Feld ist nicht leer", color = White, fontSize = 16.sp) },
                text = { Text("Wie soll der importierte Text eingefügt werden?", color = Gray, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        lyrics = pendingPdfText!!
                        pendingPdfText = null
                    }) { Text("Ersetzen", color = Volt) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            lyrics = lyrics.trimEnd() + "\n\n" + pendingPdfText!!
                            pendingPdfText = null
                        }) { Text("Anhängen", color = Volt) }
                        TextButton(onClick = { pendingPdfText = null }) { Text("Abbrechen", color = Gray) }
                    }
                }
            )
        }
    }
}

@Composable
private fun SheetField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label, color = Gray) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = White, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Volt, unfocusedBorderColor = Gray,
            focusedTextColor = White, unfocusedTextColor = White,
            cursorColor = Volt, focusedLabelColor = Volt, unfocusedLabelColor = Gray
        )
    )
}


@Composable
private fun SetSongList(
    vm: PlayerViewModel, playlistId: Long, currentSong: Song?,
    isLocked: Boolean, context: android.content.Context
) {
    val songs by vm.songs.collectAsState()
    val setSongs = songs.filter { it.playlistId == playlistId }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        setSongs.take(7).forEachIndexed { index, song ->
            StageSongRow(
                index    = index + 1,
                song     = song,
                selected = song.id == currentSong?.id,
                onClick  = { if (!isLocked) vm.selectSong(song, context, sourcePlaylistId = playlistId) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        if (setSongs.size > 7) {
            Text("+${setSongs.size - 7} weitere Songs", color = Gray, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun StageSongRow(index: Int, song: Song, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp)
            .background(if (selected) BgTrack else BgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString().padStart(2, '0'),
            color = if (selected) Volt else VoltDim,
            fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(44.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Kapo: static text only — no editable controls
            val capoStr = if (song.capoPosition > 0) "Capo ${song.capoPosition}" else "Kein Capo"
            val bpmStr  = if (song.bpmExact > 0f) "%.1f BPM".format(song.bpmExact) else "${song.bpm} BPM"
            Text("$bpmStr  ·  $capoStr  ·  ${song.duration}", color = Gray, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Global Player ──────────────────────────────────────────────────────────────
private val LoopOrange = Color(0xFFFF8C00)

@Composable
private fun GlobalPlayer(
    song: Song?, nextSong: Song?,
    isPlaying: Boolean, loopState: LoopState,
    isArmed: Boolean, isLoopActiveLive: Boolean, isExitPending: Boolean,
    isInSetMode: Boolean, isGigSetMode: Boolean,
    positionMs: Long, durationMs: Long,
    loopStartMs: Long?, loopEndMs: Long?,
    activeEndAction: Int = 0,
    onSeekTo: (Long) -> Unit = {},
    onPlayPause: () -> Unit, onStop: () -> Unit,
    onToggleLoop: () -> Unit, onSetLoopButton: () -> Unit,
    onOpenLyrics: () -> Unit = {},
    onCycleEndAction: () -> Unit = {}
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    val displayFraction = if (isSeeking) seekFraction else progress
    val remainMs = if (isSeeking) ((1f - seekFraction) * durationMs).toLong().coerceAtLeast(0L)
                  else (durationMs - positionMs).coerceAtLeast(0L)
    val remSec   = remainMs / 1000
    val timeStr  = if (song == null) "--:--" else "-%02d:%02d".format(remSec / 60, remSec % 60)

    Column(modifier = Modifier.fillMaxWidth().background(BgPlayer)) {
        // Seek-Bar: dünne Linie, interaktiv bei Berührung
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(durationMs, song) {
                    if (durationMs <= 0L || song == null) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isSeeking = true
                            seekFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            seekFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd    = { onSeekTo((seekFraction * durationMs).toLong()); isSeeking = false },
                        onDragCancel = { isSeeking = false }
                    )
                }
                .pointerInput(durationMs, song) {
                    if (durationMs <= 0L || song == null) return@pointerInput
                    detectTapGestures { offset ->
                        onSeekTo(((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
        ) {
            val w      = size.width
            val cy     = size.height / 2f
            val trackH = if (isSeeking) 5.dp.toPx() else 3.dp.toPx()
            val half   = trackH / 2f
            val filled = (w * displayFraction).coerceIn(0f, w)

            drawRect(color = BgTrack, topLeft = Offset(0f, cy - half), size = Size(w, trackH))
            if (filled > 0f)
                drawRect(color = Volt,   topLeft = Offset(0f, cy - half), size = Size(filled, trackH))
            if (isSeeking)
                drawCircle(color = Volt, radius = 6.dp.toPx(), center = Offset(filled, cy))
        }
        // Links: Countdown | Mitte: Aktueller + Nächster Song | Rechts: EndAction (GigMode)
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Countdown links — tnum verhindert Breitenflackern beim Ticken
            Text(
                text = timeStr,
                modifier = Modifier.wrapContentWidth().padding(end = 12.dp),
                style = TextStyle(
                    color = Volt,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                )
            )
            // Song-Infos — bekommt den gesamten restlichen Platz
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "Kein Song geladen",
                    color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null,
                        tint = Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    val nextText = if (nextSong != null) {
                        val capo = if (nextSong.capoPosition > 0) " · Capo ${nextSong.capoPosition}" else ""
                        "${nextSong.title}$capo"
                    } else "—"
                    Text(
                        text = nextText,
                        color = if (nextSong != null) Color(0xFFBBBBBB) else Gray,
                        fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Lyrics-Button — sichtbar sobald der Song Lyrics hinterlegt hat
            if (song != null && song.lyrics.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onOpenLyrics),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Article, contentDescription = "Lyrics anzeigen",
                        tint = Volt, modifier = Modifier.size(22.dp))
                }
            }
            // EndAction-Button — nur im Gig-Set-Modus, 48dp Touch-Target
            if (isGigSetMode && song != null) {
                val (endIcon, endTint) = when (activeEndAction) {
                    1    -> Icons.Filled.Stop     to RedStop
                    2    -> Icons.Filled.PlayArrow to Volt
                    else -> Icons.Filled.Pause    to Color(0xFF64B5F6) // CUE = hellblau
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onCycleEndAction),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(endIcon, contentDescription = "End-Action umschalten",
                        tint = endTint, modifier = Modifier.size(24.dp))
                }
            }
        }
        // Transport: PLAY/PAUSE (2x) | STOP | LOOP
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            PlayerBtn(
                modifier = Modifier.weight(2f),
                icon     = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label    = if (isPlaying) "PAUSE" else "PLAY",
                tint     = if (isPlaying) Volt else White,
                bgColor  = if (isPlaying) Color(0xFF0F2010) else BgCard,
                onClick  = onPlayPause
            )
            PlayerBtn(
                modifier = Modifier.weight(1f),
                icon     = Icons.Filled.Stop,
                label    = "STOP",
                tint     = RedStop,
                bgColor  = BgCard,
                onClick  = onStop
            )
            if (isInSetMode) {
                // Tab B: Farbcodierung nach Live-Loop-State
                val loopTint = when {
                    !isArmed          -> Gray
                    !isLoopActiveLive -> LoopOrange
                    else              -> Volt
                }
                val loopBg = when {
                    !isArmed          -> BgCard
                    !isLoopActiveLive -> Color(0xFF1A0E00)
                    else              -> Color(0xFF1A1A00)
                }
                val loopLabel = when {
                    !isArmed          -> "LOOP"
                    !isLoopActiveLive -> "ARMED"
                    isExitPending     -> "EXIT"
                    else              -> "LIVE"
                }
                val exitProgress = if (isExitPending && loopEndMs != null && loopStartMs != null
                    && loopEndMs > loopStartMs) {
                    ((loopEndMs - positionMs).coerceAtLeast(0L).toFloat() /
                        (loopEndMs - loopStartMs).toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(loopBg)
                        .then(
                            if (isArmed && song != null)
                                Modifier.pointerInput(isArmed, isLoopActiveLive, isExitPending) {
                                    detectTapGestures(onPress = { onSetLoopButton() })
                                }
                            else Modifier
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = loopLabel,
                            tint = loopTint, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(loopLabel, color = loopTint, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    // Countdown-Balken: schrumpft von rechts nach links gegen null
                    if (isExitPending && exitProgress > 0f) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            drawRect(
                                color = Volt,
                                size  = size.copy(width = size.width * exitProgress)
                            )
                        }
                    }
                }
            } else {
                // Tab A: bestehende A/B-Tipp-Logik
                val isPreloaded = loopStartMs != null && loopEndMs != null
                // Im Gig-Set-Modus: Button inaktiv wenn kein Loop gespeichert
                val loopEnabled = song != null && (!isGigSetMode || isPreloaded)
                PlayerBtn(
                    modifier   = Modifier.weight(1f),
                    icon       = Icons.Filled.Repeat,
                    label      = when {
                        loopState == LoopState.INACTIVE && isPreloaded -> "READY"
                        loopState == LoopState.INACTIVE                -> "LOOP"
                        loopState == LoopState.A_SET                   -> "SET B"
                        else                                           -> "LOOP"
                    },
                    tint       = when {
                        !loopEnabled                                   -> Gray
                        loopState == LoopState.INACTIVE && isPreloaded -> VoltDim
                        loopState == LoopState.INACTIVE                -> Gray
                        loopState == LoopState.A_SET                   -> LoopOrange
                        else                                           -> Volt
                    },
                    bgColor    = when (loopState) {
                        LoopState.INACTIVE -> BgCard
                        LoopState.A_SET    -> Color(0xFF1A0E00)
                        LoopState.LOOPING  -> Color(0xFF1A1A00)
                    },
                    enabled    = loopEnabled,
                    pressDown  = true,
                    onClick    = onToggleLoop
                )
            }
        }
    }
}

@Composable
private fun PlayerBtn(
    modifier: Modifier, icon: ImageVector, label: String,
    tint: Color, bgColor: Color, enabled: Boolean = true,
    pressDown: Boolean = false,
    onClick: () -> Unit
) {
    val touchModifier = when {
        !enabled    -> Modifier
        pressDown   -> Modifier.pointerInput(onClick) {
            detectTapGestures(onPress = { onClick() })
        }
        else        -> Modifier.clickable(onClick = onClick)
    }
    Column(
        modifier = modifier.fillMaxHeight().background(bgColor).then(touchModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(34.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Search Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun SearchBar(active: Boolean, query: String, onToggle: () -> Unit, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) fr.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().background(BgDeep)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            TextField(
                value = query, onValueChange = onChange,
                placeholder = { Text("Titel, Artist, BPM, Genre…", color = Gray, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(fr),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedIndicatorColor = Volt, unfocusedIndicatorColor = Gray, cursorColor = Volt
                )
            )
            IconButton(onClick = onToggle) {
                Icon(Icons.Filled.Close, contentDescription = "Suche schließen", tint = Gray)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onToggle) {
                Icon(Icons.Filled.Search, contentDescription = "Suchen", tint = Gray,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── A/B Loop Panel ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoopPanel(
    loopState: LoopState,
    loopStartMs: Long?,
    loopEndMs: Long?,
    durationMs: Long,
    isLoopModified: Boolean,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onRangeChanged: (Long, Long) -> Unit,
    onSave: () -> Unit,
    onClearLoop: () -> Unit
) {
    AnimatedVisibility(
        visible = loopState != LoopState.INACTIVE || (loopStartMs != null && loopEndMs != null),
        enter   = expandVertically(),
        exit    = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val startMs = loopStartMs ?: 0L
            val endMs   = loopEndMs   ?: startMs

            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A", color = Volt, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(fmtMs(startMs),
                        color = Volt, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                if (loopState == LoopState.A_SET) {
                    Text("Jetzt B setzen →",
                        color = LoopOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("B", color = RedStop, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMs(endMs),
                            color = RedStop, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            // RangeSlider + Nudge buttons (nur wenn LOOPING)
            if (loopState == LoopState.LOOPING && durationMs > 0L) {
                val dur = durationMs.toFloat()
                var sliderRange by remember(startMs, endMs) {
                    val safeEnd = endMs.coerceAtLeast(startMs + 500L).coerceAtMost(durationMs.coerceAtLeast(startMs + 500L))
                    mutableStateOf(startMs.toFloat()..safeEnd.toFloat())
                }
                Spacer(Modifier.height(4.dp))
                RangeSlider(
                    value         = sliderRange,
                    onValueChange = { range ->
                        val clamped = range.start..range.endInclusive.coerceAtLeast(range.start + 500f)
                        sliderRange = clamped
                    },
                    valueRange     = 0f..dur,
                    onValueChangeFinished = {
                        val newStart = sliderRange.start.toLong().coerceIn(0L, durationMs)
                        val newEnd   = sliderRange.endInclusive.toLong().coerceIn(newStart + 500L, durationMs)
                        if (newEnd - newStart >= 500L) onRangeChanged(newStart, newEnd)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor              = Volt,
                        activeTrackColor        = Volt,
                        inactiveTrackColor      = BgTrack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Nudge buttons
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick  = { onNudgeStart(-10L) },
                        modifier = Modifier.size(48.dp)
                    ) { Text("A−", color = Volt, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    IconButton(
                        onClick  = { onNudgeStart(+10L) },
                        modifier = Modifier.size(48.dp)
                    ) { Text("A+", color = Volt, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    // Clear-Loop-Button — immer sichtbar solange Loop aktiv
                    Row {
                        IconButton(
                            onClick  = onClearLoop,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF2A0A0A), MaterialTheme.shapes.small)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Loop verwerfen",
                                tint = RedStop, modifier = Modifier.size(22.dp))
                        }
                        // Save button — nur sichtbar wenn geändert
                        if (isLoopModified) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick  = onSave,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF0A2A0A), MaterialTheme.shapes.small)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Speichern",
                                    tint = Volt, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = { onNudgeEnd(-10L) },
                        modifier = Modifier.size(48.dp)
                    ) { Text("B−", color = RedStop, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    IconButton(
                        onClick  = { onNudgeEnd(+10L) },
                        modifier = Modifier.size(48.dp)
                    ) { Text("B+", color = RedStop, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val m = ms / 60_000L; val s = ms % 60_000L / 1000L; val r = ms % 1000L / 10L
    return "%d:%02d.%02d".format(m, s, r)
}

// ── Genre Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun GenreBar(count: Int, onGenre: (String) -> Unit, onClear: () -> Unit, onAddToSet: () -> Unit) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(BgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$count Song${if (count == 1) "" else "s"} markiert",
                color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = onAddToSet,
                colors = ButtonDefaults.buttonColors(containerColor = Volt),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = null,
                    tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("→ Set", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Auswahl aufheben",
                    tint = Gray, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Pop/Rock", "Folk/Country", "Deutsch", "Groove").forEach { g ->
                Button(
                    onClick = { onGenre(g) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(g, color = Volt, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier.size(36.dp).background(BgCard, shape = MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Eigenes Genre",
                    tint = Volt, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showCustomDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor   = BgCard,
            title = { Text("Eigenes Genre", color = White, fontWeight = FontWeight.Bold) },
            text  = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("z. B. Jazz", color = Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Volt, unfocusedBorderColor = Gray,
                        focusedTextColor = White, unfocusedTextColor = White,
                        cursorColor = Volt
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { onGenre(text.trim()); showCustomDialog = false }
                ) { Text("Übernehmen", color = Volt, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Abbrechen", color = Gray) }
            }
        )
    }
}

// ── Add-to-Set Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToSetDialog(
    gigVm: GigViewModel,
    songCount: Int,
    songIds: List<Long>,
    onConfirm: (setId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val allGigs by gigVm.allGigs.collectAsState()
    var pickedGigId by remember { mutableStateOf<Long?>(null) }
    val pickedGig = allGigs.find { it.gigId == pickedGigId }

    val setsForGig by remember(pickedGigId) {
        if (pickedGigId != null) gigVm.getSetsForGig(pickedGigId!!)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())

    // Songs, die im GEWÄHLTEN Ziel-Gig schon verplant sind → weiche Nachfrage vor dem Add
    val plannedInGig by remember(pickedGigId) {
        if (pickedGigId != null) gigVm.getSongIdsInGig(pickedGigId!!)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    var pendingSetId by remember { mutableStateOf<Long?>(null) }

    fun chooseSet(setId: Long) {
        if (songIds.any { it in plannedInGig }) pendingSetId = setId
        else onConfirm(setId)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = BgCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pickedGig != null) {
                    IconButton(onClick = { pickedGigId = null }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Zurück",
                            tint = White, modifier = Modifier.size(26.dp))
                    }
                    Text(pickedGig.name, color = White, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("$songCount Song${if (songCount == 1) "" else "s"} zu Set hinzufügen",
                        color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                }
            }

            if (pickedGig == null) {
                if (allGigs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Noch keine Gigs — erst in Tab B einen Gig anlegen.",
                            color = Gray, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Text("Gig wählen:", color = Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    allGigs.forEach { gig ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(BgTrack, shape = MaterialTheme.shapes.small)
                                .clickable { pickedGigId = gig.gigId }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(gig.name, color = White, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                                tint = Gray, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            } else {
                if (setsForGig.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Dieser Gig hat noch keine Sets.",
                            color = Gray, fontSize = 13.sp)
                    }
                } else {
                    Text("Set wählen:", color = Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    setsForGig.forEach { set ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(BgTrack, shape = MaterialTheme.shapes.small)
                                .clickable { chooseSet(set.setId) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("%02d".format(set.position + 1),
                                color = Volt, fontSize = 16.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.width(36.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(set.name, color = White, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.Check, contentDescription = null,
                                tint = Volt, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    if (pendingSetId != null) {
        AlertDialog(
            onDismissRequest = { pendingSetId = null },
            containerColor   = BgCard,
            title = { Text("Schon im Gig verplant", color = White, fontWeight = FontWeight.Bold) },
            text  = { Text("Mindestens ein Song ist in diesem Gig bereits einem Set zugeordnet. Trotzdem alle hinzufügen?",
                color = Gray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { val s = pendingSetId!!; pendingSetId = null; onConfirm(s) }) {
                    Text("Trotzdem hinzufügen", color = Volt, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSetId = null }) { Text("Abbrechen", color = Gray) }
            }
        )
    }
}
