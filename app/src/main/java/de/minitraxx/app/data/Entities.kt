package de.minitraxx.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Verhalten am Songende — pro Song wählbar. */
object EndAction {
    /** Nächsten Song laden und warten (armed). */
    const val LOAD_NEXT = 0
    /** Nächsten Song laden und sofort abspielen (Medley). */
    const val AUTOPLAY_NEXT = 1
    /** Stopp — beim aktuellen Song bleiben, kein Wechsel. */
    const val STOP = 2
}

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    /** Länge des längsten Stems in Frames (48 kHz). */
    val durationFrames: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Siehe [EndAction]. */
    val endAction: Int = EndAction.LOAD_NEXT,
)

/**
 * Ein Stem eines Songs. slot 0..3 = Instrumente (Bus MAIN/links),
 * slot 4 = Click/Cue (Bus CUE/rechts).
 */
@Entity(
    tableName = "stems",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("songId")],
)
data class StemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val slot: Int,
    val displayName: String,
    /** Dateiname des kanonischen WAV im Song-Ordner. */
    val fileName: String,
    val gainDb: Float = 0f,
    val frames: Long = 0,
)

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "setlist_items",
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setlistId"), Index("songId")],
)
data class SetlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setlistId: Long,
    val songId: Long,
    val position: Int,
)
