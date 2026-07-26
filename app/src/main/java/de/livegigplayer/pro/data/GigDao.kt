package de.livegigplayer.pro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gig: GigEntity): Long

    @Delete
    suspend fun delete(gig: GigEntity)

    @Query("SELECT * FROM gigs ORDER BY name ASC")
    fun getAllGigs(): Flow<List<GigEntity>>

    @Query("SELECT * FROM gigs WHERE gigId = :gigId")
    suspend fun getGigById(gigId: Long): GigEntity?

    @Query("UPDATE gigs SET lastActiveSetId = :setId WHERE gigId = :gigId")
    suspend fun setLastActiveSetId(gigId: Long, setId: Long)

    @Query("UPDATE gigs SET autoAdvanceSets = :enabled WHERE gigId = :gigId")
    suspend fun setAutoAdvanceSets(gigId: Long, enabled: Boolean)
}
