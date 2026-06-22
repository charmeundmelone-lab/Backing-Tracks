package de.livegigplayer.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY title ASC")
    fun getSongsByPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("UPDATE songs SET volDrums=:d, volBass=:b, volKeys=:k, volVocals=:v, volClick=:c, volCue=:cu WHERE id=:id")
    suspend fun updateMixerSettings(id: Long, d: Float, b: Float, k: Float, v: Float, c: Float, cu: Float)

    @Query("UPDATE songs SET volDrums=0, volBass=0, volKeys=0, volVocals=0, volClick=0, volCue=0")
    suspend fun resetAllMixerSettings()

    @Query("SELECT * FROM songs WHERE audioFilePath = :path LIMIT 1")
    suspend fun findByPath(path: String): Song?

    @Query("UPDATE songs SET loopStartMs=:start, loopEndMs=:end WHERE id=:id")
    suspend fun updateSongLoopPoints(id: Long, start: Long, end: Long)

    @Query("UPDATE songs SET loopStartMs=:startMs, loopEndMs=:endMs WHERE id=:id")
    suspend fun updateLoopPoints(id: Long, startMs: Long, endMs: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}
