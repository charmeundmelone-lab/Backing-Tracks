package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Many-to-Many Brücke: Set ↔ Song
// Composite PK (setId + songId): ein Song taucht pro Set maximal einmal auf.
// positionInSet: garantierte Reihenfolge im Set.
// isCompleted:   nur innerhalb dieses Sets als "gespielt" markiert — kein globaler Zustand am Song.

@Entity(
    tableName = "set_song_cross_ref",
    primaryKeys = ["setId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity        = SetEntity::class,
            parentColumns = ["setId"],
            childColumns  = ["setId"],
            onDelete      = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity        = Song::class,
            parentColumns = ["id"],
            childColumns  = ["songId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class SetSongCrossRef(
    val setId:         Long,
    val songId:        Long,
    val positionInSet: Int,
    val isCompleted:   Boolean = false,
    val isSpontaneous: Boolean = false,
    val endAction:     Int     = 0   // 0=CUE, 1=STOP, 2=AUTOPLAY
)
