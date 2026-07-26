package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gigs")
data class GigEntity(
    @PrimaryKey(autoGenerate = true) val gigId: Long = 0,
    val name: String,
    val lastActiveSetId: Long = 0L,      // 0 = noch keins → erstes Set nehmen
    val autoAdvanceSets: Boolean = false // Auto-Übergang ins nächste Set, pro Gig
)
