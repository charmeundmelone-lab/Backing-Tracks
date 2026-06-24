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
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet, ref.endAction AS endAction
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    abstract fun getSongsInSet(setId: Long): Flow<List<SongInSet>>

    @Transaction
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet, ref.endAction AS endAction
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

    @Query("SELECT endAction FROM set_song_cross_ref WHERE setId = :setId AND songId = :songId")
    abstract suspend fun getEndAction(setId: Long, songId: Long): Int?

    @Query("UPDATE set_song_cross_ref SET endAction = :endAction WHERE setId = :setId AND songId = :songId")
    abstract suspend fun updateEndAction(setId: Long, songId: Long, endAction: Int)

    // ── Atomare Spontan-Einreihung ────────────────────────────────────────────

    @Query("UPDATE set_song_cross_ref SET positionInSet = positionInSet + 1 WHERE setId = :setId AND positionInSet >= :fromPos")
    abstract suspend fun shiftPositionsUp(setId: Long, fromPos: Int)

    // Rechts-Swipe: sofort nach dem aktuell spielenden Song einschieben
    @Transaction
    open suspend fun moveSpontaneousNext(setId: Long, songId: Long, currentSongId: Long) {
        deleteCrossRef(setId, songId)
        val songs = getSongsInSetOnce(setId)
        val anchor = songs.find { it.song.id == currentSongId }?.positionInSet
            ?: songs.firstOrNull { !it.completedInSet }?.positionInSet
            ?: -1
        insertSpontaneousAt(setId, songId, anchor + 1)
    }

    // Links-Swipe: ans Ende des Sets anhängen (robust, degeneriert nicht)
    @Transaction
    open suspend fun moveSpontaneousLater(setId: Long, songId: Long, currentSongId: Long) {
        deleteCrossRef(setId, songId)
        val songs = getSongsInSetOnce(setId)
        val endPos = (songs.maxOfOrNull { it.positionInSet } ?: -1) + 1
        insertSpontaneousAt(setId, songId, endPos)
    }

    private suspend fun insertSpontaneousAt(setId: Long, songId: Long, pos: Int) {
        shiftPositionsUp(setId, pos)
        insertCrossRef(SetSongCrossRef(setId, songId, pos, isCompleted = false, isSpontaneous = true))
        // Lücken schließen: alle Positionen von 0..n-1 neu vergeben
        getSongsInSetOnce(setId).forEachIndexed { i, s -> updateSongPosition(setId, s.song.id, i) }
    }
}
