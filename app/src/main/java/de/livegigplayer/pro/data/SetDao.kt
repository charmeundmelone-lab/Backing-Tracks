package de.livegigplayer.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: SetSongCrossRef)

    @Delete
    suspend fun deleteSet(set: SetEntity)

    @Query("UPDATE sets SET position = :position WHERE setId = :setId")
    suspend fun updatePosition(setId: Long, position: Int)

    @Query("SELECT * FROM sets WHERE gigOwnerId = :gigId ORDER BY position ASC")
    fun getSetsForGig(gigId: Long): Flow<List<SetEntity>>

    @Transaction
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    fun getSongsInSet(setId: Long): Flow<List<SongInSet>>

    @Transaction
    @Query("""
        SELECT songs.*, ref.positionInSet, ref.isCompleted AS completedInSet
        FROM songs
        INNER JOIN set_song_cross_ref ref ON songs.id = ref.songId
        WHERE ref.setId = :setId
        ORDER BY ref.positionInSet ASC
    """)
    suspend fun getSongsInSetOnce(setId: Long): List<SongInSet>

    @Query("SELECT MAX(positionInSet) FROM set_song_cross_ref WHERE setId = :setId")
    suspend fun getMaxPositionInSet(setId: Long): Int?

    @Query("DELETE FROM set_song_cross_ref WHERE setId = :setId AND songId = :songId")
    suspend fun deleteCrossRef(setId: Long, songId: Long)

    @Query("UPDATE set_song_cross_ref SET positionInSet = :position WHERE setId = :setId AND songId = :songId")
    suspend fun updateSongPosition(setId: Long, songId: Long, position: Int)

    @Query("UPDATE set_song_cross_ref SET isCompleted = :completed WHERE setId = :setId AND songId = :songId")
    suspend fun markSongCompleted(setId: Long, songId: Long, completed: Boolean)

    @Query("UPDATE set_song_cross_ref SET isCompleted = 0 WHERE setId = :setId")
    suspend fun resetCompletedForSet(setId: Long)
}
