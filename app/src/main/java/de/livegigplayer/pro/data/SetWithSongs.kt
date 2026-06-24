package de.livegigplayer.pro.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

// Variante A: @Relation + Junction
// Für einfache Abfragen "Welche Songs gehören zu diesem Set?".
// Room garantiert KEINE Reihenfolge und liefert NICHT positionInSet/isCompleted.
// DAO-Verwendung:
//   @Transaction
//   @Query("SELECT * FROM sets WHERE setId = :setId")
//   fun getSetWithSongs(setId: Long): Flow<SetWithSongs>

data class SetWithSongs(
    @Embedded val set: SetEntity,
    @Relation(
        parentColumn = "setId",
        entityColumn = "id",
        associateBy  = Junction(
            value        = SetSongCrossRef::class,
            parentColumn = "setId",
            entityColumn = "songId"
        )
    )
    val songs: List<Song>
)

// Variante B: SongInSet für geordnete Abfragen mit isCompleted-Status.
// Wird mit einem custom DAO-@Query (INNER JOIN) verwendet:
//
//   @Query("""
//       SELECT songs.*, ref.positionInSet, ref.isCompleted
//       FROM songs
//       INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
//       WHERE ref.setId = :setId
//       ORDER BY ref.positionInSet ASC
//   """)
//   fun getSongsInSet(setId: Long): Flow<List<SongInSet>>

data class SongInSet(
    @Embedded val song: Song,
    val positionInSet:   Int,
    val completedInSet:  Boolean,
    val spontaneousInSet: Boolean,
    val endAction:       Int
)
