package de.minitraxx.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class GigRepository(context: Context) {

    private val db = AppDatabase.get(context)
    val gigDao: GigDao get() = db.gigDao()

    suspend fun startGig(): Long {
        val label = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.getDefault()).format(Date())
        return gigDao.insertGig(GigEntity(label = label))
    }

    suspend fun endGig(gigId: Long) {
        gigDao.endGig(gigId, System.currentTimeMillis())
    }

    suspend fun recordPlay(gigId: Long, songId: Long?, title: String, artist: String, playedMs: Long) {
        gigDao.insertPlay(
            GigPlayEntity(
                gigId = gigId,
                songId = songId,
                titleSnapshot = title,
                artistSnapshot = artist,
                playedMs = playedMs,
            )
        )
    }

    /** Emittiert bei jedem Gig-Wechsel automatisch die aktuellen Plays. */
    val playedSongIdsForActiveGig: Flow<Set<Long>> = gigDao
        .observeActiveGig()
        .flatMapLatest { gig ->
            if (gig == null) flowOf(emptySet())
            else gigDao.observePlayedSongIds(gig.id)
                .map { ids -> ids.filterNotNull().toSet() }
        }

    /** songId → Anzahl Plays im aktiven Gig. */
    val playCountsForActiveGig: Flow<Map<Long, Int>> = gigDao
        .observeActiveGig()
        .flatMapLatest { gig ->
            if (gig == null) flowOf(emptyMap())
            else gigDao.observePlayCounts(gig.id)
                .map { list -> list.associate { it.songId to it.count } }
        }

    companion object {
        @Volatile
        private var instance: GigRepository? = null

        fun get(context: Context): GigRepository =
            instance ?: synchronized(this) {
                instance ?: GigRepository(context.applicationContext).also { instance = it }
            }
    }
}
