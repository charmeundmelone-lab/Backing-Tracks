package de.minitraxx.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Inline ChordPro: [Chord]text → Seg(chord="Chord", text="text")
private data class Seg(val chord: String?, val text: String)

private val INLINE_CHORD = Regex("""\[([A-G][^\]]{0,7})\]""")

private fun String.parseSegs(): List<Seg> {
    if (!INLINE_CHORD.containsMatchIn(this)) return listOf(Seg(null, this))
    val segs = mutableListOf<Seg>()
    var pos = 0
    var pending: String? = null
    for (m in INLINE_CHORD.findAll(this)) {
        val before = substring(pos, m.range.first)
        if (pending != null || before.isNotEmpty()) segs += Seg(pending, before)
        pending = m.groupValues[1]
        pos = m.range.last + 1
    }
    segs += Seg(pending, substring(pos))
    return segs
}

private fun List<Seg>.rebuildLine(): String = joinToString("") { s ->
    (if (s.chord != null) "[${s.chord}]" else "") + s.text
}

// Moves chord at idx one word to the left (chord jumps over the last word of the prev segment).
private fun List<Seg>.moveLeft(idx: Int): List<Seg> {
    if (idx <= 0) return this
    val r = toMutableList()
    val prevText = r[idx - 1].text
    val trimmed = prevText.trimEnd()
    val cut = trimmed.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
    r[idx - 1] = r[idx - 1].copy(text = prevText.substring(0, cut))
    r[idx] = r[idx].copy(text = prevText.substring(cut) + r[idx].text)
    return r
}

// Moves chord at idx one word to the right (chord jumps over the first word of its own segment).
private fun List<Seg>.moveRight(idx: Int): List<Seg> {
    val r = toMutableList()
    val text = r[idx].text
    val cut = text.indexOf(' ').let { if (it < 0) text.length else it + 1 }
    val word = text.substring(0, cut)
    if (word.isBlank()) return this
    r[idx] = r[idx].copy(text = text.substring(cut))
    if (idx > 0) r[idx - 1] = r[idx - 1].copy(text = r[idx - 1].text + word)
    else r.add(0, Seg(null, word))
    return r
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChordEditorDialog(
    initialChordPro: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    // Parse every raw line into segments; retain original structure for non-lyric lines.
    var lineSegs by remember {
        mutableStateOf(initialChordPro.lines().map { it.parseSegs() })
    }
    // (lineIdx, segIdx) of the currently selected chord chip
    var selection by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun rebuild() = lineSegs.joinToString("\n") { it.rebuildLine() }

    fun move(lineIdx: Int, segIdx: Int, dir: Int) {
        val updated = lineSegs.toMutableList()
        updated[lineIdx] = if (dir < 0) updated[lineIdx].moveLeft(segIdx)
                           else updated[lineIdx].moveRight(segIdx)
        lineSegs = updated
        // Keep selection so user can keep tapping the arrow without re-selecting
        selection = lineIdx to segIdx
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Akkorde anpassen", style = MaterialTheme.typography.titleLarge)
                    Row {
                        TextButton(onClick = onDismiss) { Text("Abbrechen") }
                        TextButton(onClick = { onSave(rebuild()) }) {
                            Text("Speichern", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "Akkord antippen → ← → zum Verschieben (ein Wort pro Tap)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(lineSegs) { lineIdx, segs ->
                        val hasChords = segs.any { it.chord != null }
                        val rawText = segs.rebuildLine()
                        when {
                            rawText.isBlank() -> Spacer(Modifier.height(8.dp))
                            rawText.trimStart().startsWith("{section:") -> {
                                val name = rawText.removePrefix("{section:")
                                    .removeSuffix("}").trim()
                                Text(
                                    name,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                                )
                            }
                            hasChords -> EditableLyricLine(
                                segs = segs,
                                lineIdx = lineIdx,
                                selection = selection,
                                onSelect = { segIdx ->
                                    selection = if (selection == lineIdx to segIdx) null
                                               else lineIdx to segIdx
                                },
                                onMove = { segIdx, dir -> move(lineIdx, segIdx, dir) },
                            )
                            else -> Text(rawText, fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableLyricLine(
    segs: List<Seg>,
    lineIdx: Int,
    selection: Pair<Int, Int>?,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        for ((segIdx, seg) in segs.withIndex()) {
            if (seg.chord != null) {
                val isSelected = selection == lineIdx to segIdx
                val canLeft = segIdx > 0 && segs[segIdx - 1].text.isNotBlank()
                val canRight = seg.text.isNotBlank()

                if (isSelected) {
                    IconButton(
                        onClick = { if (canLeft) onMove(segIdx, -1) },
                        enabled = canLeft,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ein Wort nach links",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { onSelect(segIdx) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        seg.chord,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (isSelected) {
                    IconButton(
                        onClick = { if (canRight) onMove(segIdx, +1) },
                        enabled = canRight,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "ein Wort nach rechts",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (seg.text.isNotEmpty()) {
                Text(seg.text, fontSize = 14.sp)
            }
        }
    }
}
