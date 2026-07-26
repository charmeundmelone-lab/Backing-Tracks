package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gigs")
data class GigEntity(
    @PrimaryKey(autoGenerate = true) val gigId: Long = 0,
    val name: String,
    val lastActiveSetId: Long = 0L,
    val autoAdvanceSets: Boolean = false
)
