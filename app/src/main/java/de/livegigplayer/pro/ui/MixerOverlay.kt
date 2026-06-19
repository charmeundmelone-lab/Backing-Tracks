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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.TrackMode

private val MixerBg   = Color(0xF01A1A1A)
private val MixerVolt = Color(0xFFE8FF00)
private val MixerGray = Color(0xFF777777)
private val MixerWhite = Color(0xFFFFFFFF)

@Composable
fun MixerOverlay(
    visible: Boolean,
    song: Song?,
    trackMode: TrackMode?,
    onVolumeChange: (String, Float) -> Unit,
    onReset: () -> Unit,
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
                .background(MixerBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
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
                        Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = MixerWhite)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MixerSlider("DRUMS",  song.volDrums,  mt.drums  != null) { onVolumeChange("drums",  it) }
                        MixerSlider("BASS",   song.volBass,   mt.bass   != null) { onVolumeChange("bass",   it) }
                        MixerSlider("KEYS",   song.volKeys,   mt.keys   != null) { onVolumeChange("keys",   it) }
                        MixerSlider("VOCALS", song.volVocals, mt.vocals != null) { onVolumeChange("vocals", it) }
                        MixerSlider("CLICK",  song.volClick,  mt.click  != null) { onVolumeChange("click",  it) }
                        MixerSlider("CUE",    song.volCue,    mt.cue    != null) { onVolumeChange("cue",    it) }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("ALLE ZURÜCKSETZEN", color = MixerVolt, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MixerSlider(label: String, valueDb: Float, enabled: Boolean, onValueChange: (Float) -> Unit) {
    val labelColor = if (enabled) MixerWhite else Color(0xFF444444)
    val valueColor = if (enabled) MixerGray  else Color(0xFF333333)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(if (enabled) "%.1f dB".format(valueDb) else "n/a", color = valueColor, fontSize = 12.sp)
        }
        Slider(
            value = valueDb,
            onValueChange = onValueChange,
            valueRange = -3f..3f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MixerVolt,
                activeTrackColor = MixerVolt,
                inactiveTrackColor = Color(0xFF444444),
                disabledThumbColor = Color(0xFF333333),
                disabledActiveTrackColor = Color(0xFF333333),
                disabledInactiveTrackColor = Color(0xFF2A2A2A)
            )
        )
    }
}
