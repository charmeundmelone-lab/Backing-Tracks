package de.livegigplayer.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    Song(1, "Sultans of Swing", 149, "4/4", 1, false, ""),
    Song(2, "Hotel California", 147, "4/4", 1, false, ""),
    Song(3, "Nothing Else Matters", 69, "6/8", 1, false, ""),
    Song(4, "Sweet Child O' Mine", 125, "4/4", 1, false, ""),
    Song(5, "Comfortably Numb", 63, "4/4", 1, false, ""),
    Song(6, "Stairway to Heaven", 82, "4/4", 1, false, ""),
    Song(7, "Wish You Were Here", 66, "4/4", 1, true, ""),
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(dummySongs) { index, song ->
                SongRow(index = index + 1, song = song)
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
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
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
private fun SongRow(index: Int, song: Song) {
    val dimmed = song.isCompleted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = if (dimmed) VoltDim else Volt,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(56.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = song.title,
                color = if (dimmed) Gray else White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.bpm} BPM | ${song.timeSignature}",
                color = Gray,
                fontSize = 13.sp,
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "-00:00",
                color = VoltDim,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
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
            .aspectRatio(1f)
            .background(BgCard),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = White,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
