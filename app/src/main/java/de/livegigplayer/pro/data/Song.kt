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
    val autoStop: Boolean = false
)
