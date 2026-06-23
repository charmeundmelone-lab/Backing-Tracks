package de.livegigplayer.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSet(set: SetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCrossRef(crossRef: SetSongCrossRef)

    @Delete
    abstract suspend fun deleteSet(set: SetEntity)

    @Query("UPDATE sets SET position = :position WHERE setId = :setId")
    abstract suspend fun updatePosition(setId: Long, position: Int)

    @Query("SELECT * FROM sets WHERE gigOwnerId = :gigId ORDER BY position ASC")
    abstract fun getSetsForGig(gigId: Long): Flow<List<SetEntity>>

    @Transaction
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    abstract fun getSongsInSet(setId: Long): Flow<List<SongInSet>>

    @Transaction
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    abstract suspend fun getSongsInSetOnce(setId: Long): List<SongInSet>

    @Query("SELECT MAX(positionInSet) FROM set_song_cross_ref WHERE setId = :setId")
    abstract suspend fun getMaxPositionInSet(setId: Long): Int?

    @Query("DELETE FROM set_song_cross_ref WHERE setId = :setId AND songId = :songId")
    abstract suspend fun deleteCrossRef(setId: Long, songId: Long)

    @Query("UPDATE set_song_cross_ref SET positionInSet = :position WHERE setId = :setId AND songId = :songId")
    abstract suspend fun updateSongPosition(setId: Long, songId: Long, position: Int)

    @Query("UPDATE set_song_cross_ref SET isCompleted = :completed WHERE setId = :setId AND songId = :songId")
    abstract suspend fun markSongCompleted(setId: Long, songId: Long, completed: Boolean)

    @Query("UPDATE set_song_cross_ref SET isCompleted = 0 WHERE setId = :setId")
    abstract suspend fun resetCompletedForSet(setId: Long)

    // ── Atomare Spontan-Einreihung ────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM set_song_cross_ref WHERE setId = :setId AND songId = :songId")
    abstract suspend fun existsInSet(setId: Long, songId: Long): Int

    // Verschiebt nur Position + isSpontaneous; isCompleted bleibt unberührt.
    @Query("UPDATE set_song_cross_ref SET positionInSet = :position, isSpontaneous = 1 WHERE setId = :setId AND songId = :songId")
    abstract suspend fun moveSongSpontaneous(setId: Long, songId: Long, position: Int)

    @Transaction
    open suspend fun moveSpontaneousNext(setId: Long, songId: Long, currentSongId: Long) {
        val songs = getSongsInSetOnce(setId)
        val currentPos = songs.find { it.song.id == currentSongId }?.positionInSet
            ?: (songs.maxOfOrNull { it.positionInSet } ?: -1)
        applyMoveSpontaneous(setId, songId, currentPos + 1, songs)
    }

    @Transaction
    open suspend fun moveSpontaneousLater(setId: Long, songId: Long, currentSongId: Long) {
        val songs = getSongsInSetOnce(setId)
        val firstRegular = songs.firstOrNull {
            !it.completedInSet && !it.spontaneousInSet && it.song.id != currentSongId
        }
        val insertPos = firstRegular?.positionInSet
            ?: ((songs.maxOfOrNull { it.positionInSet } ?: -1) + 1)
        applyMoveSpontaneous(setId, songId, insertPos, songs)
    }

    private suspend fun applyMoveSpontaneous(setId: Long, songId: Long, insertPos: Int, songs: List<SongInSet>) {
        // Nachbarn verschieben; den Song selbst auslassen (kein doppelter Shift)
        songs.filter { it.positionInSet >= insertPos && it.song.id != songId }
             .forEach { updateSongPosition(setId, it.song.id, it.positionInSet + 1) }
        // Existiert der Song bereits im Set? → nur Position + isSpontaneous updaten,
        // isCompleted BLEIBT erhalten. Neu? → frisch einreihen.
        if (existsInSet(setId, songId) > 0) {
            moveSongSpontaneous(setId, songId, insertPos)
        } else {
            insertCrossRef(SetSongCrossRef(setId, songId, insertPos, isSpontaneous = true))
        }
        // Lückenlos renummerieren innerhalb derselben Transaktion
        val updated = getSongsInSetOnce(setId)
        updated.forEachIndexed { i, s -> updateSongPosition(setId, s.song.id, i) }
    }
}
