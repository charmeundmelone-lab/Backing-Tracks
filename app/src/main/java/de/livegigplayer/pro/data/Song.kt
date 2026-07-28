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
    // (v17) Reserviert/unbenutzt: war ein einstellbarer Scroll-Vorlauf (Sprint 5.45),
    // durch das Abschnitts-Modell mit Oben-Anker (Sprint 5.46) abgelöst. Spalte bleibt
    // aus Schema-Kompatibilität erhalten (ADD-only-Konvention, Gerät ist bereits auf v17),
    // wird nicht mehr geschrieben — analog lyricsStartMs.
    val lyricsLeadMs: Long = 0L,
    // Manuelles Tempo-Tag fürs Archiv-Filtern (v19) — bewusst NICHT aus BPM abgeleitet,
    // da viele BPM-Werte im Bestand unzuverlässig/leer sind. 0=ungetaggt, 1=Langsam,
    // 2=Mittel, 3=Schnell.
    val tempoTag: Int = 0
)
