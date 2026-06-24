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

    // Plain query without @Transaction — safe to call from inside @Transaction methods
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet, ref.endAction AS endAction
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    abstract suspend fun getSongsInSetOncePlain(setId: Long): List<SongInSet>

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

    @Query("UPDATE set_song_cross_ref SET isSpontaneous = :spontaneous WHERE setId = :setId AND songId = :songId")
    abstract suspend fun updateSpontaneous(setId: Long, songId: Long, spontaneous: Boolean)

    // ── Spontaneous insertion — fully in-memory, single read pass, no nested @Transaction ──

    // Right-swipe: insert directly after the currently playing song
    @Transaction
    open suspend fun moveSpontaneousNext(setId: Long, songId: Long, currentSongId: Long) {
        val songs = getSongsInSetOncePlain(setId).toMutableList()
        val songIdx = songs.indexOfFirst { it.song.id == songId }
        if (songIdx < 0) return
        val song = songs.removeAt(songIdx)
        val anchorIdx = songs.indexOfFirst { it.song.id == currentSongId }
        val insertIdx = if (anchorIdx >= 0) {
            anchorIdx + 1
        } else {
            val firstUnplayed = songs.indexOfFirst { !it.completedInSet }
            if (firstUnplayed >= 0) firstUnplayed + 1 else 0
        }
        songs.add(insertIdx.coerceIn(0, songs.size), song)
        updateSpontaneous(setId, songId, true)
        songs.forEachIndexed { i, s -> updateSongPosition(setId, s.song.id, i) }
    }

    // Left-swipe: append to end of set
    @Transaction
    open suspend fun moveSpontaneousLater(setId: Long, songId: Long, currentSongId: Long) {
        val songs = getSongsInSetOncePlain(setId).toMutableList()
        val songIdx = songs.indexOfFirst { it.song.id == songId }
        if (songIdx < 0) return
        val song = songs.removeAt(songIdx)
        songs.add(song)
        updateSpontaneous(setId, songId, true)
        songs.forEachIndexed { i, s -> updateSongPosition(setId, s.song.id, i) }
    }
}
