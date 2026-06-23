package de.livegigplayer.pro.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity        = GigEntity::class,
            parentColumns = ["gigId"],
            childColumns  = ["gigOwnerId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gigOwnerId")]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val gigOwnerId: Long,
    val name:       String,
    val position:   Int
)
