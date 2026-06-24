package de.livegigplayer.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet, ref.isSpontaneous AS spontaneousInSet, ref.endAction AS endAction
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    abstract suspend fun getSongsInSetOncePlain(setId: Long): List<SongInSet>

    @Query("SELECT * FROM set_song_cross_ref WHERE setId = :setId ORDER BY positionInSet ASC")
    protected abstract suspend fun getRawCrossRefs(setId: Long): List<SetSongCrossRef>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun updateRawCrossRefs(refs: List<SetSongCrossRef>)

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

    // ── Rechts-Swipe: Song direkt nach dem aktuellen Song einfügen ────────────

    @Transaction
    open suspend fun moveSpontaneousNext(setId: Long, songId: Long, currentSongId: Long) {
        val refs = getRawCrossRefs(setId).toMutableList()
        val targetIdx = refs.indexOfFirst { it.songId == songId }
        if (targetIdx < 0) return

        val targetRef = refs.removeAt(targetIdx)

        val anchorIdx = refs.indexOfFirst { it.songId == currentSongId }
        val insertIdx = if (anchorIdx >= 0) {
            anchorIdx + 1
        } else {
            val firstUnplayed = refs.indexOfFirst { !it.isCompleted }
            if (firstUnplayed >= 0) firstUnplayed else 0
        }

        val updatedRef = targetRef.copy(isSpontaneous = true)
        refs.add(insertIdx.coerceIn(0, refs.size), updatedRef)

        val sanitized = refs.mapIndexed { i, ref -> ref.copy(positionInSet = i) }
        updateRawCrossRefs(sanitized)
    }

    // ── Links-Swipe: Song an die Grenze der Spontan-Zone (vor erstem regulären) einfügen ──

    @Transaction
    open suspend fun moveSpontaneousLater(setId: Long, songId: Long, currentSongId: Long) {
        val refs = getRawCrossRefs(setId).toMutableList()
        val targetIdx = refs.indexOfFirst { it.songId == songId }
        if (targetIdx < 0) return

        val targetRef = refs.removeAt(targetIdx)

        val anchorIdx = refs.indexOfFirst { it.songId == currentSongId }
        val startSearchIdx = if (anchorIdx >= 0) {
            anchorIdx + 1
        } else {
            val firstUnplayed = refs.indexOfFirst { !it.isCompleted }
            if (firstUnplayed >= 0) firstUnplayed else 0
        }

        var insertIdx = refs.size
        for (i in startSearchIdx until refs.size) {
            if (!refs[i].isCompleted && !refs[i].isSpontaneous) {
                insertIdx = i
                break
            }
        }

        val updatedRef = targetRef.copy(isSpontaneous = true)
        refs.add(insertIdx.coerceIn(0, refs.size), updatedRef)

        val sanitized = refs.mapIndexed { i, ref -> ref.copy(positionInSet = i) }
        updateRawCrossRefs(sanitized)
    }
}
