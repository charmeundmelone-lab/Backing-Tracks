package de.livegigplayer.pro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.livegigplayer.pro.data.Song

private val BgDeep  = Color(0xFF0A0A0A)
private val BgCard  = Color(0xFF1A1A1A)
private val BgTrack = Color(0xFF2A2A2A)
private val Volt    = Color(0xFFE8FF00)
private val VoltDim = Color(0x8CE8FF00)
private val White   = Color(0xFFFFFFFF)
private val Gray    = Color(0xFF777777)

@Composable
fun MainScreen(vm: PlayerViewModel = viewModel()) {
    val context     = LocalContext.current
    val songs       by vm.songs.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying   by vm.isPlaying.collectAsState()
    val trackMode   by vm.trackMode.collectAsState()
    val showMixer   by vm.showMixer.collectAsState()
    val positionMs  by vm.positionMs.collectAsState()
    val durationMs  by vm.durationMs.collectAsState()
    val isScanning  by vm.isScanning.collectAsState()
    val scanProgress by vm.scanProgress.collectAsState()

    var isLocked by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.importFolder(context, it) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .systemBarsPadding()
        ) {
            TopBar(
                isLocked      = isLocked,
                onLockToggle  = { isLocked = !isLocked },
                onMixerToggle = { vm.toggleMixer() },
                onImport      = { importLauncher.launch(null) }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                songs.forEachIndexed { index, song ->
                    SongRow(
                        index    = index + 1,
                        song     = song,
                        selected = song.id == currentSong?.id,
                        onClick  = { if (!isLocked) vm.selectSong(song, context) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            BottomPlayer(
                song        = currentSong,
                isPlaying   = isPlaying,
                positionMs  = positionMs,
                durationMs  = durationMs,
                onPrevious  = { vm.skipPrevious() },
                onPlayPause = { vm.togglePlayPause() },
                onNext      = { vm.skipNext() }
            )
        }

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

        // Scanning-Overlay
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Volt, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SCANNE ORDNER", color = Volt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (scanProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = scanProgress,
                            color = Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onMixerToggle: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Live-Gig-Player Pro",
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onImport) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = "Ordner importieren",
                tint = Gray,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = onMixerToggle) {
            Icon(Icons.Filled.Tune, contentDescription = "Mixer", tint = Gray, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (isLocked) "Gesperrt" else "Entsperrt",
                tint = if (isLocked) Volt else Gray,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun SongRow(
    index: Int, song: Song, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val dimmed = song.isCompleted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) BgTrack else BgCard, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (dimmed) VoltDim else Volt,
            fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = song.title,
                color = if (dimmed) Gray else White,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val capoText = if (song.capoPosition == 0) "Kein Kapo" else "Kapo: ${song.capoPosition}"
            Text("${song.bpm} BPM | ${song.duration} | $capoText", color = Gray, fontSize = 12.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun BottomPlayer(
    song: Song?, isPlaying: Boolean, positionMs: Long, durationMs: Long,
    onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    val remainMs = (durationMs - positionMs).coerceAtLeast(0L)
    val remSec   = remainMs / 1000
    val timeText = if (song == null) "-00:00" else "-%02d:%02d".format(remSec / 60, remSec % 60)

    Column(modifier = Modifier.fillMaxWidth().background(BgDeep)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(timeText, color = VoltDim, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Volt, trackColor = BgTrack, drawStopIndicator = {}
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            PlayerButton(Icons.Filled.SkipPrevious, "ZURÜCK", Modifier.weight(1f), onClick = onPrevious)
            PlayerButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "PAUSE" else "PLAY",
                modifier = Modifier.weight(1f),
                tint = if (isPlaying) Volt else White,
                onClick = onPlayPause
            )
            PlayerButton(Icons.Filled.Repeat, "LOOP", Modifier.weight(1f), onClick = {})
            PlayerButton(Icons.Filled.SkipNext, "WEITER", Modifier.weight(1f), onClick = onNext)
        }
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    tint: Color = White, onClick: () -> Unit
) {
    Column(
        modifier = modifier.height(110.dp).background(BgCard).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(42.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
