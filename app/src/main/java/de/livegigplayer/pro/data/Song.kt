package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    val bpm: Int,
    val bpmExact: Float = 0f,
    val timeSignature: String,
    val playlistId: Long,
    val isCompleted: Boolean = false,
    val audioFilePath: String,
    val duration: String = "00:00",
    val capoPosition: Int = 0,
    val keySignature: String = "",
    val genre: String = "",
    val volDrums: Float = 0f,
    val volBass: Float = 0f,
    val volKeys: Float = 0f,
    val volVocals: Float = 0f,
    val volClick: Float = 0f,
    val volCue: Float = 0f,
    val autoStop: Boolean = false,
    val loopStartMs: Long = 0L,
    val loopEndMs: Long = 0L,
    val lyrics: String = "",
    // Superseded durch lyricsSyncPoints (v16) — Feld bleibt nur für Schema-Kompatibilität
    // erhalten (Room verlangt exakte Spalten-Übereinstimmung), wird nicht mehr geschrieben.
    val lyricsStartMs: Long = 0L,
    // Kalibrierungspunkte aus dem Teleprompter: "lineIndex:positionMs" kommagetrennt,
    // sortiert nach positionMs. Siehe LyricsOverlay.parseSyncPoints/serializeSyncPoints.
    val lyricsSyncPoints: String = "",
    // Vorlauf des Teleprompter-Scrolls in ms (v17): um wie viel der Text dem echten
    // Gesang vorauseilt. Kompensiert die menschliche Reaktionszeit beim Kalibrieren
    // (jeder Tap wird ~0,3–0,5s NACH dem echten Abschnittswechsel gesetzt) und gibt
    // dem Sänger Vorlesezeit. Live im Teleprompter per −/+ verstellbar.
    val lyricsLeadMs: Long = 0L
)
