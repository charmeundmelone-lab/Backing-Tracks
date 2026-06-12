package de.minitraxx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SongWithStems(
    @Embedded val song: SongEntity,
    @Relation(parentColumn = "id", entityColumn = "songId")
    val stems: List<StemEntity>,
)

data class SetlistItemWithSong(
    @Embedded val item: SetlistItemEntity,
    @Relation(parentColumn = "songId", entityColumn = "id")
    val song: SongEntity,
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SongEntity>>

    @Transaction
    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeWithStems(id: Long): Flow<SongWithStems?>

    @Transaction
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getWithStems(id: Long): SongWithStems?

    @Insert
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertStem(stem: StemEntity): Long

    @Update
    suspend fun updateStem(stem: StemEntity)

    @Delete
    suspend fun deleteStem(stem: StemEntity)

    @Query("SELECT MAX(frames) FROM stems WHERE songId = :songId")
    suspend fun maxStemFrames(songId: Long): Long?

    @Query("UPDATE songs SET durationFrames = :frames WHERE id = :songId")
    suspend fun updateDuration(songId: Long, frames: Long)
}

@Dao
interface SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SetlistEntity>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    fun observeById(id: Long): Flow<SetlistEntity?>

    @Transaction
    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId ORDER BY position")
    fun observeItems(setlistId: Long): Flow<List<SetlistItemWithSong>>

    @Transaction
    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId ORDER BY position")
    suspend fun getItems(setlistId: Long): List<SetlistItemWithSong>

    @Insert
    suspend fun insert(setlist: SetlistEntity): Long

    @Update
    suspend fun update(setlist: SetlistEntity)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItem(item: SetlistItemEntity): Long

    @Query("DELETE FROM setlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun nextPosition(setlistId: Long): Int

    @Query("UPDATE setlist_items SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun reorder(itemIdsInOrder: List<Long>) {
        itemIdsInOrder.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}
