package de.livegigplayer.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.livegigplayer.pro.data.Song

private val BgDeep = Color(0xFF0A0A0A)
private val BgCard = Color(0xFF1A1A1A)
private val BgTrack = Color(0xFF2A2A2A)
private val Volt = Color(0xFFE8FF00)
private val VoltDim = Color(0x8CE8FF00)
private val White = Color(0xFFFFFFFF)
private val Gray = Color(0xFF777777)

private val dummySongs = listOf(
    Song(1, "Sultans of Swing", 149, "4/4", 1, false, "", "04:03", 0),
    Song(2, "Hotel California", 147, "4/4", 1, false, "", "06:31", 0),
    Song(3, "Nothing Else Matters", 69, "6/8", 1, false, "", "06:28", 0),
    Song(4, "Sweet Child O' Mine", 125, "4/4", 1, false, "", "05:56", 2),
    Song(5, "Comfortably Numb", 63, "4/4", 1, false, "", "06:22", 0),
    Song(6, "Stairway to Heaven", 82, "4/4", 1, false, "", "08:02", 0),
    Song(7, "Wish You Were Here", 66, "4/4", 1, true, "", "05:34", 3),
)

@Composable
fun MainScreen() {
    var isLocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .systemBarsPadding()
    ) {
        TopBar(isLocked = isLocked, onLockToggle = { isLocked = !isLocked })

        // Jede Kachel bekommt weight(1f) → füllt exakt den verfügbaren Raum ohne schwarze Lücke
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            dummySongs.forEachIndexed { index, song ->
                SongRow(
                    index = index + 1,
                    song = song,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        BottomPlayer()
    }
}

@Composable
private fun TopBar(isLocked: Boolean, onLockToggle: () -> Unit) {
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
private fun SongRow(index: Int, song: Song, modifier: Modifier = Modifier) {
    val dimmed = song.isCompleted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgCard, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (dimmed) VoltDim else Volt,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 30.sp,
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = song.title,
                color = if (dimmed) Gray else White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val capoText = if (song.capoPosition == 0) "🎸 Kein Kapo" else "🎸 Kapo: ${song.capoPosition}"
            Text(
                text = "${song.bpm} BPM | ${song.timeSignature} | ⏱️ ${song.duration} | $capoText",
                color = Gray,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun BottomPlayer() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "-00:00",
                color = VoltDim,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp
            )
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Volt,
                trackColor = BgTrack,
                drawStopIndicator = {}
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            PlayerButton(Icons.Filled.SkipPrevious, "ZURÜCK", Modifier.weight(1f))
            PlayerButton(Icons.Filled.PlayArrow, "PLAY", Modifier.weight(1f))
            PlayerButton(Icons.Filled.Repeat, "LOOP", Modifier.weight(1f))
            PlayerButton(Icons.Filled.SkipNext, "WEITER", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(110.dp)
            .background(BgCard),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = White,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
