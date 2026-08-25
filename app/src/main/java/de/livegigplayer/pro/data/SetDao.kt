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

    @Query("UPDATE sets SET name = :name WHERE setId = :setId")
    abstract suspend fun renameSet(setId: Long, name: String)

    @Query("SELECT * FROM sets WHERE gigOwnerId = :gigId ORDER BY position ASC")
    abstract fun getSetsForGig(gigId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM sets WHERE gigOwnerId = :gigId ORDER BY position ASC")
    protected abstract suspend fun getRawSets(gigId: Long): List<SetEntity>

    @Query("SELECT * FROM sets WHERE gigOwnerId = :gigId ORDER BY position ASC")
    abstract suspend fun getSetsForGigOnce(gigId: Long): List<SetEntity>

    @Query("""
        SELECT setId AS setId, COUNT(*) AS total, SUM(isCompleted) AS completed
        FROM set_song_cross_ref WHERE setId IN (:setIds) GROUP BY setId
    """)
    abstract suspend fun getSetProgress(setIds: List<Long>): List<SetProgress>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun updateRawSets(sets: List<SetEntity>)

    // Alle Set-Zugehörigkeiten in der gesamten DB, gigübergreifend — für die
    // Song-Verknüpfungsprüfung (SongLinkCheck): zeigt pro Song, in wie vielen/
    // welchen Sets er noch referenziert wird, unabhängig vom aktuell offenen Gig.
    @Query("""
        SELECT ref.songId AS songId, s.setId AS setId, s.name AS setName, g.name AS gigName
        FROM set_song_cross_ref ref
        INNER JOIN sets s ON s.setId = ref.setId
        INNER JOIN gigs g ON g.gigId = s.gigOwnerId
    """)
    abstract suspend fun getAllCrossRefMembershipOnce(): List<CrossRefMembership>

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

    // Alle SongIds, die in IRGENDEINEM Set dieses Gigs liegen (für Ausgrau-Logik im Archiv)
    @Query("""
        SELECT DISTINCT ref.songId FROM set_song_cross_ref ref
        INNER JOIN sets ON sets.setId = ref.setId
        WHERE sets.gigOwnerId = :gigId
    """)
    abstract fun getSongIdsInGig(gigId: Long): Flow<List<Long>>

    @Query("DELETE FROM set_song_cross_ref WHERE setId = :setId AND songId = :songId")
    abstract suspend fun deleteCrossRef(setId: Long, songId: Long)

    @Query("UPDATE set_song_cross_ref SET positionInSet = :position WHERE setId = :setId AND songId = :songId")
    abstract suspend fun updateSongPosition(setId: Long, songId: Long, position: Int)

    @Query("UPDATE set_song_cross_ref SET isCompleted = :completed WHERE setId = :setId AND songId = :songId")
    abstract suspend fun markSongCompleted(setId: Long, songId: Long, completed: Boolean)

    @Query("UPDATE set_song_cross_ref SET isCompleted = 0, isSpontaneous = 0 WHERE setId = :setId")
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

    // ── Manuelles Umsortieren (Drag & Drop im Sortier-Modus) ───────────────────

    @Transaction
    open suspend fun reorderSongs(setId: Long, orderedSongIds: List<Long>) {
        val refsBySongId = getRawCrossRefs(setId).associateBy { it.songId }
        val sanitized = orderedSongIds.mapIndexedNotNull { i, songId ->
            refsBySongId[songId]?.copy(positionInSet = i)
        }
        updateRawCrossRefs(sanitized)
    }

    // ── Manuelles Umsortieren der Sets innerhalb eines Gigs ────────────────────

    @Transaction
    open suspend fun reorderSets(gigId: Long, orderedSetIds: List<Long>) {
        val setsById = getRawSets(gigId).associateBy { it.setId }
        val sanitized = orderedSetIds.mapIndexedNotNull { i, setId ->
            setsById[setId]?.copy(position = i)
        }
        updateRawSets(sanitized)
    }
}
