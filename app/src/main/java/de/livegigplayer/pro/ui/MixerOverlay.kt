package de.livegigplayer.pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode

// Vollständig deckend – kein Alpha-Kanal
private val MixerBg    = Color(0xFF0A0A0A)
private val MixerCard  = Color(0xFF1E1E1E)
private val MixerVolt  = Color(0xFFE8FF00)
private val MixerGray  = Color(0xFF777777)
private val MixerWhite = Color(0xFFFFFFFF)

@Composable
fun MixerOverlay(
    visible: Boolean,
    song: Song?,
    trackMode: TrackMode?,
    isPlaying: Boolean,
    onVolumeChange: (String, Float) -> Unit,
    onReset: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit  = slideOutVertically(targetOffsetY  = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MixerBg)   // 100% deckend, kein Bleed-through
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()          // oben: Status-Bar freilassen
                    .navigationBarsPadding()      // unten: Gesten-/Home-Bar freilassen
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {

                // ── Header ─────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE-MIXER",
                        color = MixerVolt,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = MixerGray)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Transport Controls ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        icon    = Icons.Filled.Stop,
                        label   = "STOP",
                        tint    = MixerGray,
                        active  = false,
                        modifier = Modifier.weight(1f),
                        onClick = onStop
                    )
                    TransportButton(
                        icon    = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label   = if (isPlaying) "PAUSE" else "PLAY",
                        tint    = if (isPlaying) MixerVolt else MixerWhite,
                        active  = isPlaying,
                        modifier = Modifier.weight(1f),
                        onClick = onPlayPause
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Spacer(modifier = Modifier.height(8.dp))

                // ── Fader ───────────────────────────────────────
                val mt = trackMode as? TrackMode.Multitrack
                if (song == null || mt == null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Kein Multitrack geladen.",
                        color = MixerGray,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        MixerSlider("DRUMS",  song.volDrums,  mt.drums  != null) { onVolumeChange("drums",  it) }
                        MixerSlider("BASS",   song.volBass,   mt.bass   != null) { onVolumeChange("bass",   it) }
                        MixerSlider("KEYS",   song.volKeys,   mt.keys   != null) { onVolumeChange("keys",   it) }
                        MixerSlider("VOCALS", song.volVocals, mt.vocals != null) { onVolumeChange("vocals", it) }
                        MixerSlider("CLICK",  song.volClick,  mt.click  != null) { onVolumeChange("click",  it) }
                        MixerSlider("CUE",    song.volCue,    mt.cue    != null) { onVolumeChange("cue",    it) }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Reset ───────────────────────────────────────
                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Text(
                        text = "ALLE ZURÜCKSETZEN",
                        color = MixerVolt,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) Color(0xFF2A2A00) else MixerCard
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = bg)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MixerSlider(label: String, valueDb: Float, enabled: Boolean, onValueChange: (Float) -> Unit) {
    val labelColor = if (enabled) MixerWhite else Color(0xFF3A3A3A)
    val valueColor = if (enabled) MixerGray  else Color(0xFF2A2A2A)
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text  = if (enabled) "%+.1f dB".format(valueDb) else "–",
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value         = valueDb,
            onValueChange = onValueChange,
            valueRange    = -3f..3f,
            steps         = 11,
            enabled       = enabled,
            modifier      = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor                = MixerVolt,
                activeTrackColor          = MixerVolt,
                inactiveTrackColor        = Color(0xFF333333),
                disabledThumbColor        = Color(0xFF1E1E1E),
                disabledActiveTrackColor  = Color(0xFF1E1E1E),
                disabledInactiveTrackColor = Color(0xFF1A1A1A)
            )
        )
    }
}
