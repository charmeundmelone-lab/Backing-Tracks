package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val bpm: Int,
    val timeSignature: String,
    val playlistId: Long,
    val isCompleted: Boolean = false,
    val audioFilePath: String
)
